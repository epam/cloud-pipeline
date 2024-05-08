/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.datastorage.providers.aws.omics;

import com.amazonaws.services.omics.model.CreateSequenceStoreResult;
import com.amazonaws.services.omics.model.FileInformation;
import com.amazonaws.services.omics.model.GetReadSetMetadataResult;
import com.amazonaws.services.omics.model.ListReadSetsResult;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.datastorage.providers.StorageEventCollector;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.RegionAwareS3Helper;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage.*;
import static com.epam.pipeline.manager.datastorage.providers.aws.omics.OmicsHelper.parseFilePath;

@Service
@Slf4j
public class OmicsSequenceStorageProvider extends AbstractOmicsStorageProvider<AWSOmicsSequenceDataStorage> {

    public static final String READ_SET_STORE_PATH_SUFFIX = "/readSet";
    public static final String SUBJECT_ID = "SubjectId";
    public static final String SAMPLE_ID = "SampleId";
    public static final String UPLOAD_FAILED_STATUS = "UPLOAD_FAILED";

    public static final Pattern AWS_S3_FILE_PATH_TEMPLATE = Pattern.compile("^s3://([^/]+)/(.+)$");

    private final StorageEventCollector omicsS3Events;

    public OmicsSequenceStorageProvider(final StorageEventCollector omicsS3Events,
                                        final MessageHelper messageHelper,
                                        final CloudRegionManager cloudRegionManager,
                                        final AuthManager authManager) {
        super(messageHelper, cloudRegionManager, authManager);
        this.omicsS3Events = omicsS3Events;
    }

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AWS_OMICS_SEQ;
    }

    @Override
    public String createStorage(final AWSOmicsSequenceDataStorage storage) throws DataStorageException {
        final CreateSequenceStoreResult omicsSeqStorage = getOmicsHelper(storage).registerOmicsSeqStorage(storage);
        final Matcher arnMatcher = SEQUENCE_STORE_ARN_FORMAT.matcher(omicsSeqStorage.getArn());
        if (arnMatcher.find()) {
            return String.format(AWS_OMICS_STORE_PATH_TEMPLATE,
                    arnMatcher.group(ACCOUNT),
                    arnMatcher.group(REGION),
                    arnMatcher.group(SEQUENCE_STORE_ID)
            ) + READ_SET_STORE_PATH_SUFFIX;
        } else {
            throw new IllegalArgumentException(
                String.format(
                    "Can't parse AWS Omics ARN because it doesn't match the schema: %s", omicsSeqStorage.getArn()
                )
            );
        }
    }

    @Override
    public void deleteStorage(final AWSOmicsSequenceDataStorage dataStorage) throws DataStorageException {
        getOmicsHelper(dataStorage).deleteOmicsSeqStorage(dataStorage);
    }

    @Override
    public Stream<DataStorageFile> listDataStorageFiles(final AWSOmicsSequenceDataStorage dataStorage,
                                                        final String path) {
        final Spliterator<List<DataStorageFile>> spliterator = Spliterators.spliteratorUnknownSize(
                new OmicsPageIterator(t -> getItems(dataStorage, path, false, null, t)), 0
        );
        return StreamSupport.stream(spliterator, false).flatMap(List::stream);
    }

    @Override
    DataStorageListing listOmicsFileSources(final AWSOmicsSequenceDataStorage dataStorage,
                                                      final String path) {
        final OmicsHelper omicsHelper = getOmicsHelper(dataStorage);
        final Pair<String, String> fileIdAndSource = parseFilePath(path);
        final GetReadSetMetadataResult readSetFile = omicsHelper.getOmicsSeqStorageFile(dataStorage, path);
        final ArrayList<AbstractDataStorageItem> results = new ArrayList<>();

        if (readSetFile.getStatus().equals(UPLOAD_FAILED_STATUS)) {
            throw new DataStorageException(String.format(
                    "Can't list ReadSet object with status %s. Status reason: %s",
                    UPLOAD_FAILED_STATUS, readSetFile.getStatusMessage())
            );
        }

        Stream.of(
                Pair.create(readSetFile.getFiles().getSource1(), SOURCE_1),
                Pair.create(readSetFile.getFiles().getSource2(), SOURCE_2),
                Pair.create(readSetFile.getFiles().getIndex(), INDEX)
        ).filter(fileInformation -> {
            String fileSourceToList = fileIdAndSource.getValue();
            if (fileSourceToList != null) {
                return fileInformation.getValue().equals(fileSourceToList);
            }
            return true;
        }).forEach(fileInformation ->
                Optional.ofNullable(
                        mapOmicsFileToDataStorageFile(
                                fileInformation.getKey(), fileIdAndSource.getKey(), fileInformation.getValue()
                        )
                ).ifPresent(file -> {
                    file.setChanged(S3Constants.getAwsDateFormat().format(readSetFile.getCreationTime()));
                    file.setLabels(new HashMap<String, String>() {
                        {
                            put(S3Helper.STORAGE_CLASS, readSetFile.getStatus());
                            put(FILE_NAME, readSetFile.getName());
                            put(FILE_TYPE, readSetFile.getFileType());
                            put(SUBJECT_ID, readSetFile.getSubjectId());
                            put(SAMPLE_ID, readSetFile.getSampleId());
                        }
                    });
                    results.add(file);
                }));

        Assert.notEmpty(results, String.format("Path '%s' not found!", path));
        return new DataStorageListing(null, results);
    }

    @Override
    DataStorageListing listOmicsFiles(final AWSOmicsSequenceDataStorage dataStorage,
                                                final Integer pageSize, final String marker) {
        final ListReadSetsResult result = getOmicsHelper(dataStorage).listReadSets(dataStorage, pageSize, marker);
        return new DataStorageListing(
                result.getNextToken(),
                Optional.ofNullable(result.getReadSets()).orElse(Collections.emptyList()).stream()
                        .map(readSet -> {
                            final DataStorageFolder file = new DataStorageFolder();
                            file.setPath(readSet.getId());
                            file.setName(readSet.getName());
                            file.setLabels(new HashMap<String, String>() {
                                {
                                    put(S3Helper.STORAGE_CLASS, readSet.getStatus());
                                    put(FILE_NAME, readSet.getName());
                                    put(FILE_TYPE, readSet.getFileType());
                                    put(SUBJECT_ID, readSet.getSubjectId());
                                    put(SAMPLE_ID, readSet.getSampleId());
                                }
                            });
                            return file;
                        }).collect(Collectors.toList())
        );
    }

    @Override
    public Optional<DataStorageFile> findFile(final AWSOmicsSequenceDataStorage dataStorage,
                                              final String path, final String version) {
        return Optional.ofNullable(
                getOmicsHelper(dataStorage).getOmicsSeqStorageFile(dataStorage, path)
        ).map(seqMetadata -> {
            final DataStorageFile file = new DataStorageFile();
            file.setName(seqMetadata.getName());
            file.setSize(seqMetadata.getSequenceInformation().getTotalBaseCount());
            file.setPath(seqMetadata.getId());
            return file;
        });
    }

    @Override
    public void deleteFile(final AWSOmicsSequenceDataStorage dataStorage, final String path,
                           final String version, final Boolean totally) throws DataStorageException {
        if (StringUtils.isNotBlank(version)) {
            log.warn(messageHelper.getMessage(MessageConstants.AWS_OMICS_STORE_DOESNT_SUPPORT_VERSIONING));
        }
        getOmicsHelper(dataStorage).deleteOmicsSeqStorageFile(dataStorage, path);
    }

    @Override
    public PathDescription getDataSize(final AWSOmicsSequenceDataStorage dataStorage,
                                       final String path, final PathDescription pathDescription) {
        if (path != null) {
            final DataStorageFile file = findFile(dataStorage, path, null)
                    .orElseThrow(() -> new DataStorageException(
                            messageHelper.getMessage(MessageConstants.AWS_OMICS_FILE_NOT_FOUND, path)));
            pathDescription.setSize(file.getSize());
        } else {
            listDataStorageFiles(dataStorage, null)
                    .forEach(file -> pathDescription.increaseSize(file.getSize()));
        }
        pathDescription.setCompleted(true);
        return pathDescription;
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(final AWSOmicsSequenceDataStorage dataStorage,
                                                          final String path, final String version,
                                                          final ContentDisposition contentDisposition) {
        final Pair<String, String> bucketAndPath = getS3SourceFileLocation(dataStorage, path);
        final AwsRegion region = cloudRegionManager.getAwsRegion(dataStorage);
        final RegionAwareS3Helper s3Helper = new RegionAwareS3Helper(
                omicsS3Events, messageHelper, region, cloudRegionManager.loadCredentials(region)
        );
        return s3Helper.generateDownloadURL(
                bucketAndPath.getFirst(), bucketAndPath.getSecond(), null, contentDisposition
        );
    }

    @Override
    public DataStorageDownloadFileUrl generateUrl(final AWSOmicsSequenceDataStorage dataStorage, final String path,
                                                  final List<String> permissions, final Duration duration) {
        return generateDownloadURL(dataStorage, path, null, null);
    }

    private Pair<String, String> getS3SourceFileLocation(final AWSOmicsSequenceDataStorage dataStorage,
                                                         final String path) {
        final Pair<String, String> fileIdAndSource = parseFilePath(path);
        Assert.notNull(
                fileIdAndSource.getSecond(),
                String.format("Can't define S3 readSet file location. " +
                        "No source/source1/source2/index in readSet file path: %s.", path
                )
        );

        return Optional.ofNullable(
                getOmicsHelper(dataStorage).getOmicsSeqStorageFile(dataStorage, path)
        ).map(readSeqMetadata -> {
            final FileInformation sourceFile;
            if (fileIdAndSource.getSecond().equals(SOURCE_1)) {
                sourceFile = readSeqMetadata.getFiles().getSource1();
            } else if (fileIdAndSource.getSecond().equals(SOURCE_2)) {
                sourceFile = readSeqMetadata.getFiles().getSource2();
            } else {
                sourceFile = readSeqMetadata.getFiles().getIndex();
            }

            final String s3Uri = sourceFile.getS3Access().getS3Uri();
            final Matcher s3ObjectPathMatcher = AWS_S3_FILE_PATH_TEMPLATE.matcher(s3Uri);
            if (s3ObjectPathMatcher.find()) {
                return Pair.create(s3ObjectPathMatcher.group(1), s3ObjectPathMatcher.group(2));
            } else {
                throw new DataStorageException(String.format(
                        "Can't parse s3 path: '%s', to get bucket name and path.", s3Uri)
                );
            }
        }).orElseThrow(() -> new DataStorageException("Can't get S3 location for ReadSet source!"));
    }
}

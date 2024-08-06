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

import com.amazonaws.services.omics.model.CreateReferenceStoreResult;
import com.amazonaws.services.omics.model.GetReferenceMetadataResult;
import com.amazonaws.services.omics.model.ListReferencesResult;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsReferenceDataStorage;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.epam.pipeline.entity.datastorage.aws.AWSOmicsReferenceDataStorage.*;


@Service
@Slf4j
public class OmicsReferenceStorageProvider extends AbstractOmicsStorageProvider<AWSOmicsReferenceDataStorage> {

    public static final String REFERENCE_STORE_PATH_SUFFIX = "/reference";

    public OmicsReferenceStorageProvider(final MessageHelper messageHelper,
                                         final CloudRegionManager cloudRegionManager,
                                         final AuthManager authManager) {
        super(messageHelper, cloudRegionManager, authManager);
    }

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AWS_OMICS_REF;
    }

    @Override
    public String createStorage(final AWSOmicsReferenceDataStorage storage) throws DataStorageException {
        Assert.state(authManager.getCurrentUser().isAdmin(), messageHelper.getMessage(
                MessageConstants.AWS_OMICS_REFERENCE_STORE_CREATION_ADMIN_ONLY, storage.getName()));
        final CreateReferenceStoreResult omicsRefStorage = getOmicsHelper(storage).registerOmicsRefStorage(storage);
        final Matcher arnMatcher = REFERENCE_STORE_ARN_FORMAT.matcher(omicsRefStorage.getArn());
        if (arnMatcher.find()) {
            return String.format(AWS_OMICS_STORE_PATH_TEMPLATE,
                    arnMatcher.group(ACCOUNT),
                    arnMatcher.group(REGION),
                    arnMatcher.group(REFERENCE_STORE_ID)
            ) + REFERENCE_STORE_PATH_SUFFIX;
        } else {
            throw new IllegalArgumentException(
                String.format(
                    "Can't parse AWS Omics ARN because it doesn't match the schema: %s", omicsRefStorage.getArn()
                )
            );
        }
    }

    @Override
    public void deleteStorage(final AWSOmicsReferenceDataStorage dataStorage) throws DataStorageException {
        getOmicsHelper(dataStorage).deleteOmicsRefStorage(dataStorage);
    }

    public Optional<DataStorageFile> findFile(final AWSOmicsReferenceDataStorage dataStorage,
                                              final String path, final String version) {
        return Optional.ofNullable(
                getOmicsHelper(dataStorage).getOmicsRefStorageFile(dataStorage, path)
        ).map(refMetadata -> {
            final DataStorageFile file = new DataStorageFile();
            file.setName(refMetadata.getName());
            file.setSize(getReferenceSize(refMetadata));
            file.setPath(refMetadata.getId());
            return file;
        });
    }

    @Override
    public void deleteFile(final AWSOmicsReferenceDataStorage dataStorage, final String path,
                           final String version, final Boolean totally) throws DataStorageException {
        if (StringUtils.isNotBlank(version)) {
            log.warn(messageHelper.getMessage(MessageConstants.AWS_OMICS_STORE_DOESNT_SUPPORT_VERSIONING));
        }
        getOmicsHelper(dataStorage).deleteOmicsRefStorageFile(dataStorage, path);
    }

    public Stream<DataStorageFile> listDataStorageFiles(final AWSOmicsReferenceDataStorage dataStorage,
                                                        final String path) {
        final Spliterator<List<DataStorageFile>> spliterator = Spliterators.spliteratorUnknownSize(
                new OmicsPageIterator(t -> getItems(dataStorage, path, false, null, t)), 0
        );
        return StreamSupport.stream(spliterator, false).flatMap(List::stream);
    }

    @Override
    public PathDescription getDataSize(final AWSOmicsReferenceDataStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        if (path != null) {
            final DataStorageFile file = findFile(dataStorage, path, null)
                    .orElseThrow(() -> new DataStorageException(
                            messageHelper.getMessage(MessageConstants.AWS_OMICS_FILE_NOT_FOUND, path)));
            pathDescription.setSize(file.getSize());
        } else {
            listDataStorageFiles(dataStorage, null)
                    .forEach(file -> {
                        final DataStorageFile fileInfo = findFile(dataStorage, file.getPath(), null)
                            .orElseThrow(
                                () -> new DataStorageException(
                                    messageHelper.getMessage(
                                            MessageConstants.AWS_OMICS_FILE_NOT_FOUND, file.getPath())
                                )
                            );
                        pathDescription.increaseSize(fileInfo.getSize());
                    });
        }
        pathDescription.setCompleted(true);
        return pathDescription;
    }

    @Override
    DataStorageListing listOmicsFileSources(final AWSOmicsReferenceDataStorage dataStorage, final String path) {
        final OmicsHelper omicsHelper = getOmicsHelper(dataStorage);
        final Pair<String, String> fileIdAndSource = OmicsHelper.parseFilePath(path);
        final GetReferenceMetadataResult refFile = omicsHelper.getOmicsRefStorageFile(dataStorage, path);
        final ArrayList<AbstractDataStorageItem> results = new ArrayList<>();

        Stream.of(
                Pair.create(refFile.getFiles().getSource(), SOURCE),
                Pair.create(refFile.getFiles().getIndex(), INDEX)
        ).filter(fileInformation -> {
            final String omicsFileSource = fileIdAndSource.getValue();
            if (omicsFileSource != null) {
                return fileInformation.getValue().equals(omicsFileSource);
            }
            return true;
        }).forEach(fileInformation ->
                Optional.ofNullable(
                        mapOmicsFileToDataStorageFile(
                                fileInformation.getKey(), fileIdAndSource.getKey(), fileInformation.getValue()
                        )
                ).ifPresent(file -> {
                    file.setChanged(S3Constants.getAwsDateFormat().format(refFile.getCreationTime()));
                    file.setLabels(new HashMap<String, String>() {
                        {
                            put(S3Helper.STORAGE_CLASS, refFile.getStatus());
                            put(FILE_NAME, refFile.getName());
                            put(FILE_TYPE, REFERENCE_FILE_TYPE);
                        }
                    });
                    results.add(file);
                }));

        Assert.notEmpty(results, String.format("Path '%s' not found!", path));
        return new DataStorageListing(null, results);
    }

    @Override
    DataStorageListing listOmicsFiles(final AWSOmicsReferenceDataStorage dataStorage,
                                                final Integer pageSize, final String marker) {
        final ListReferencesResult result = getOmicsHelper(dataStorage)
                .listReferences(dataStorage, pageSize, marker);
        return new DataStorageListing(
                result.getNextToken(),
                Optional.ofNullable(result.getReferences()).orElse(Collections.emptyList()).stream()
                        .map(refItem -> {
                            final DataStorageFolder file = new DataStorageFolder();
                            file.setPath(refItem.getId());
                            file.setName(refItem.getName());
                            file.setLabels(new HashMap<String, String>() {
                                {
                                    put(S3Helper.STORAGE_CLASS, refItem.getStatus());
                                    put(FILE_NAME, refItem.getName());
                                    put(FILE_TYPE, REFERENCE_FILE_TYPE);
                                }
                            });
                            return file;
                        }).collect(Collectors.toList())
        );
    }

    private long getReferenceSize(final GetReferenceMetadataResult referenceMetadata) {
        return getSizeFromFileInformation(referenceMetadata.getFiles().getIndex())
                + getSizeFromFileInformation(referenceMetadata.getFiles().getSource());
    }
}

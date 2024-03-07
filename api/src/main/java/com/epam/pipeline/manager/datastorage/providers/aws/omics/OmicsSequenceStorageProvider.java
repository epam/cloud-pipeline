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

import com.amazonaws.services.omics.model.*;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class OmicsSequenceStorageProvider extends OmicsStorageProvider<AWSOmicsSequenceDataStorage> {

    public static final String READ_SET_STORE_PATH_SUFFIX = "/readSet/";

    public OmicsSequenceStorageProvider(final MessageHelper messageHelper,
                                        final CloudRegionManager cloudRegionManager) {
        super(messageHelper, cloudRegionManager);
    }

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AWS_OMICS_SEQ;
    }

    @Override
    public String createStorage(final AWSOmicsSequenceDataStorage storage) throws DataStorageException {
        final CreateSequenceStoreResult omicsRefStorage = getOmicsHelper(storage).registerOmicsSeqStorage(storage);
        final Matcher arnMatcher = AWSOmicsSequenceDataStorage.SEQUENCE_STORE_ARN_FORMAT.matcher(omicsRefStorage.getArn());
        if (arnMatcher.find()) {
            return String.format(AWS_OMICS_STORE_PATH_TEMPLATE,
                    arnMatcher.group("account"),
                    arnMatcher.group("region"),
                    arnMatcher.group("sequenceStoreId")
            ) + READ_SET_STORE_PATH_SUFFIX;
        } else {
            throw new IllegalArgumentException();
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
    public DataStorageListing getItems(final AWSOmicsSequenceDataStorage dataStorage, final String path,
                                       final Boolean showVersion, final Integer pageSize, final String marker) {
        if (StringUtils.isNotBlank(path)) {
            log.warn("path field is not empty, but Omics Reference store doesn't support hierarchy.");
        }
        if (BooleanUtils.isTrue(showVersion)) {
            log.warn("showVersion field is not empty, but Omics Reference store doesn't support versioning.");
        }
        final ListReadSetsResult result = getOmicsHelper(dataStorage).listReadSets(dataStorage, pageSize, marker);
        return new DataStorageListing(
                result.getNextToken(),
                Optional.ofNullable(result.getReadSets()).orElse(Collections.emptyList()).stream()
                .map(readSet -> {
                            final DataStorageFile file = new DataStorageFile();
                            file.setPath(readSet.getId());
                            file.setName(readSet.getName());
                            file.setLabels(Collections.singletonMap(S3Helper.STORAGE_CLASS, readSet.getStatus()));
                            file.setSize(readSet.getSequenceInformation().getTotalBaseCount());
                            file.setChanged(S3Constants.getAwsDateFormat().format(readSet.getCreationTime()));
                            return file;
                        }).collect(Collectors.toList())
        );
    }

    @Override
    public DataStorageListing getItems(final AWSOmicsSequenceDataStorage dataStorage, final String path,
                                       final Boolean showVersion, final Integer pageSize, final String marker,
                                       final DataStorageLifecycleRestoredListingContainer restoredListing) {
        return getItems(dataStorage, path, showVersion, pageSize, marker);
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
            log.warn("Version field is not empty, but Omics Reference store doesn't support versioning.");
        }
        getOmicsHelper(dataStorage).deleteOmicsSeqStorageFile(dataStorage, path);
    }

    @Override
    public PathDescription getDataSize(final AWSOmicsSequenceDataStorage dataStorage,
                                       final String path, final PathDescription pathDescription) {
        if (path != null) {
            final DataStorageFile file = findFile(dataStorage, path, null)
                    .orElseThrow(() -> new DataStorageException(String.format("Reference not found by path %s", path)));
            pathDescription.setSize(file.getSize());
        } else {
            listDataStorageFiles(dataStorage, null)
                    .forEach(file -> pathDescription.increaseSize(file.getSize()));
        }
        pathDescription.setCompleted(true);
        return pathDescription;
    }

    @Override
    public DataStorageFile createFile(final AWSOmicsSequenceDataStorage dataStorage,
                                      final String path, final byte[] contents) throws DataStorageException {
        // TODO
        return null;
    }

}

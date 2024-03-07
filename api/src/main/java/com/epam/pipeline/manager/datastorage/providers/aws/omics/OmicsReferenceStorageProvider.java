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
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsReferenceDataStorage;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


@Service
@Slf4j
public class OmicsReferenceStorageProvider extends OmicsStorageProvider<AWSOmicsReferenceDataStorage> {

    public static final String REFERENCE_STORE_PATH_SUFFIX = "/reference/";

    public OmicsReferenceStorageProvider(final MessageHelper messageHelper,
                                         final CloudRegionManager cloudRegionManager) {
        super(messageHelper, cloudRegionManager);
    }

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AWS_OMICS_REF;
    }

    @Override
    public String createStorage(final AWSOmicsReferenceDataStorage storage) throws DataStorageException {
        final CreateReferenceStoreResult omicsRefStorage = getOmicsHelper(storage).registerOmicsRefStorage(storage);
        final Matcher arnMatcher = AWSOmicsReferenceDataStorage.REFERENCE_STORE_ARN_FORMAT.matcher(omicsRefStorage.getArn());
        if (arnMatcher.find()) {
            return String.format(AWS_OMICS_STORE_PATH_TEMPLATE,
                    arnMatcher.group("account"),
                    arnMatcher.group("region"),
                    arnMatcher.group("referenceStoreId")
            ) + REFERENCE_STORE_PATH_SUFFIX;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void deleteStorage(final AWSOmicsReferenceDataStorage dataStorage) throws DataStorageException {
        getOmicsHelper(dataStorage).deleteOmicsRefStorage(dataStorage);
    }

    @Override
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
            log.warn("Version field is not empty, but Omics Reference store doesn't support versioning.");
        }
        getOmicsHelper(dataStorage).deleteOmicsRefStorageFile(dataStorage, path);
    }

    @Override
    public Stream<DataStorageFile> listDataStorageFiles(final AWSOmicsReferenceDataStorage dataStorage,
                                                        final String path) {
        final Spliterator<List<DataStorageFile>> spliterator = Spliterators.spliteratorUnknownSize(
                new OmicsPageIterator(t -> getItems(dataStorage, path, false, null, t)), 0
        );
        return StreamSupport.stream(spliterator, false).flatMap(List::stream);
    }

    @Override
    public DataStorageListing getItems(final AWSOmicsReferenceDataStorage dataStorage, final String path,
                                       final Boolean showVersion, final Integer pageSize, final String marker) {
        if (StringUtils.isNotBlank(path)) {
            log.warn("path field is not empty, but Omics Reference store doesn't support hierarchy.");
        }
        if (BooleanUtils.isTrue(showVersion)) {
            log.warn("showVersion field is not empty, but Omics Reference store doesn't support versioning.");
        }
        final ListReferencesResult result = getOmicsHelper(dataStorage).listReferences(dataStorage, pageSize, marker);
        return new DataStorageListing(
            result.getNextToken(),
            Optional.ofNullable(result.getReferences()).orElse(Collections.emptyList()).stream()
                .map(refItem -> {
                    final DataStorageFile file = new DataStorageFile();
                    file.setPath(refItem.getId());
                    file.setName(refItem.getName());
                    file.setChanged(S3Constants.getAwsDateFormat().format(refItem.getUpdateTime()));
                    return file;
                }).collect(Collectors.toList())
        );
    }

    @Override
    public DataStorageListing getItems(final AWSOmicsReferenceDataStorage dataStorage, final String path,
                                       final Boolean showVersion, final Integer pageSize, final String marker,
                                       final DataStorageLifecycleRestoredListingContainer restoredListing) {
        return getItems(dataStorage, path, showVersion, pageSize, marker);
    }

    @Override
    public PathDescription getDataSize(final AWSOmicsReferenceDataStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        if (path != null) {
            final DataStorageFile file = findFile(dataStorage, path, null)
                    .orElseThrow(() -> new DataStorageException(String.format("Reference not found by path %s", path)));
            pathDescription.setSize(file.getSize());
        } else {
            listDataStorageFiles(dataStorage, null)
                    .forEach(file -> {
                        final DataStorageFile fileInfo = findFile(dataStorage, file.getPath(), null)
                                .orElseThrow(
                                        () -> new DataStorageException(
                                                String.format("Reference not found by path %s", file.getPath())
                                        )
                                );
                        pathDescription.increaseSize(fileInfo.getSize());
                    });
        }
        pathDescription.setCompleted(true);
        return pathDescription;
    }

    @Override
    public DataStorageFile createFile(final AWSOmicsReferenceDataStorage dataStorage,
                                      final String path, final byte[] contents) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    private long getReferenceSize(final GetReferenceMetadataResult referenceMetadata) {
        return getSizeFromFileInformation(referenceMetadata.getFiles().getIndex())
                + getSizeFromFileInformation(referenceMetadata.getFiles().getSource());
    }

}

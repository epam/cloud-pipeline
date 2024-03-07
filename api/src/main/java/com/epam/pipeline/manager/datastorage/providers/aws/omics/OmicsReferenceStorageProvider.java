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
import com.amazonaws.services.omics.model.ListReferencesResult;
import com.amazonaws.services.omics.model.ReferenceFiles;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.entity.datastorage.*;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsReferenceDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


@Service
@RequiredArgsConstructor
@Slf4j
public class OmicsReferenceStorageProvider implements StorageProvider<AWSOmicsReferenceDataStorage> {

    public static final String AWS_OMICS_REFERENCE_STORE_FILE_PATH_TEMPLATE = "reference/%s/source";
    public static final Pattern AWS_OMICS_REFERENCE_STORE_FILE_PATH_FORMAT =
            Pattern.compile("reference/(?<referenceId>.*)/source");
    public static final String EMPTY = "";

    private final MessageHelper messageHelper;
    private final CloudRegionManager cloudRegionManager;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AWS_OMICS_REF;
    }

    @Override
    public String createStorage(final AWSOmicsReferenceDataStorage storage) throws DataStorageException {
        final CreateReferenceStoreResult omicsRefStorage = getOmicsHelper(storage).registerOmicsRefStorage(storage);
        final Matcher arnMatcher = AWSOmicsReferenceDataStorage.REFERENCE_STORE_ARN_FORMAT.matcher(omicsRefStorage.getArn());
        if (arnMatcher.find()) {
            return String.format(AWSOmicsReferenceDataStorage.AWS_OMICS_REFERENCE_STORE_PATH_TEMPLATE,
                    arnMatcher.group("account"),
                    arnMatcher.group("region"),
                    arnMatcher.group("referenceStoreId")
            );
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void deleteStorage(final AWSOmicsReferenceDataStorage dataStorage) throws DataStorageException {
        getOmicsHelper(dataStorage).registerOmicsRefStorage(dataStorage);
    }

    @Override
    public Optional<DataStorageFile> findFile(final AWSOmicsReferenceDataStorage dataStorage,
                                              final String path, final String version) {
        return Optional.ofNullable(
                getOmicsHelper(dataStorage).getOmicsRefStorageFile(dataStorage, getReferenceIdFromFilePath(path))
        ).map(refMetadata -> {
            final DataStorageFile file = new DataStorageFile();
            file.setName(refMetadata.getName());
            file.setPath(String.format(AWS_OMICS_REFERENCE_STORE_FILE_PATH_TEMPLATE, refMetadata.getId()));
            return file;
        });
    }

    @Override
    public void deleteFile(final AWSOmicsReferenceDataStorage dataStorage, final String path,
                           final String version, final Boolean totally) throws DataStorageException {
        if (StringUtils.isNotBlank(version)) {
            log.warn("Version field is not empty, but Omics Reference store doesn't support versioning.");
        }
        getOmicsHelper(dataStorage).deleteOmicsRefStorageFile(dataStorage, getReferenceIdFromFilePath(path));
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
        final ListReferencesResult result = getOmicsHelper(dataStorage).listItems(dataStorage, pageSize, marker);
        return new DataStorageListing(
            result.getNextToken(),
            result.getReferences().stream()
                .map(refItem -> {
                    final DataStorageFile file = new DataStorageFile();
                    file.setPath(String.format(AWS_OMICS_REFERENCE_STORE_FILE_PATH_TEMPLATE, refItem.getId()));
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
    public String buildFullStoragePath(final AWSOmicsReferenceDataStorage dataStorage, final String name) {
        return name;
    }

    @Override
    public PathDescription getDataSize(final AWSOmicsReferenceDataStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        if (path != null) {
            pathDescription.setSize(getReferenceSize(dataStorage, path));
        } else {
            listDataStorageFiles(dataStorage, null)
                    .forEach(file -> pathDescription.increaseSize(getReferenceSize(dataStorage, file.getPath())));
        }
        pathDescription.setCompleted(true);
        return pathDescription;
    }

    @Override
    public boolean checkStorage(final AWSOmicsReferenceDataStorage dataStorage) {
        return false;
    }

    @Override
    public ActionStatus postCreationProcessing(final AWSOmicsReferenceDataStorage storage) {
        return ActionStatus.notSupported();
    }

    @Override
    public DataStorageFile createFile(AWSOmicsReferenceDataStorage dataStorage, String path, byte[] contents) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFile createFile(AWSOmicsReferenceDataStorage dataStorage, String path, InputStream dataStream) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageItemContent getFile(AWSOmicsReferenceDataStorage dataStorage, String path, String version, Long maxDownloadSize) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public void applyStoragePolicy(AWSOmicsReferenceDataStorage dataStorage) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public void restoreFileVersion(AWSOmicsReferenceDataStorage dataStorage, String path, String version) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(AWSOmicsReferenceDataStorage dataStorage, String path, String version, ContentDisposition contentDisposition) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(AWSOmicsReferenceDataStorage dataStorage, String path) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageDownloadFileUrl generateUrl(AWSOmicsReferenceDataStorage dataStorage, String path, List<String> permissions, Duration duration) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFolder createFolder(AWSOmicsReferenceDataStorage dataStorage, String path) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public void deleteFolder(AWSOmicsReferenceDataStorage dataStorage, String path, Boolean totally) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFile moveFile(AWSOmicsReferenceDataStorage dataStorage, String oldPath, String newPath) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFolder moveFolder(AWSOmicsReferenceDataStorage dataStorage, String oldPath, String newPath) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFile copyFile(AWSOmicsReferenceDataStorage dataStorage, String oldPath, String newPath) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFolder copyFolder(AWSOmicsReferenceDataStorage dataStorage, String oldPath, String newPath) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public Map<String, String> updateObjectTags(AWSOmicsReferenceDataStorage dataStorage, String path, Map<String, String> tags, String version) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public Map<String, String> listObjectTags(AWSOmicsReferenceDataStorage dataStorage, String path, String version) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public Map<String, String> deleteObjectTags(AWSOmicsReferenceDataStorage dataStorage, String path, Set<String> tagsToDelete, String version) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageStreamingContent getStream(AWSOmicsReferenceDataStorage dataStorage, String path, String version) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public String getDefaultMountOptions(AWSOmicsReferenceDataStorage dataStorage) {
        throw new UnsupportedOperationException("Mount mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyStorageLifecyclePolicyRule(final StorageLifecycleRule rule) {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyStorageLifecycleRuleExecution(final StorageLifecycleRuleExecution execution) {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyRestoreActionSupported() {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public String verifyOrDefaultRestoreMode(final StorageRestoreActionRequest restoreMode) {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageItemType getItemType(final AWSOmicsReferenceDataStorage dataStorage,
                                           final String path, final String version) {
        return DataStorageItemType.File;
    }

    private long getReferenceSize(final AWSOmicsReferenceDataStorage dataStorage, final String path) {
        final ReferenceFiles refFiles = getOmicsHelper(dataStorage)
                .getOmicsRefStorageFile(dataStorage, getReferenceIdFromFilePath(path)).getFiles();
        return refFiles.getIndex().getContentLength() + refFiles.getSource().getContentLength();
    }

    private static String getReferenceIdFromFilePath(final String path) {
        final Matcher refPathMatcher = AWS_OMICS_REFERENCE_STORE_FILE_PATH_FORMAT
                .matcher(Optional.ofNullable(path).orElse(EMPTY));
        if (refPathMatcher.find()) {
            return refPathMatcher.group("referenceId");
        }
        throw new IllegalArgumentException();
    }

    private AwsRegion getAwsRegion(final AWSOmicsReferenceDataStorage dataStorage) {
        return cloudRegionManager.getAwsRegion(dataStorage);
    }

    private AwsRegionCredentials getAwsCredentials(final AwsRegion region) {
        return cloudRegionManager.loadCredentials(region);
    }

    public OmicsHelper getOmicsHelper(AWSOmicsReferenceDataStorage dataStorage) {
        final AwsRegion region = getAwsRegion(dataStorage);
        if (dataStorage.isUseAssumedCredentials()) {
            final String roleArn = Optional.ofNullable(dataStorage.getTempCredentialsRole())
                    .orElse(region.getTempCredentialsRole());
            return new OmicsHelper(region, roleArn);
        }
        if (StringUtils.isNotBlank(region.getIamRole())) {
            return new OmicsHelper(region, region.getIamRole());
        }
        return new OmicsHelper(region, getAwsCredentials(region));
    }

}

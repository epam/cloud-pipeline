/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.gcp;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.audit.AuditClient;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GSBucketStorageProvider implements StorageProvider<GSBucketStorage> {

    private final CloudRegionManager cloudRegionManager;
    private final MessageHelper messageHelper;
    private final GCPClient gcpClient;
    private final AuthManager authManager;
    private final AuditClient gsAuditClient;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.GS;
    }

    @Override
    public String createStorage(final GSBucketStorage storage) throws DataStorageException {
        return getHelper(storage).createGoogleStorage(storage);
    }

    @Override
    public ActionStatus postCreationProcessing(final GSBucketStorage storage) {
        return getHelper(storage).applyIamPolicy(storage);
    }

    @Override
    public void deleteStorage(final GSBucketStorage dataStorage) throws DataStorageException {
        getHelper(dataStorage).deleteGoogleStorage(dataStorage.getPath());
    }

    @Override
    public void applyStoragePolicy(final GSBucketStorage storage) {
        final GCPRegion gcpRegion = cloudRegionManager.getGCPRegion(storage);
        final StoragePolicy policy = buildPolicy(gcpRegion, storage.getStoragePolicy());
        getHelper(storage).applyStoragePolicy(storage, policy);
    }

    @Override
    public void restoreFileVersion(final GSBucketStorage dataStorage, final String path, final String version)
            throws DataStorageException {
        getHelper(dataStorage).restoreFileVersion(dataStorage, path, version);
    }

    @Override
    public Stream<DataStorageFile> listDataStorageFiles(final GSBucketStorage dataStorage, final String path) {
        return getHelper(dataStorage).listDataStorageFiles(dataStorage, path);
    }

    @Override
    public DataStorageListing getItems(final GSBucketStorage dataStorage, final String path, final Boolean showVersion,
                                       final Integer pageSize, String marker) {
        return getHelper(dataStorage).listItems(dataStorage, path, showVersion, pageSize, marker);
    }

    @Override
    public DataStorageListing getItems(final GSBucketStorage dataStorage, final String path, final Boolean showVersion,
                                       final Integer pageSize, final String marker,
                                       final DataStorageLifecycleRestoredListingContainer restoredListing) {
        if (Objects.nonNull(restoredListing)) {
            throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
        }
        return getItems(dataStorage, path, showVersion, pageSize, marker);
    }

    @Override
    public Optional<DataStorageFile> findFile(final GSBucketStorage dataStorage, final String path, 
                                              final String version) {
        return getHelper(dataStorage).findFile(dataStorage, path, version);
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(final GSBucketStorage dataStorage, final String path,
                                                          final String version, ContentDisposition contentDisposition) {
        return getHelper(dataStorage).generateDownloadUrl(dataStorage, path, version);
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(final GSBucketStorage dataStorage,
                                                                       final String path) {
        return null;
    }

    @Override
    public DataStorageDownloadFileUrl generateUrl(final GSBucketStorage dataStorage,
                                                  final String path,
                                                  final List<String> permissions,
                                                  final Duration duration) {
        return generateDownloadURL(dataStorage, path, null, null);
    }

    @Override
    public DataStorageFile createFile(final GSBucketStorage dataStorage, final String path, final byte[] contents)
            throws DataStorageException {
        return getHelper(dataStorage).createFile(dataStorage, path, contents, authManager.getAuthorizedUser());
    }

    @Override
    public DataStorageFile createFile(final GSBucketStorage dataStorage, final String path,
                                      final InputStream dataStream) throws DataStorageException {
        return getHelper(dataStorage).createFile(dataStorage, path, dataStream, authManager.getAuthorizedUser());
    }

    @Override
    public DataStorageFolder createFolder(final GSBucketStorage dataStorage, final String path)
            throws DataStorageException {
        return getHelper(dataStorage).createFolder(dataStorage, path);
    }

    @Override
    public void deleteFile(final GSBucketStorage dataStorage, final String path, final String version,
                           final Boolean totally) throws DataStorageException {
        getHelper(dataStorage).deleteFile(dataStorage, path, version, totally);
    }

    @Override
    public void deleteFolder(final GSBucketStorage dataStorage, final String path, final Boolean totally)
            throws DataStorageException {
        getHelper(dataStorage).deleteFolder(dataStorage, path, totally);
    }

    @Override
    public DataStorageFile moveFile(final GSBucketStorage dataStorage, final String oldPath, final String newPath)
            throws DataStorageException {
        return getHelper(dataStorage).moveFile(dataStorage, oldPath, newPath);
    }

    @Override
    public DataStorageFolder moveFolder(final GSBucketStorage dataStorage, final String oldPath, final String newPath)
            throws DataStorageException {
        return getHelper(dataStorage).moveFolder(dataStorage, oldPath, newPath);
    }

    @Override
    public DataStorageFile copyFile(final GSBucketStorage dataStorage, final String oldPath, final String newPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageFolder copyFolder(final GSBucketStorage dataStorage, final String oldPath, final String newPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean checkStorage(final GSBucketStorage dataStorage) {
        return getHelper(dataStorage).checkStorageExists(dataStorage.getPath());
    }

    @Override
    public Map<String, String> updateObjectTags(final GSBucketStorage dataStorage, final String path,
                                                final Map<String, String> tags, final String version) {
        return getHelper(dataStorage).updateMetadata(dataStorage, path, tags, version);
    }

    @Override
    public Map<String, String> listObjectTags(final GSBucketStorage dataStorage, final String path,
                                              final String version) {
        return getHelper(dataStorage).listMetadata(dataStorage, path, version);
    }

    @Override
    public Map<String, String> deleteObjectTags(final GSBucketStorage dataStorage, final String path,
                                                final Set<String> tagsToDelete, final String version) {
        return getHelper(dataStorage).deleteMetadata(dataStorage, path, tagsToDelete, version);
    }

    @Override
    public DataStorageItemContent getFile(final GSBucketStorage dataStorage, final String path, final String version,
                                          final Long maxDownloadSize) {
        return getHelper(dataStorage).getFileContent(dataStorage, path, version, maxDownloadSize);
    }

    @Override
    public DataStorageStreamingContent getStream(final GSBucketStorage dataStorage, final String path,
                                                 final String version) {
        return getHelper(dataStorage).getFileStream(dataStorage, path, version);
    }

    @Override
    public String buildFullStoragePath(final GSBucketStorage dataStorage, final String name) {
        return name.toLowerCase();
    }

    @Override
    public String getDefaultMountOptions(GSBucketStorage dataStorage) {
        return null;
    }

    @Override
    public PathDescription getDataSize(final GSBucketStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        return getHelper(dataStorage).getDataSize(dataStorage, path, pathDescription);
    }

    @Override
    public void verifyStorageLifecyclePolicyRule(final StorageLifecycleRule rule) {
        throw new UnsupportedOperationException("Lifecycle policy mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyStorageLifecycleRuleExecution(final StorageLifecycleRuleExecution execution) {
        throw new UnsupportedOperationException("Lifecycle policy mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyRestoreActionSupported() {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public String verifyOrDefaultRestoreMode(final StorageRestoreActionRequest actionRequest) {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageItemType getItemType(final GSBucketStorage dataStorage,
                                           final String path,
                                           final String version) {
        throw new UnsupportedOperationException();
    }

    private GSBucketStorageHelper getHelper(final GSBucketStorage storage) {
        final GCPRegion region = cloudRegionManager.getGCPRegion(storage);
        return new GSBucketStorageHelper(gsAuditClient, messageHelper, region, gcpClient);
    }
}

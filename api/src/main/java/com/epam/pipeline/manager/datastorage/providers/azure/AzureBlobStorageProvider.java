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

package com.epam.pipeline.manager.datastorage.providers.azure;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.microsoft.azure.storage.blob.BlobSASPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureBlobStorageProvider implements StorageProvider<AzureBlobStorage> {

    private final CloudRegionManager cloudRegionManager;
    private final MessageHelper messageHelper;
    private final AuthManager authManager;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AZ;
    }

    @Override
    public String createStorage(final AzureBlobStorage storage) {
        return getAzureStorageHelper(storage).createBlobStorage(storage);
    }

    @Override
    public ActionStatus postCreationProcessing(final AzureBlobStorage storage) {
        return ActionStatus.notSupported();
    }

    @Override
    public void deleteStorage(final AzureBlobStorage dataStorage) {
        getAzureStorageHelper(dataStorage).deleteStorage(dataStorage);
    }

    @Override
    public void applyStoragePolicy(final AzureBlobStorage dataStorage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restoreFileVersion(final AzureBlobStorage dataStorage, final String path, final String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageListing getItems(final AzureBlobStorage dataStorage, final String path, final Boolean showVersion,
                                       final Integer pageSize, final String marker) {
        return getAzureStorageHelper(dataStorage).getItems(dataStorage, path, pageSize, marker);
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(final AzureBlobStorage dataStorage,
                                                          final String path,
                                                          final String version) {
        final BlobSASPermission permission = new BlobSASPermission()
            .withRead(true)
            .withAdd(false)
            .withWrite(false);
        return getAzureStorageHelper(dataStorage).generatePresignedUrl(dataStorage, path, permission.toString());
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(final AzureBlobStorage dataStorage,
                                                                       final String path) {
        final BlobSASPermission permission = new BlobSASPermission()
                .withRead(true)
                .withAdd(true)
                .withWrite(true);
        return getAzureStorageHelper(dataStorage).generatePresignedUrl(dataStorage, path, permission.toString());
    }

    @Override
    public DataStorageFile createFile(final AzureBlobStorage dataStorage, final String path, final byte[] contents) {
        return getAzureStorageHelper(dataStorage)
                .createFile(dataStorage, path, contents, authManager.getAuthorizedUser());
    }

    @Override
    public DataStorageFile createFile(final AzureBlobStorage dataStorage,
                                      final String path,
                                      final InputStream dataStream) {
        return getAzureStorageHelper(dataStorage)
                .createFile(dataStorage, path, dataStream, authManager.getAuthorizedUser());
    }

    @Override
    public DataStorageFolder createFolder(final AzureBlobStorage dataStorage, final String path) {
        return getAzureStorageHelper(dataStorage).createFolder(dataStorage, path);
    }

    @Override
    public void deleteFile(final AzureBlobStorage dataStorage, final String path, final String version,
                           final Boolean totally) {
        getAzureStorageHelper(dataStorage).deleteItem(dataStorage, path);
    }

    @Override
    public void deleteFolder(final AzureBlobStorage dataStorage, final String path, final Boolean totally) {
        getAzureStorageHelper(dataStorage).deleteItem(dataStorage, ProviderUtils.withTrailingDelimiter(path));
    }

    @Override
    public DataStorageFile moveFile(final AzureBlobStorage dataStorage, final String oldPath, final String newPath) {
        return getAzureStorageHelper(dataStorage).moveFile(dataStorage, oldPath, newPath);
    }

    @Override
    public DataStorageFolder moveFolder(final AzureBlobStorage dataStorage, final String oldRawPath,
                                        final String newRawPath) {
        return getAzureStorageHelper(dataStorage).moveFolder(dataStorage, oldRawPath, newRawPath);
    }

    @Override
    public boolean checkStorage(final AzureBlobStorage dataStorage) {
        return getAzureStorageHelper(dataStorage).checkStorage(dataStorage);
    }

    @Override
    public Map<String, String> updateObjectTags(final AzureBlobStorage dataStorage, final String path,
                                                final Map<String, String> tags, final String version) {
        return getAzureStorageHelper(dataStorage).updateObjectTags(dataStorage, path, tags);
    }

    @Override
    public Map<String, String> listObjectTags(final AzureBlobStorage dataStorage, final String path,
                                              final String version) {
        return getAzureStorageHelper(dataStorage).listObjectTags(dataStorage, path);
    }

    @Override
    public Map<String, String> deleteObjectTags(final AzureBlobStorage dataStorage, final String path,
                                                final Set<String> tagsToDelete, final String version) {
        return getAzureStorageHelper(dataStorage).deleteObjectTags(dataStorage, path, tagsToDelete);
    }

    @Override
    public DataStorageItemContent getFile(final AzureBlobStorage dataStorage, final String path, final String version,
                                          final Long maxDownloadSize) {
        return getAzureStorageHelper(dataStorage).getFile(dataStorage, path, maxDownloadSize);
    }

    @Override
    public DataStorageStreamingContent getStream(final AzureBlobStorage dataStorage, final String path,
                                                 final String version) {
        return getAzureStorageHelper(dataStorage).getStream(dataStorage, path);
    }

    @Override
    public String buildFullStoragePath(AzureBlobStorage dataStorage, String name) {
        return name.toLowerCase();
    }

    @Override
    public String getDefaultMountOptions(AzureBlobStorage dataStorage) {
        return null;
    }

    @Override
    public PathDescription getDataSize(final AzureBlobStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        return getAzureStorageHelper(dataStorage).getDataSize(dataStorage, path, pathDescription);
    }

    private AzureStorageHelper getAzureStorageHelper(final AzureBlobStorage storage) {
        final AzureRegion region = cloudRegionManager.getAzureRegion(storage);
        final AzureRegionCredentials credentials = cloudRegionManager.loadCredentials(region);
        return new AzureStorageHelper(region, credentials, messageHelper);
    }
}


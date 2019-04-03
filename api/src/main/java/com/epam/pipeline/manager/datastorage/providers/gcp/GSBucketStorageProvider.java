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
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GSBucketStorageProvider implements StorageProvider<GSBucketStorage> {

    private final CloudRegionManager cloudRegionManager;
    private final MessageHelper messageHelper;
    private final GCPClient gcpClient;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.GS;
    }

    @Override
    public String createStorage(final GSBucketStorage storage) throws DataStorageException {
        return getHelper(storage).createGoogleStorage(storage);
    }

    @Override
    public void deleteStorage(final GSBucketStorage dataStorage) throws DataStorageException {
        getHelper(dataStorage).deleteGoogleStorage(dataStorage.getPath());
    }

    @Override
    public void applyStoragePolicy(GSBucketStorage dataStorage) {
        // no op
    }

    @Override
    public void restoreFileVersion(GSBucketStorage dataStorage, String path, String version)
            throws DataStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageListing getItems(final GSBucketStorage dataStorage, final String path, final Boolean showVersion,
                                       final Integer pageSize, String marker) {
        return getHelper(dataStorage).listItems(dataStorage, path, showVersion, pageSize, marker);
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(final GSBucketStorage dataStorage, final String path,
                                                          final String version) {
        return null;
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(GSBucketStorage dataStorage,
                                                                       String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageFile createFile(final GSBucketStorage dataStorage, final String path, final byte[] contents)
            throws DataStorageException {
        return getHelper(dataStorage).createFile(dataStorage, path, contents);
    }

    @Override
    public DataStorageFile createFile(final GSBucketStorage dataStorage, final String path,
                                      final InputStream dataStream) throws DataStorageException {
        return getHelper(dataStorage).createFile(dataStorage, path, dataStream);
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
    public boolean checkStorage(final GSBucketStorage dataStorage) {
        return getHelper(dataStorage).checkStorageExists(dataStorage.getPath());
    }

    @Override
    public Map<String, String> updateObjectTags(GSBucketStorage dataStorage, String path, Map<String, String> tags,
                                                String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> listObjectTags(GSBucketStorage dataStorage, String path, String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> deleteObjectTags(GSBucketStorage dataStorage, String path, Set<String> tagsToDelete,
                                                String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageItemContent getFile(GSBucketStorage dataStorage, String path, String version,
                                          Long maxDownloadSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageStreamingContent getStream(GSBucketStorage dataStorage, String path, String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String buildFullStoragePath(GSBucketStorage dataStorage, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDefaultMountOptions(GSBucketStorage dataStorage) {
        return null;
    }

    private GSBucketStorageHelper getHelper(final GSBucketStorage storage) {
        final GCPRegion gcpRegion = cloudRegionManager.getGCPRegion(storage);
        return new GSBucketStorageHelper(messageHelper, gcpRegion, gcpClient);
    }
}

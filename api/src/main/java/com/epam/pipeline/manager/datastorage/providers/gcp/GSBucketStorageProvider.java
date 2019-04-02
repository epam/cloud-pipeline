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
    public DataStorageListing getItems(GSBucketStorage dataStorage, String path, Boolean showVersion,
                                       Integer pageSize, String marker) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(GSBucketStorage dataStorage, String path,
                                                          String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(GSBucketStorage dataStorage,
                                                                       String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageFile createFile(GSBucketStorage dataStorage, String path, byte[] contents)
            throws DataStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageFile createFile(GSBucketStorage dataStorage, String path, InputStream dataStream)
            throws DataStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageFolder createFolder(GSBucketStorage dataStorage, String path)
            throws DataStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteFile(GSBucketStorage dataStorage, String path, String version,
                           Boolean totally) throws DataStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteFolder(GSBucketStorage dataStorage, String path, Boolean totally) throws DataStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageFile moveFile(GSBucketStorage dataStorage, String oldPath, String newPath)
            throws DataStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageFolder moveFolder(GSBucketStorage dataStorage, String oldPath, String newPath)
            throws DataStorageException {
        throw new UnsupportedOperationException();
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
        return new GSBucketStorageHelper(messageHelper, gcpRegion);
    }
}

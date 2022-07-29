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

package com.epam.pipeline.manager.datastorage.providers;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.region.VersioningAwareRegion;

public interface StorageProvider<T extends AbstractDataStorage> {
    DataStorageType getStorageType();

    String createStorage(T storage) throws DataStorageException;

    ActionStatus postCreationProcessing(T storage);

    void deleteStorage(T dataStorage) throws DataStorageException;

    void applyStoragePolicy(T dataStorage);

    void restoreFileVersion(T dataStorage, String path, String version)
            throws DataStorageException;

    Stream<DataStorageFile> listDataStorageFiles(T dataStorage, String path);

    DataStorageListing getItems(T dataStorage, String path,
            Boolean showVersion, Integer pageSize, String marker);

    Optional<DataStorageFile> findFile(T dataStorage, String path, String version);

    DataStorageDownloadFileUrl generateDownloadURL(T dataStorage, String path, String version,
                                                   ContentDisposition contentDisposition);

    DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(T dataStorage, String path);

    DataStorageDownloadFileUrl generateUrl(T dataStorage, String path, List<String> permissions, Duration duration);

    DataStorageFile createFile(T dataStorage, String path, byte[] contents)
            throws DataStorageException;

    DataStorageFile createFile(T dataStorage, String path, InputStream dataStream)
        throws DataStorageException;

    DataStorageFolder createFolder(T dataStorage, String path)
            throws DataStorageException;

    void deleteFile(T dataStorage, String path, String version, Boolean totally)
            throws DataStorageException;

    void deleteFolder(T dataStorage, String path, Boolean totally)
            throws DataStorageException;

    DataStorageFile moveFile(T dataStorage, String oldPath, String newPath)
            throws DataStorageException;

    DataStorageFolder moveFolder(T dataStorage, String oldPath, String newPath)
            throws DataStorageException;

    DataStorageFile copyFile(T dataStorage, String oldPath, String newPath);

    DataStorageFolder copyFolder(T dataStorage, String oldPath, String newPath);

    boolean checkStorage(T dataStorage);

    Map<String, String> updateObjectTags(T dataStorage, String path, Map<String, String> tags,
                                         String version);

    Map<String, String> listObjectTags(T dataStorage, String path, String version);

    Map<String, String> deleteObjectTags(T dataStorage, String path, Set<String> tagsToDelete,
                                         String version);

    DataStorageItemContent getFile(T dataStorage, String path, String version, Long maxDownloadSize);

    DataStorageStreamingContent getStream(T dataStorage, String path, String version);

    String buildFullStoragePath(T dataStorage, String name);

    String getDefaultMountOptions(T dataStorage);

    default StoragePolicy buildPolicy(VersioningAwareRegion region, StoragePolicy storagePolicy) {
        if (storagePolicy == null) {
            StoragePolicy defaultPolicy = new StoragePolicy();
            defaultPolicy.setVersioningEnabled(region.isVersioningEnabled());
            defaultPolicy.setBackupDuration(region.getBackupDuration());
            return defaultPolicy;
        }
        if (storagePolicy.getVersioningEnabled() == null) {
            storagePolicy.setVersioningEnabled(region.isVersioningEnabled());
        }
        if (storagePolicy.getBackupDuration() == null) {
            storagePolicy.setBackupDuration(region.getBackupDuration());
        }
        return storagePolicy;
    }

    PathDescription getDataSize(T dataStorage, String path, PathDescription pathDescription);

    void verifyStorageLifecyclePolicyRule(StorageLifecycleRule rule);

}

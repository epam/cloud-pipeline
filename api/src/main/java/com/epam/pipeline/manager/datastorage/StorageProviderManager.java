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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
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
import com.epam.pipeline.manager.datastorage.leakagepolicy.SensitiveStorageOperation;
import com.epam.pipeline.manager.datastorage.leakagepolicy.StorageWriteOperation;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@SuppressWarnings("unchecked")
public class StorageProviderManager {
    @Autowired
    private PreferenceManager preferenceManager;

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageProviderManager.class);

    @Autowired
    private MessageHelper messageHelper;


    private Map<DataStorageType, StorageProvider> storageProviders;

    @Autowired
    public void setStorageProviders(List<StorageProvider> providers) {
        this.storageProviders = CommonUtils.groupByKey(providers, StorageProvider::getStorageType);
    }

    public StorageProvider getStorageProvider(AbstractDataStorage dataStorage) {
        StorageProvider<? extends AbstractDataStorage> provider = storageProviders.get(dataStorage.getType());
        if (provider == null) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_NOT_SUPPORTED,
                    dataStorage.getName(), dataStorage.getType()));
        }
        return provider;
    }

    public String createBucket(AbstractDataStorage dataStorage) throws DataStorageException {
        return getStorageProvider(dataStorage).createStorage(dataStorage);
    }

    public ActionStatus postCreationProcessing(final AbstractDataStorage dataStorage) {
        return getStorageProvider(dataStorage).postCreationProcessing(dataStorage);
    }

    public void deleteBucket(AbstractDataStorage dataStorage) throws DataStorageException {
        LOGGER.debug("Start the process of deleting of the {} bucket: {}",
                dataStorage.getType(), dataStorage.getPath());
        getStorageProvider(dataStorage).deleteStorage(dataStorage);
    }

    public void applyStoragePolicy(AbstractDataStorage dataStorage) {
        getStorageProvider(dataStorage).applyStoragePolicy(dataStorage);
    }

    public void restoreFileVersion(AbstractDataStorage dataStorage, String path, String version)
            throws DataStorageException {
        getStorageProvider(dataStorage).restoreFileVersion(dataStorage, path, version);
    }

    public DataStorageListing getItems(AbstractDataStorage dataStorage, String path,
            Boolean showVersion, Integer pageSize, String marker) {
        return getStorageProvider(dataStorage).getItems(dataStorage, path, showVersion, pageSize, marker);
    }

    public DataStorageFile getFileMetadata(AbstractDataStorage dataStorage, String path) {
        return getStorageProvider(dataStorage).getFileMetadata(dataStorage, path);
    }

    public Stream<DataStorageFile> listFiles(AbstractDataStorage dataStorage, String path) {
        return getStorageProvider(dataStorage).listDataStorageFiles(dataStorage, path);
    }

    public Stream<DataStorageFile> listFileVersions(AbstractDataStorage dataStorage, String path) {
        return getStorageProvider(dataStorage).listDataStorageFileVersions(dataStorage, path);
    }

    @SensitiveStorageOperation
    public DataStorageDownloadFileUrl generateDownloadURL(AbstractDataStorage dataStorage,
                                                          String path, String version,
                                                          ContentDisposition contentDisposition) {
        return getStorageProvider(dataStorage).generateDownloadURL(dataStorage, path, version, contentDisposition);
    }

    @StorageWriteOperation
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(AbstractDataStorage dataStorage,
                                                                       String path) {
        return getStorageProvider(dataStorage).generateDataStorageItemUploadUrl(dataStorage, path);
    }

    @SensitiveStorageOperation
    public DataStorageDownloadFileUrl generateUrl(AbstractDataStorage dataStorage,
                                                  String path,
                                                  List<String> permissions,
                                                  Duration duration) {
        return getStorageProvider(dataStorage).generateUrl(dataStorage, path, permissions, duration);
    }

    @StorageWriteOperation
    public DataStorageFile createFile(AbstractDataStorage dataStorage, String path, byte[] contents)
            throws DataStorageException {
        return getStorageProvider(dataStorage).createFile(dataStorage, path, contents);
    }

    @StorageWriteOperation
    public DataStorageFile createFile(AbstractDataStorage dataStorage, String path, InputStream contentStream)
            throws DataStorageException {
        return getStorageProvider(dataStorage).createFile(dataStorage, path, contentStream);
    }

    @StorageWriteOperation
    public DataStorageFolder createFolder(AbstractDataStorage dataStorage, String path)
            throws DataStorageException {
        return getStorageProvider(dataStorage).createFolder(dataStorage, path);
    }

    @StorageWriteOperation
    public void deleteFile(AbstractDataStorage dataStorage, String path, String version, Boolean totally)
            throws DataStorageException {
        getStorageProvider(dataStorage).deleteFile(dataStorage, path, version, totally);
    }

    @StorageWriteOperation
    public void deleteFolder(AbstractDataStorage dataStorage, String path, Boolean totally)
            throws DataStorageException {
        getStorageProvider(dataStorage).deleteFolder(dataStorage, path, totally);
    }

    @StorageWriteOperation
    public DataStorageFile moveFile(AbstractDataStorage dataStorage, String oldPath, String newPath)
            throws DataStorageException {
        return getStorageProvider(dataStorage).moveFile(dataStorage, oldPath, newPath);
    }

    @StorageWriteOperation
    public DataStorageFolder moveFolder(AbstractDataStorage dataStorage, String oldPath, String newPath)
            throws DataStorageException {
        return getStorageProvider(dataStorage).moveFolder(dataStorage, oldPath, newPath);
    }

    public boolean checkStorage(AbstractDataStorage dataStorage) {
        return getStorageProvider(dataStorage).checkStorage(dataStorage);
    }

    public Map<String, String> updateObjectTags(AbstractDataStorage dataStorage, String path, Map<String, String> tags,
                                 String version) {
        return getStorageProvider(dataStorage).updateObjectTags(dataStorage, path, tags, version);
    }

    public Map<String, String> listObjectTags(AbstractDataStorage dataStorage, String path, String version) {
        return getStorageProvider(dataStorage).listObjectTags(dataStorage, path, version);
    }

    public Map<String, String> deleteObjectTags(AbstractDataStorage dataStorage, String path, Set<String> tags,
                                                String version) {
        return getStorageProvider(dataStorage).deleteObjectTags(dataStorage, path, tags, version);
    }

    @SensitiveStorageOperation
    public DataStorageItemContent getFile(AbstractDataStorage dataStorage, String path, String version) {
        long maxDownloadSize = preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_MAX_DOWNLOAD_SIZE);
        return getStorageProvider(dataStorage).getFile(dataStorage, path, version, maxDownloadSize);
    }

    @SensitiveStorageOperation
    public DataStorageStreamingContent getFileStream(AbstractDataStorage dataStorage, String path, String version) {
        return getStorageProvider(dataStorage).getStream(dataStorage, path, version);
    }

    public String buildFullStoragePath(AbstractDataStorage dataStorage, String name) {
        return getStorageProvider(dataStorage).buildFullStoragePath(dataStorage, name);
    }

    public PathDescription getDataSize(final AbstractDataStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        return getStorageProvider(dataStorage).getDataSize(dataStorage, path, pathDescription);
    }
}

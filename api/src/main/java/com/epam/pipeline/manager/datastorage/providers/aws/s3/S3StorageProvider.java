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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.services.s3.model.CORSRule;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
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
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class S3StorageProvider implements StorageProvider<S3bucketDataStorage> {

    private final AuthManager authManager;
    private final MessageHelper messageHelper;
    private final CloudRegionManager cloudRegionManager;
    private final PreferenceManager preferenceManager;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.S3;
    }

    @Override
    public String createStorage(final S3bucketDataStorage storage) {
        return getS3Helper(storage).createS3Bucket(storage.getPath());
    }

    @Override
    public ActionStatus postCreationProcessing(final S3bucketDataStorage storage) {
        final AwsRegion awsRegion = getAwsRegion(storage);

        final Map<String, String> tags = new HashMap<>();
        final CloudRegionsConfiguration configuration = preferenceManager.getObjectPreferenceAs(
                SystemPreferences.CLUSTER_NETWORKS_CONFIG, new TypeReference<CloudRegionsConfiguration>() {});
        if (configuration != null && !CollectionUtils.isEmpty(configuration.getTags())) {
            tags.putAll(configuration.getTags());
        }

        if (storage.getRegionId() == null) {
            storage.setRegionId(awsRegion.getId());
        }

        final ObjectMapper corsRulesMapper = JsonMapper.newInstance()
                .addMixIn(CORSRule.class, AbstractCORSRuleMixin.class)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

        final List<CORSRule> corsPolicyRules = JsonMapper.parseData(awsRegion.getCorsRules(),
                new TypeReference<List<CORSRule>>() {}, corsRulesMapper);

        return getS3Helper(storage).postCreationProcessing(storage.getPath(), awsRegion.getPolicy(),
                storage.getAllowedCidrs(), corsPolicyRules, awsRegion, storage.isShared(), tags);
    }

    @Override
    public void deleteStorage(S3bucketDataStorage dataStorage) {
        getS3Helper(dataStorage).deleteS3Bucket(dataStorage.getPath());

    }

    @Override
    public void applyStoragePolicy(S3bucketDataStorage dataStorage) {
        final AwsRegion awsRegion = getAwsRegion(dataStorage);
        final StoragePolicy storagePolicy = buildStoragePolicy(awsRegion, dataStorage.getStoragePolicy());
        getS3Helper(dataStorage).applyStoragePolicy(dataStorage.getPath(), storagePolicy);
        dataStorage.setStoragePolicy(storagePolicy);
    }

    @Override
    public void restoreFileVersion(S3bucketDataStorage dataStorage, String path, String version) {
        getS3Helper(dataStorage).restoreFileVersion(dataStorage.getPath(), path, version);
    }

    @Override
    public DataStorageItemContent getFile(S3bucketDataStorage dataStorage, String path,
            String version, Long maxDownloadSize) {
        return getS3Helper(dataStorage).getFileContent(dataStorage, path, version, maxDownloadSize);
    }

    @Override
    public DataStorageStreamingContent getStream(S3bucketDataStorage dataStorage, String path, String version) {
        return getS3Helper(dataStorage).getFileStream(dataStorage, path, version);
    }

    @Override
    public DataStorageListing getItems(S3bucketDataStorage dataStorage, String path,
            Boolean showVersion, Integer pageSize, String marker) {
        return getS3Helper(dataStorage).getItems(dataStorage.getPath(), path, showVersion, pageSize, marker);
    }

    @Override public DataStorageDownloadFileUrl generateDownloadURL(S3bucketDataStorage dataStorage,
                                                                    String path, String version,
                                                                    ContentDisposition contentDisposition) {
        return getS3Helper(dataStorage).generateDownloadURL(dataStorage.getPath(), path, version, contentDisposition);
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(S3bucketDataStorage dataStorage, String path) {
        return getS3Helper(dataStorage).generateDataStorageItemUploadUrl(
                dataStorage.getPath(), path, authManager.getAuthorizedUser());
    }

    @Override public DataStorageFile createFile(S3bucketDataStorage dataStorage, String path,
            byte[] contents) {
        return getS3Helper(dataStorage).createFile(
                dataStorage.getPath(), path, contents, authManager.getAuthorizedUser());
    }

    @Override
    public DataStorageFile createFile(S3bucketDataStorage dataStorage, String path, InputStream dataStream)
        throws DataStorageException {
        return getS3Helper(dataStorage).createFile(
                dataStorage.getPath(), path, dataStream, authManager.getAuthorizedUser());
    }

    @Override public DataStorageFolder createFolder(S3bucketDataStorage dataStorage, String path) {
        return getS3Helper(dataStorage).createFolder(dataStorage.getPath(), path);
    }

    @Override
    public void deleteFile(S3bucketDataStorage dataStorage, String path, String version, Boolean totally) {
        getS3Helper(dataStorage).deleteFile(dataStorage.getPath(), path, version,
                totally && dataStorage.isVersioningEnabled());
    }

    @Override
    public void deleteFolder(S3bucketDataStorage dataStorage, String path, Boolean totally) {
        getS3Helper(dataStorage)
                .deleteFolder(dataStorage.getPath(), path, totally && dataStorage.isVersioningEnabled());

    }

    @Override public DataStorageFile moveFile(S3bucketDataStorage dataStorage, String oldPath,
            String newPath) throws DataStorageException {
        return getS3Helper(dataStorage).moveFile(dataStorage.getPath(), oldPath, newPath);
    }

    @Override public DataStorageFolder moveFolder(S3bucketDataStorage dataStorage, String oldPath,
            String newPath) throws DataStorageException {
        return getS3Helper(dataStorage).moveFolder(dataStorage.getPath(), oldPath, newPath);
    }

    @Override public boolean checkStorage(S3bucketDataStorage dataStorage) {
        if (dataStorage.getRegionId() == null) {
            AwsRegion awsRegion = cloudRegionManager.getAwsRegion(dataStorage);
            dataStorage.setRegionId(awsRegion.getId());
        }
        return getS3Helper(dataStorage).checkBucket(dataStorage.getPath());
    }

    @Override
    public Map<String, String> updateObjectTags(S3bucketDataStorage dataStorage, String path, Map<String, String> tags,
                                 String version) {
        return getS3Helper(dataStorage).updateObjectTags(dataStorage, path, tags, version);
    }

    @Override
    public Map<String, String> listObjectTags(S3bucketDataStorage dataStorage, String path, String version) {
        return getS3Helper(dataStorage).listObjectTags(dataStorage, path, version);
    }

    @Override
    public Map<String, String> deleteObjectTags(S3bucketDataStorage dataStorage, String path, Set<String> tagsToDelete,
                                                String version) {
        return getS3Helper(dataStorage).deleteObjectTags(dataStorage, path, tagsToDelete, version);
    }

    @Override
    public String buildFullStoragePath(S3bucketDataStorage dataStorage, String name) {
        return name.toLowerCase();
    }

    @Override
    public String getDefaultMountOptions(S3bucketDataStorage dataStorage) {
        return "--dir-mode 0774 --file-mode 0774 -o rw -o allow_other -f --gid 0";
    }

    @Override
    public PathDescription getDataSize(final S3bucketDataStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        return getS3Helper(dataStorage).getDataSize(dataStorage, path, pathDescription);
    }

    public S3Helper getS3Helper(S3bucketDataStorage dataStorage) {
        AwsRegion region = getAwsRegion(dataStorage);
        return new RegionAwareS3Helper(region, messageHelper);
    }

    private AwsRegion getAwsRegion(S3bucketDataStorage dataStorage) {
        return cloudRegionManager.getAwsRegion(dataStorage);
    }

    private StoragePolicy buildStoragePolicy(final AwsRegion awsRegion,
                                             final StoragePolicy storagePolicy) {
         final Integer incompleteUploadCleanupDays = preferenceManager
                .getPreference(SystemPreferences.STORAGE_INCOMPLETE_UPLOAD_CLEAN_DAYS);

        if (storagePolicy == null) {
            StoragePolicy defaultPolicy = new StoragePolicy();
            defaultPolicy.setVersioningEnabled(awsRegion.isVersioningEnabled());
            defaultPolicy.setBackupDuration(awsRegion.getBackupDuration());
            defaultPolicy.setIncompleteUploadCleanupDays(incompleteUploadCleanupDays);
            return defaultPolicy;
        }
        if (storagePolicy.getVersioningEnabled() == null) {
            storagePolicy.setVersioningEnabled(awsRegion.isVersioningEnabled());
        }
        if (storagePolicy.getBackupDuration() == null) {
            storagePolicy.setBackupDuration(awsRegion.getBackupDuration());
        }
        storagePolicy.setIncompleteUploadCleanupDays(incompleteUploadCleanupDays);
        return storagePolicy;
    }
}

/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper.validateFilePathMatchingMasks;
import static com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper.resolveFolderPathListingMasks;
import static com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper.validateFolderPathMatchingMasks;

import com.amazonaws.services.s3.model.CORSRule;
import com.amazonaws.services.s3.model.StorageClass;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.DatastoragePath;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.VersioningAwareRegion;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;
import com.epam.pipeline.manager.cloud.aws.S3TemporaryCredentialsGenerator;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageProvider implements StorageProvider<S3bucketDataStorage> {

    /**
     * See {@link StorageClass} from s3 model.
     * */
    private static final List<String> SUPPORTED_STORAGE_CLASSES = Arrays.asList(
            "GLACIER", "DEEP_ARCHIVE", "GLACIER_IR", "DELETION");

    public static final String STANDARD_RESTORE_MODE = "STANDARD";
    private static final List<String> SUPPORTED_RESTORE_MODES = Arrays.asList(STANDARD_RESTORE_MODE, "BULK");
    private final AuthManager authManager;
    private final MessageHelper messageHelper;
    private final CloudRegionManager cloudRegionManager;
    private final PreferenceManager preferenceManager;
    private final S3TemporaryCredentialsGenerator stsCredentialsGenerator;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.S3;
    }

    @Override
    public String createStorage(final S3bucketDataStorage storage) {
        final S3Helper s3Helper = getS3Helper(storage);
        final DatastoragePath datastoragePath = ProviderUtils.parsePath(storage.getPath());
        final String prefix = datastoragePath.getPath();
        if (!StringUtils.isNotBlank(prefix) || !checkStorage(storage)) {
            s3Helper.createS3Bucket(datastoragePath.getRoot());
        }
        if (StringUtils.isNotBlank(prefix)) {
            try {
                s3Helper.createFile(datastoragePath.getRoot(), ProviderUtils.withTrailingDelimiter(prefix),
                        new byte[]{}, authManager.getAuthorizedUser());
            } catch (DataStorageException e) {
                log.debug("Failed to create file {}.", prefix);
                log.debug(e.getMessage(), e);
            }
        }
        return storage.getPath();
    }

    @Override
    public ActionStatus postCreationProcessing(final S3bucketDataStorage storage) {
        final AwsRegion awsRegion = getAwsRegion(storage);

        final Map<String, String> tags = new HashMap<>();
        final CloudRegionsConfiguration configuration = preferenceManager.getObjectPreferenceAs(
                SystemPreferences.CLUSTER_NETWORKS_CONFIG, new TypeReference<CloudRegionsConfiguration>() {});
        if (configuration != null && !MapUtils.isEmpty(configuration.getTags())) {
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
        final String kmsKeyId = AWSUtils.getKeyArnValue(storage, awsRegion);
        return getS3Helper(storage).postCreationProcessing(storage.getRoot(), awsRegion.getPolicy(),
                storage.getAllowedCidrs(), corsPolicyRules, awsRegion, storage.isShared(), kmsKeyId, tags);
    }

    @Override
    public void deleteStorage(final S3bucketDataStorage dataStorage) {
        final DatastoragePath datastoragePath = ProviderUtils.parsePath(dataStorage.getPath());
        if (StringUtils.isNotBlank(datastoragePath.getPath())) {
            getS3Helper(dataStorage).deleteFolder(datastoragePath.getRoot(), datastoragePath.getPath(), true);
        } else {
            getS3Helper(dataStorage).deleteS3Bucket(dataStorage.getPath());
        }
    }

    @Override
    public void applyStoragePolicy(final S3bucketDataStorage dataStorage) {
        final AwsRegion awsRegion = getAwsRegion(dataStorage);
        final StoragePolicy storagePolicy = buildPolicy(awsRegion, dataStorage.getStoragePolicy());
        getS3Helper(dataStorage).applyStoragePolicy(dataStorage.getRoot(), storagePolicy);
        dataStorage.setStoragePolicy(storagePolicy);
    }

    @Override
    public StoragePolicy buildPolicy(final VersioningAwareRegion region, final StoragePolicy storagePolicy) {
        final StoragePolicy policy = StorageProvider.super.buildPolicy(region, storagePolicy);
        final Integer incompleteUploadCleanupDays = preferenceManager
                .getPreference(SystemPreferences.STORAGE_INCOMPLETE_UPLOAD_CLEAN_DAYS);
        policy.setIncompleteUploadCleanupDays(incompleteUploadCleanupDays);
        return policy;
    }

    @Override
    public void restoreFileVersion(S3bucketDataStorage dataStorage, String path, String version) {
        validateFilePathMatchingMasks(dataStorage, path);
        getS3Helper(dataStorage).restoreFileVersion(dataStorage.getRoot(),
                ProviderUtils.buildPath(dataStorage, path), version);
    }

    @Override
    public DataStorageItemContent getFile(S3bucketDataStorage dataStorage, String path,
                                          String version, Long maxDownloadSize) {
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).getFileContent(dataStorage,
                ProviderUtils.buildPath(dataStorage, path), version, maxDownloadSize);
    }

    @Override
    public DataStorageStreamingContent getStream(S3bucketDataStorage dataStorage, String path, String version) {
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).getFileStream(dataStorage,
                ProviderUtils.buildPath(dataStorage, path), version);
    }

    @Override
    public DataStorageListing getItems(S3bucketDataStorage dataStorage, String path,
            Boolean showVersion, Integer pageSize, String marker) {
        final DatastoragePath datastoragePath = ProviderUtils.parsePath(dataStorage.getPath());
        final Set<String> activeLinkingMasks = resolveFolderPathListingMasks(dataStorage, path);
        return getS3Helper(dataStorage)
            .getItems(datastoragePath.getRoot(),
                      ProviderUtils.buildPath(dataStorage, path), showVersion, pageSize, marker,
                      ProviderUtils.withTrailingDelimiter(datastoragePath.getPath()),
                      Optional.of(activeLinkingMasks).filter(CollectionUtils::isNotEmpty).orElse(null));
    }

    @Override
    public Optional<DataStorageFile> findFile(final S3bucketDataStorage dataStorage,
                                              final String path,
                                              final String version) {
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage)
                .findFile(dataStorage.getRoot(), ProviderUtils.buildPath(dataStorage, path), version);
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(S3bucketDataStorage dataStorage,
                                                          String path, String version,
                                                          ContentDisposition contentDisposition) {
        validateFilePathMatchingMasks(dataStorage, path);
        final TemporaryCredentials credentials = getStsCredentials(dataStorage, version, false);
        return getS3Helper(credentials, getAwsRegion(dataStorage)).generateDownloadURL(dataStorage.getRoot(),
                ProviderUtils.buildPath(dataStorage, path), version, contentDisposition);
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(S3bucketDataStorage dataStorage, String path) {
        validateFilePathMatchingMasks(dataStorage, path);
        final TemporaryCredentials credentials = getStsCredentials(dataStorage, null, true);
        return getS3Helper(credentials, getAwsRegion(dataStorage)).generateDataStorageItemUploadUrl(
                dataStorage.getRoot(), ProviderUtils.buildPath(dataStorage, path), authManager.getAuthorizedUser());
    }

    @Override
    public DataStorageDownloadFileUrl generateUrl(final S3bucketDataStorage dataStorage,
                                                  final String path,
                                                  final List<String> permissions,
                                                  final Duration duration) {
        return generateDownloadURL(dataStorage, path, null, null);
    }

    @Override public DataStorageFile createFile(S3bucketDataStorage dataStorage, String path,
            byte[] contents) {
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).createFile(
                dataStorage.getRoot(), ProviderUtils.buildPath(dataStorage, path), contents,
                authManager.getAuthorizedUser());
    }

    @Override
    public DataStorageFile createFile(S3bucketDataStorage dataStorage, String path, InputStream dataStream)
        throws DataStorageException {
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).createFile(
                dataStorage.getRoot(), ProviderUtils.buildPath(dataStorage, path),
                dataStream, authManager.getAuthorizedUser());
    }

    @Override public DataStorageFolder createFolder(S3bucketDataStorage dataStorage, String path) {
        validateFolderPathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).createFolder(dataStorage.getRoot(),
                ProviderUtils.buildPath(dataStorage, path));
    }

    @Override
    public Stream<DataStorageFile> listDataStorageFiles(final S3bucketDataStorage dataStorage,
                                                        final String path) {
        final Set<String> activeLinkingMasks = resolveFolderPathListingMasks(dataStorage, path);
        if (CollectionUtils.isEmpty(activeLinkingMasks)) {
            return getS3Helper(dataStorage).listDataStorageFiles(dataStorage.getRoot(),
                                                                 ProviderUtils.buildPath(dataStorage, path));
        } else {
            final Set<String> fileMasks = S3Helper.extractFileMasks(activeLinkingMasks);
            final Set<String> folderMasks = S3Helper.extractFolderMasks(activeLinkingMasks);
            return getS3Helper(dataStorage).listDataStorageFiles(dataStorage.getRoot(),
                                                                 ProviderUtils.buildPath(dataStorage, path))
                .filter(item -> ProviderUtils.dataStorageItemMatching(item, fileMasks, folderMasks));
        }
    }

    @Override
    public void deleteFile(S3bucketDataStorage dataStorage, String path, String version, Boolean totally) {
        validateFilePathMatchingMasks(dataStorage, path);
        getS3Helper(dataStorage).deleteFile(dataStorage.getRoot(),
                ProviderUtils.buildPath(dataStorage, path), version,
                totally && dataStorage.isVersioningEnabled());
    }

    @Override
    public void deleteFolder(S3bucketDataStorage dataStorage, String path, Boolean totally) {
        validateFolderPathMatchingMasks(dataStorage, path);
        getS3Helper(dataStorage)
                .deleteFolder(dataStorage.getRoot(),
                        ProviderUtils.buildPath(dataStorage, path), totally && dataStorage.isVersioningEnabled());

    }

    @Override public DataStorageFile moveFile(S3bucketDataStorage dataStorage, String oldPath,
            String newPath) throws DataStorageException {
        validateFilePathMatchingMasks(dataStorage, oldPath);
        validateFilePathMatchingMasks(dataStorage, newPath);
        return getS3Helper(dataStorage).moveFile(dataStorage.getRoot(),
                ProviderUtils.buildPath(dataStorage, oldPath),
                ProviderUtils.buildPath(dataStorage, newPath));
    }

    @Override public DataStorageFolder moveFolder(S3bucketDataStorage dataStorage, String oldPath,
            String newPath) throws DataStorageException {
        validateFolderPathMatchingMasks(dataStorage, oldPath);
        validateFolderPathMatchingMasks(dataStorage, newPath);
        return getS3Helper(dataStorage).moveFolder(dataStorage.getRoot(),
                ProviderUtils.buildPath(dataStorage, oldPath),
                ProviderUtils.buildPath(dataStorage, newPath));
    }

    @Override
    public DataStorageFile copyFile(final S3bucketDataStorage dataStorage, final String oldPath, final String newPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageFolder copyFolder(final S3bucketDataStorage dataStorage, final String oldPath,
                                        final String newPath) {
        throw new UnsupportedOperationException();
    }

    @Override public boolean checkStorage(S3bucketDataStorage dataStorage) {
        if (dataStorage.getRegionId() == null) {
            final AwsRegion awsRegion = cloudRegionManager.getAwsRegion(dataStorage);
            dataStorage.setRegionId(awsRegion.getId());
        }
        final DatastoragePath datastoragePath = ProviderUtils.parsePath(dataStorage.getPath());
        final S3Helper s3Helper = getS3Helper(dataStorage);
        final boolean exists = s3Helper.checkBucket(datastoragePath.getRoot());
        if (!exists) {
            return false;
        }
        if (!dataStorage.isSensitive() && StringUtils.isNotBlank(datastoragePath.getPath())) {
            try {
                s3Helper.createFile(datastoragePath.getRoot(),
                    ProviderUtils.withTrailingDelimiter(datastoragePath.getPath()),
                    new byte[]{}, authManager.getAuthorizedUser());
            } catch (DataStorageException e) {
                log.debug("Failed to create file {}.", datastoragePath.getPath());
                log.debug(e.getMessage(), e);
            }
        }
        return true;
    }

    @Override
    public Map<String, String> updateObjectTags(S3bucketDataStorage dataStorage, String path, Map<String, String> tags,
                                 String version) {
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).updateObjectTags(dataStorage,
                ProviderUtils.buildPath(dataStorage, path), tags, version);
    }

    @Override
    public Map<String, String> listObjectTags(S3bucketDataStorage dataStorage, String path, String version) {
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).listObjectTags(dataStorage,
                ProviderUtils.buildPath(dataStorage, path), version);
    }

    @Override
    public Map<String, String> deleteObjectTags(S3bucketDataStorage dataStorage, String path, Set<String> tagsToDelete,
                                                String version) {
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).deleteObjectTags(dataStorage,
                ProviderUtils.buildPath(dataStorage, path), tagsToDelete, version);
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
        validateFilePathMatchingMasks(dataStorage, path);
        return getS3Helper(dataStorage).getDataSize(dataStorage,
                ProviderUtils.buildPath(dataStorage, path), pathDescription);
    }

    @Override
    public void verifyStorageLifecyclePolicyRule(final StorageLifecycleRule rule) {
        rule.getTransitions().forEach(t -> {
            Assert.isTrue(SUPPORTED_STORAGE_CLASSES.contains(t.getStorageClass()),
                    "Storage class should be one of: " + SUPPORTED_STORAGE_CLASSES);
            Assert.isTrue(t.getTransitionAfterDays() != null || t.getTransitionDate() != null,
                    "transitionAfterDays or transitionDate should be provided!");
            Assert.isTrue(!(t.getTransitionAfterDays() != null && t.getTransitionDate() != null),
                    "Only transitionAfterDays or transitionDate could be provided, but not both!");
        });
    }

    @Override
    public void verifyStorageLifecycleRuleExecution(final StorageLifecycleRuleExecution execution) {
        Assert.isTrue(SUPPORTED_STORAGE_CLASSES.contains(execution.getStorageClass()),
                "Storage class should be one of: " + SUPPORTED_STORAGE_CLASSES);
    }

    @Override
    public void verifyRestoreActionSupported() {
        // s3 provider supports restore - nothing to do
    }

    @Override
    public String verifyOrDefaultRestoreMode(final StorageRestoreActionRequest restoreActionRequest) {
        if (StringUtils.isEmpty(restoreActionRequest.getRestoreMode())) {
            return STANDARD_RESTORE_MODE;
        }
        Assert.isTrue(SUPPORTED_RESTORE_MODES.contains(restoreActionRequest.getRestoreMode()),
                "Restore request mode should be one of: " + SUPPORTED_RESTORE_MODES);
        return restoreActionRequest.getRestoreMode();
    }

    public S3Helper getS3Helper(S3bucketDataStorage dataStorage) {
        AwsRegion region = getAwsRegion(dataStorage);
        if (dataStorage.isUseAssumedCredentials()) {
            final String roleArn = Optional.ofNullable(dataStorage.getTempCredentialsRole())
                    .orElse(region.getTempCredentialsRole());
            return new AssumedCredentialsS3Helper(roleArn, region, messageHelper);
        }
        if (StringUtils.isNotBlank(region.getIamRole())) {
            return new AssumedCredentialsS3Helper(region.getIamRole(), region, messageHelper);
        }
        return new RegionAwareS3Helper(region, messageHelper);
    }

    public S3Helper getS3Helper(final TemporaryCredentials credentials, final AwsRegion region) {
        return new TemporaryCredentialsS3Helper(credentials, messageHelper, region);
    }

    private AwsRegion getAwsRegion(S3bucketDataStorage dataStorage) {
        return cloudRegionManager.getAwsRegion(dataStorage);
    }

    private TemporaryCredentials getStsCredentials(final S3bucketDataStorage dataStorage,
                                                   final String version,
                                                   final boolean write) {
        final boolean useVersion = StringUtils.isNotBlank(version);
        final DataStorageAction action = new DataStorageAction();
        action.setId(dataStorage.getId());
        action.setBucketName(dataStorage.getRoot());
        action.setPath(dataStorage.getPath());
        action.setRead(true);
        action.setReadVersion(useVersion);
        action.setWrite(write);
        action.setWriteVersion(useVersion);
        return stsCredentialsGenerator
                .generate(Collections.singletonList(action), Collections.singletonList(dataStorage));
    }
}

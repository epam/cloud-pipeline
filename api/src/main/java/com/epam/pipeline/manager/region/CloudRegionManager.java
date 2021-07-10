/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.info.CloudRegionInfo;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import com.epam.pipeline.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@AclSync
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class CloudRegionManager implements SecuredEntityManager {

    public static final String CP_REGION_CREDS_SECRET = "cp-region-creds-secret";

    private final CloudRegionDao cloudRegionDao;
    private final FileShareMountManager shareMountManager;
    private final CloudRegionMapper cloudRegionMapper;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final KubernetesManager kubernetesManager;
    private final Map<CloudProvider, ? extends CloudRegionHelper> helpers;

    public CloudRegionManager(final CloudRegionDao cloudRegionDao,
                              final CloudRegionMapper cloudRegionMapper,
                              final FileShareMountManager shareMountManager,
                              final MessageHelper messageHelper,
                              final PreferenceManager preferenceManager,
                              final AuthManager authManager,
                              KubernetesManager kubernetesManager, final List<CloudRegionHelper> helpers) {
        this.cloudRegionDao = cloudRegionDao;
        this.cloudRegionMapper = cloudRegionMapper;
        this.shareMountManager = shareMountManager;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.authManager = authManager;
        this.kubernetesManager = kubernetesManager;
        this.helpers = CommonUtils.groupByCloudProvider(helpers);
    }

    public List<? extends AbstractCloudRegion> loadAll() {
        return cloudRegionDao.loadAll();
    }

    public List<CloudRegionInfo> loadAllRegionsInfo() {
        return loadAll().stream()
            .map(CloudRegionInfo::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public AbstractCloudRegion create(final AbstractCloudRegionDTO regionDTO) {
        final AbstractCloudRegion region = cloudRegionMapper.toEntity(regionDTO);
        final AbstractCloudRegionCredentials credentials = cloudRegionMapper.toCredentialsEntity(regionDTO);
        validateRegion(region, credentials);
        region.setOwner(authManager.getAuthorizedUser());
        region.setCreatedDate(DateUtils.now());
        switchDefaultRegion(region, credentials);
        return cloudRegionDao.create(region, credentials);
    }

    @Transactional
    public AbstractCloudRegion update(final Long id, final AbstractCloudRegionDTO regionDTO) {
        final AbstractCloudRegion modifiedRegion = assembleModifiedRegion(id, regionDTO);
        final AbstractCloudRegionCredentials modifiedCredentials =
                assembleModifiedCredentials(modifiedRegion, regionDTO);
        validateRegion(modifiedRegion, modifiedCredentials);
        switchDefaultRegion(modifiedRegion, modifiedCredentials);
        cloudRegionDao.update(modifiedRegion, modifiedCredentials);
        return modifiedRegion;
    }

    private AbstractCloudRegion assembleModifiedRegion(final Long id, final AbstractCloudRegionDTO regionDTO) {
        final AbstractCloudRegion oldRegion = load(id);
        final AbstractCloudRegion updatedRegion = cloudRegionMapper.toEntity(regionDTO);
        return mergeRegions(oldRegion, updatedRegion);
    }

    private AbstractCloudRegionCredentials assembleModifiedCredentials(final AbstractCloudRegion region,
                                                                       final  AbstractCloudRegionDTO regionVO) {
        final AbstractCloudRegionCredentials oldCredentials = cloudRegionDao.loadCredentials(region.getId())
                .orElse(null);
        final AbstractCloudRegionCredentials updatedCredentials = cloudRegionMapper.toCredentialsEntity(regionVO);
        return mergeCredentials(oldCredentials, updatedCredentials);
    }

    private AbstractCloudRegion mergeRegions(final AbstractCloudRegion oldRegion,
                                             final AbstractCloudRegion updatedRegion) {
        validateProvider(oldRegion.getProvider(), updatedRegion.getProvider());
        return getHelper(oldRegion.getProvider()).mergeRegions(oldRegion, updatedRegion);
    }

    private AbstractCloudRegionCredentials mergeCredentials(final AbstractCloudRegionCredentials oldCredentials,
                                                            final AbstractCloudRegionCredentials updatedCredentials) {
        if (oldCredentials == null || updatedCredentials == null) {
            return updatedCredentials;
        }
        validateProvider(oldCredentials.getProvider(), updatedCredentials.getProvider());
        return getHelper(oldCredentials.getProvider()).mergeCredentials(oldCredentials, updatedCredentials);
    }

    private void validateProvider(final CloudProvider oldProvider, final CloudProvider newProvider) {
        Assert.isTrue(newProvider == null || oldProvider == newProvider,
                messageHelper.getMessage(MessageConstants.ERROR_REGION_PROVIDER_MISMATCH, newProvider,
                        oldProvider));
    }

    @Transactional
    public AbstractCloudRegion delete(final Long id) {
        final AbstractCloudRegion region = load(id);
        region.getFileShareMounts().stream()
                .map(FileShareMount::getId)
                .forEach(shareMountManager::delete);
        cloudRegionDao.delete(id);
        return region;
    }

    @Override
    public AbstractCloudRegion load(final Long id) {
        return cloudRegionDao.loadById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_REGION_NOT_FOUND, id)));
    }

    public AbstractCloudRegionCredentials loadCredentials(final Long id) {
        return loadCredentials(load(id));
    }

    public AzureRegionCredentials loadCredentials(final AzureRegion region) {
        return (AzureRegionCredentials) loadCredentials((AbstractCloudRegion) region);
    }

    public AbstractCloudRegionCredentials loadCredentials(final AbstractCloudRegion region) {
        return cloudRegionDao.loadCredentials(region.getId())
                .map(credentials -> {
                    validateProvider(credentials.getProvider(), region.getProvider());
                    return credentials;
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_REGION_CREDENTIALS_NOT_FOUND, region.getId())));
    }

    @Override
    public AbstractCloudRegion changeOwner(final Long id, final String owner) {
        final AbstractCloudRegion region = load(id);
        region.setOwner(owner);
        final  AbstractCloudRegionDTO regionVO = cloudRegionMapper.toCloudRegionDTO(region);
        return update(id, regionVO);
    }

    @Override
    public AclClass getSupportedClass() {
        return AclClass.CLOUD_REGION;
    }

    @Override
    public AbstractCloudRegion loadByNameOrId(final String identifier) {
        return loadRegionByNameOrId(identifier)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_REGION_NOT_FOUND, identifier)));
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(Integer page, Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AbstractSecuredEntity loadWithParents(Long id) {
        throw new UnsupportedOperationException();
    }

    public AbstractCloudRegion loadOrDefault(final Long id) {
        return Optional.ofNullable(id)
                .map(i -> cloudRegionDao.loadById(i)
                        .orElseThrow(() -> new IllegalArgumentException(
                                messageHelper.getMessage(MessageConstants.ERROR_REGION_NOT_FOUND, i))))
                .orElseGet(this::loadDefaultRegion);
    }

    public void refreshCloudRegionCredKubeSecret() {
        log.debug("Create Kube secret with cloud region creds if it does not exist.");
        if (!kubernetesManager.doesSecretExist(CP_REGION_CREDS_SECRET)) {
            log.warn("Secret: " + CP_REGION_CREDS_SECRET + " doesn't exist!");
            return;
        }
        kubernetesManager.refreshSecret(CP_REGION_CREDS_SECRET,
                loadAll()
                        .stream()
                        .collect(Collectors.toMap(region -> region.getId().toString(),
                                                  region -> helpers.get(region.getProvider())
                                                      .serializeCredentials(region, loadCredentials(region))
                                 )
                        )
        );
    }

    public List<CloudProvider> loadProviders() {
        return Arrays.stream(CloudProvider.values()).collect(Collectors.toList());
    }

    public List<String> loadAllAvailable(CloudProvider provider) {
        final CloudProvider requestProvider = Optional.ofNullable(provider)
                .orElse(getDefaultProvider());
        return getHelper(requestProvider).loadAvailableRegions();
    }

    private CloudProvider getDefaultProvider() {
        return CloudProvider.valueOf(preferenceManager.getPreference(SystemPreferences.CLOUD_DEFAULT_PROVIDER));
    }

    public AwsRegion getAwsRegion(final S3bucketDataStorage dataStorage) {
        return (AwsRegion) getCloudRegion(CloudProvider.AWS, dataStorage.getRegionId());
    }

    public AzureRegion getAzureRegion(final AzureBlobStorage dataStorage) {
        return (AzureRegion) getCloudRegion(CloudProvider.AZURE, dataStorage.getRegionId());
    }

    public GCPRegion getGCPRegion(final GSBucketStorage dataStorage) {
        return (GCPRegion) getCloudRegion(CloudProvider.GCP, dataStorage.getRegionId());
    }

    private AbstractCloudRegion getCloudRegion(final CloudProvider provider, final Long regionId) {
        final AbstractCloudRegion region = Optional.ofNullable(regionId)
                .map(this::load)
                .orElseGet(this::loadDefaultRegion);
        if (region.getProvider() != provider) {
            throw new IllegalArgumentException();
        }
        return region;
    }

    public AbstractCloudRegion loadDefaultRegion() {
        return cloudRegionDao.loadDefaultRegion()
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_REGION_DEFAULT_UNDEFINED)));
    }

    public AbstractCloudRegion load(final CloudProvider provider, final String regionCode) {
        return cloudRegionDao.loadByProviderAndRegionCode(provider, regionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_REGION_NOT_FOUND, regionCode)));
    }

    private void switchDefaultRegion(final AbstractCloudRegion region,
                                     final AbstractCloudRegionCredentials credentials) {
        if (region.isDefault()) {
            cloudRegionDao.loadDefaultRegion().ifPresent(defaultRegion -> {
                if (!defaultRegion.getId().equals(region.getId())) {
                    defaultRegion.setDefault(false);
                    cloudRegionDao.update(defaultRegion, credentials);
                }
            });
        }
    }

    private Optional<AbstractCloudRegion> loadRegionByNameOrId(final String identifier) {
        if (NumberUtils.isDigits(identifier)) {
            final Optional<AbstractCloudRegion> region = cloudRegionDao.loadById(Long.parseLong(identifier));
            return region.isPresent()
                    ? region
                    : cloudRegionDao.loadByRegionName(identifier);
        } else {
            return cloudRegionDao.loadByRegionName(identifier);
        }
    }

    private void validateRegion(final AbstractCloudRegion region, final AbstractCloudRegionCredentials credentials) {
        Assert.isTrue(StringUtils.isNotBlank(region.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_REGION_NAME_MISSING));
        Assert.notNull(region.getMountStorageRule(),
                messageHelper.getMessage(MessageConstants.ERROR_REGION_MOUNT_RULE_MISSING));
        Assert.notNull(region.getMountCredentialsRule(),
                messageHelper.getMessage(MessageConstants.ERROR_REGION_MOUNT_CREDENTIALS_RULE_MISSING));
        getHelper(region.getProvider()).validateRegion(region, credentials);
    }

    private CloudRegionHelper getHelper(CloudProvider type) {
        return helpers.get(type);
    }
}

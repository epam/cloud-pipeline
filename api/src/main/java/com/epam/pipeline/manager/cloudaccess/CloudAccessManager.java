/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloudaccess;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.cloudaccess.CloudAccessManagementConfig;
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessProfile;
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessRegionProfile;
import com.epam.pipeline.entity.cloudaccess.key.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.metadata.PipeConfValueType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CloudAccessManager {

    private final CloudAccessManagementFacade cloudAccessManagementFacade;
    private final UserManager userManager;
    private final CloudRegionManager regionManager;
    private final MetadataManager metadataManager;
    private final PreferenceManager preferenceManager;


    public CloudUserAccessProfile getCloudUserProfile(final String username) {
        final PipelineUser user = fetchPipelineUserByName(username);
        final Map<Long, CloudUserAccessRegionProfile> profiles = regionManager.loadAll()
                .stream()
                .filter(region ->
                        Optional.ofNullable(fetchCloudAccessConfig(region.getId()))
                                .map(CloudAccessManagementConfig::isEnabled).orElse(false))
                .map(region -> constructCloudUserAccessRegionProfile(user, region))
                .filter(Objects::nonNull)
                .filter(profile -> profile.getPolicy() != null || profile.getKeys() != null)
                .collect(Collectors.toMap(CloudUserAccessRegionProfile::getRegionId, p -> p));
        return CloudUserAccessProfile.builder().user(user).profiles(profiles).build();
    }

    public CloudUserAccessKeys getKeys(final Long regionId, final String username) {
        final CloudAccessManagementConfig cloudAccessConfig = validateAndGetCloudAccessConfig(regionId);
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        return fetchCloudAccessKeyIdAttachedForUser(cloudAccessConfig, user)
                .map(kId -> cloudAccessManagementFacade.getAccessKeys(cloudAccessConfig, region, user, kId))
                .orElse(null);
    }

    public CloudUserAccessKeys generateKeys(final Long regionId, final String username, final boolean force) {
        final CloudAccessManagementConfig cloudAccessConfig = validateAndGetCloudAccessConfig(regionId);
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);

        fetchCloudAccessKeyIdAttachedForUser(cloudAccessConfig, user).ifPresent(kId -> {
            if (cloudAccessManagementFacade.getAccessKeys(cloudAccessConfig, region, user, kId) != null) {
                if (force) {
                    // re-generate keys if specified
                    revokeKeys(regionId, username);
                } else {
                    throw new IllegalStateException(String.format("Cloud access keys for user %s already exist," +
                            " if you want to regenerate it please use 'force=true'!", username));
                }
            }
        });

        final CloudUserAccessKeys cloudKeys = cloudAccessManagementFacade
                .generateAccessKeys(cloudAccessConfig, region, user);
        final MetadataVO cloudKeyMetadata = new MetadataVO(
                new EntityVO(user.getId(), AclClass.PIPELINE_USER),
                Collections.singletonMap(
                        fetchCloudAccessKeyUserMetadataTag(cloudAccessConfig),
                        new PipeConfValue(PipeConfValueType.STRING.toString(), cloudKeys.getId())
                )
        );
        metadataManager.updateMetadataItemKey(cloudKeyMetadata);
        return cloudKeys;

    }

    public void revokeKeys(final Long regionId, final String username) {
        final CloudAccessManagementConfig cloudAccessConfig = validateAndGetCloudAccessConfig(regionId);
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        final String keysId = fetchCloudAccessKeyIdAttachedForUser(cloudAccessConfig, user)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("User %s has no cloud access keys attached", user.getUserName())));
        revokeCloudUserAccessPermissions(regionId, username);
        cloudAccessManagementFacade.revokeKeys(cloudAccessConfig, region, user, keysId);
        metadataManager.deleteMetadataItemKey(
                new EntityVO(user.getId(), AclClass.PIPELINE_USER),
                fetchCloudAccessKeyUserMetadataTag(cloudAccessConfig)
        );

        // Delete cloud user only if it hasn't any other keys
        if (cloudAccessManagementFacade.listKeys(cloudAccessConfig, region, user).isEmpty()) {
            cloudAccessManagementFacade.deleteUser(cloudAccessConfig, region, user);
        }
    }

    public CloudAccessPolicy updateCloudUserAccessPermissions(final Long regionId, final String username,
                                                              final CloudAccessPolicy accessPolicy) {
        final CloudAccessManagementConfig cloudAccessConfig = validateAndGetCloudAccessConfig(regionId);
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        return cloudAccessManagementFacade.updateCloudUserAccessPolicy(cloudAccessConfig, region, user, accessPolicy);
    }

    public void revokeCloudUserAccessPermissions(final Long regionId, final String username) {
        final CloudAccessManagementConfig cloudAccessConfig = validateAndGetCloudAccessConfig(regionId);
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        if (getCloudUserAccessPermissions(regionId, username) == null) {
            return;
        }
        cloudAccessManagementFacade.revokeCloudUserAccessPermissions(cloudAccessConfig, region, user);
    }

    public CloudAccessPolicy getCloudUserAccessPermissions(final Long regionId, final String username) {
        final CloudAccessManagementConfig cloudAccessConfig = validateAndGetCloudAccessConfig(regionId);
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        return cloudAccessManagementFacade.getCloudUserAccessPermissions(cloudAccessConfig, region, user);
    }

    private CloudUserAccessRegionProfile constructCloudUserAccessRegionProfile(final PipelineUser user,
                                                                               final AbstractCloudRegion region) {
        final CloudUserAccessKeys keys = getKeys(region.getId(), user.getUserName());
        final CloudAccessPolicy policy = getCloudUserAccessPermissions(region.getId(), user.getUserName());
        return CloudUserAccessRegionProfile.builder()
                .regionId(region.getId()).keys(keys)
                .policy(policy).build();
    }

    private Optional<String> fetchCloudAccessKeyIdAttachedForUser(final CloudAccessManagementConfig cloudAccessConfig,
                                                                  final PipelineUser user) {
        return ListUtils.emptyIfNull(
                        metadataManager.listMetadataItems(
                                Collections.singletonList(new EntityVO(user.getId(), AclClass.PIPELINE_USER)))
                ).stream()
                .map(metadataEntry -> metadataEntry.getData()
                        .get(fetchCloudAccessKeyUserMetadataTag(cloudAccessConfig)))
                .filter(Objects::nonNull)
                .findFirst()
                .map(PipeConfValue::getValue);
    }

    private CloudAccessManagementConfig validateAndGetCloudAccessConfig(final Long regionId) {
        final CloudAccessManagementConfig cloudAccessConfig = getCloudAccessConfig(regionId);
        Assert.isTrue(cloudAccessConfig.isEnabled(),
                String.format("Cloud access feature is not enabled for region %s", regionId));
        return cloudAccessConfig;
    }

    private CloudAccessManagementConfig getCloudAccessConfig(final Long regionId) {
        return Optional.ofNullable(fetchCloudAccessConfig(regionId))
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("No cloud access config found for region %s", regionId)));

    }

    private CloudAccessManagementConfig fetchCloudAccessConfig(final Long regionId) {
        return preferenceManager.getPreference(
                        SystemPreferences.CLOUD_ACCESS_MANAGEMENT_CONFIG).stream()
                .filter(config -> regionId.equals(config.getRegionId()))
                .findFirst()
                .orElse(null);

    }

    private PipelineUser fetchPipelineUserByName(final String username) {
        final PipelineUser user = userManager.loadUserByName(username);
        if (user == null) {
            throw new IllegalArgumentException(
                    String.format("User with name: %s cannot be found it the system!", username));
        }
        return user;
    }

    private String fetchCloudAccessKeyUserMetadataTag(final CloudAccessManagementConfig config) {
        final CloudAccessManagementConfig cloudAccessConfig = getCloudAccessConfig(config.getRegionId());
        return Optional.ofNullable(
                cloudAccessConfig.getCloudAccessKeyUserMetadataPrefix()
        ).map(p -> String.format("%s%d", p, config.getRegionId()))
         .orElseThrow(() -> new IllegalStateException(
                String.format("User metadata tag prefix ('cloudAccessKeyUserMetadataPrefix') for cloud access " +
                        "key is not defined for region %s", config.getRegionId()))
        );
    }
}

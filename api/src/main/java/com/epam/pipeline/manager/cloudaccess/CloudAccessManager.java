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
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessKeys;
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

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
// TODO refactor code to put all system preferences to CloudAccessManagementConfig.java
public class CloudAccessManager {

    private final CloudAccessManagementFacade cloudAccessManagementFacade;
    private final UserManager userManager;
    private final CloudRegionManager regionManager;
    private final MetadataManager metadataManager;
    private final PreferenceManager preferenceManager;

    public CloudUserAccessKeys getKeys(final Long regionId, final String username) {
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        return fetchCloudAccessKeyIdAttachedForUser(user)
                .map(kId -> cloudAccessManagementFacade.getAccessKeys(region, user, kId))
                .orElse(null);
    }

    public CloudUserAccessKeys generateKeys(final Long regionId, final String username, final boolean force) {
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);

        fetchCloudAccessKeyIdAttachedForUser(user).ifPresent(kId -> {
            if (cloudAccessManagementFacade.getAccessKeys(region, user, kId) != null) {
                if (force) {
                    // re-generate keys if specified
                    revokeKeys(regionId, username);
                } else {
                    throw new IllegalStateException(String.format("Cloud access keys for user %s already exist," +
                            " if you want to regenerate it please use 'force=true'!", username));
                }
            }
        });

        final CloudUserAccessKeys cloudKeys = cloudAccessManagementFacade.generateAccessKeys(region, user);
        final MetadataVO cloudKeyMetadata = new MetadataVO(
                new EntityVO(user.getId(), AclClass.PIPELINE_USER),
                Collections.singletonMap(
                        fetchCloudAccessKeyUserMetadataTag(),
                        new PipeConfValue(PipeConfValueType.STRING.toString(), cloudKeys.getId())
                )
        );
        metadataManager.updateMetadataItemKey(cloudKeyMetadata);
        return cloudKeys;

    }

    public void revokeKeys(final Long regionId, final String username) {
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        final String keysId = fetchCloudAccessKeyIdAttachedForUser(user)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("User %s has no cloud access keys attached", user.getUserName())));
        revokeCloudUserAccessPermissions(regionId, username);
        cloudAccessManagementFacade.revokeKeys(region, user, keysId);
        cloudAccessManagementFacade.deleteUser(region, user);
        metadataManager.deleteMetadataItemKey(
                new EntityVO(user.getId(), AclClass.PIPELINE_USER),
                fetchCloudAccessKeyUserMetadataTag()
        );
    }

    public CloudAccessPolicy updateCloudUserAccessPermissions(final Long regionId, final String username,
                                                              final CloudAccessPolicy accessPolicy) {
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        return cloudAccessManagementFacade.updateCloudUserAccessPolicy(region, user, accessPolicy);
    }

    public void revokeCloudUserAccessPermissions(final Long regionId, final String username) {
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        if (getCloudUserAccessPermissions(regionId, username) == null) {
            return;
        }
        cloudAccessManagementFacade.revokeCloudUserAccessPermissions(region, user);
    }

    public CloudAccessPolicy getCloudUserAccessPermissions(final Long regionId, final String username) {
        final PipelineUser user = fetchPipelineUserByName(username);
        final AbstractCloudRegion region = regionManager.load(regionId);
        return cloudAccessManagementFacade.getCloudUserAccessPermissions(region, user);
    }

    private Optional<String> fetchCloudAccessKeyIdAttachedForUser(final PipelineUser user) {
        return ListUtils.emptyIfNull(
                        metadataManager.listMetadataItems(
                                Collections.singletonList(new EntityVO(user.getId(), AclClass.PIPELINE_USER)))
                ).stream()
                .map(metadataEntry -> metadataEntry.getData()
                        .get(fetchCloudAccessKeyUserMetadataTag()))
                .filter(Objects::nonNull)
                .findFirst()
                .map(PipeConfValue::getValue);
    }

    private PipelineUser fetchPipelineUserByName(final String username) {
        final PipelineUser user = userManager.loadUserByName(username);
        if (user == null) {
            throw new IllegalArgumentException("User with name: %s cannot be found it the system!");
        }
        return user;
    }

    private String fetchCloudAccessKeyUserMetadataTag() {
        return Optional.ofNullable(
                preferenceManager.getPreference(SystemPreferences.CLOUD_ACCESS_KEY_USER_METADATA_TAG)
        ).orElseThrow(() -> new IllegalStateException(
                String.format("Preference %s is not defined",
                        SystemPreferences.CLOUD_ACCESS_KEY_USER_METADATA_TAG.getKey()))
        );
    }
}

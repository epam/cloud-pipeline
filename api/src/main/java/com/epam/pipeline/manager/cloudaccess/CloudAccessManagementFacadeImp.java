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

import com.epam.pipeline.entity.cloudaccess.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.cloud.CloudAwareService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudAccessManagementFacadeImp implements CloudAccessManagementFacade {

    private final PreferenceManager preferenceManager;

    private Map<CloudProvider, CloudAccessManagementService> cloudAccessServices;

    @Autowired
    public void setCloudAccessServices(final List<CloudAccessManagementService<?>> services) {
        cloudAccessServices = services.stream()
                .collect(Collectors.toMap(CloudAwareService::getProvider, s -> s));
    }

    @Override
    public <R extends AbstractCloudRegion> CloudUserAccessKeys generateKeys(final R region,
                                                                            final PipelineUser user,
                                                                            final CloudAccessPolicy accessPolicy) {
        final CloudAccessManagementService<R> accessManagementService = getCloudAccessManagementService(region);

        if (!accessManagementService.doesCloudUserExist(region, user.getUserName())) {
            accessManagementService.createCloudUser(region, user.getUserName());
        }

        updateCloudUserAccessPolicy(region, user, accessPolicy);
        return accessManagementService.generateCloudKeysForUser(region, user.getUserName());
    }

    @Override
    public <R extends AbstractCloudRegion> CloudAccessPolicy updateCloudUserAccessPolicy(
            final R region, final PipelineUser user, final CloudAccessPolicy accessPolicy) {
        final CloudAccessManagementService<R> accessManagementService = getCloudAccessManagementService(region);

        final String accessPolicyPrefix = preferenceManager.getPreference(
                SystemPreferences.CLOUD_ACCESS_MANAGEMENT_POLICY_PREFIX);
        accessManagementService.grantCloudUserPermissions(region,user.getUserName(),
                String.format("%s-%s", accessPolicyPrefix, user.getUserName()), accessPolicy);
        return accessPolicy;
    }

    @Override
    public <R extends AbstractCloudRegion> void revokeCloudUserAccessPolicy(final R region, final PipelineUser user) {
        final CloudAccessManagementService<R> accessManagementService =
                getCloudAccessManagementService(region);
        final String accessPolicyPrefix = preferenceManager.getPreference(
                SystemPreferences.CLOUD_ACCESS_MANAGEMENT_POLICY_PREFIX);
        accessManagementService.revokeCloudUserPermissions(region, user.getUserName(),
                String.format("%s-%s", accessPolicyPrefix, user.getUserName()));
    }

    @Override
    public <R extends AbstractCloudRegion> void revokeKeys(final R region, final PipelineUser user,
                                                           final String keysId) {
        final CloudAccessManagementService<R> accessManagementService =
                getCloudAccessManagementService(region);
        accessManagementService.revokeCloudKeysForUser(region, user.getUserName(), keysId);
    }

    @Override
    public <R extends AbstractCloudRegion> CloudAccessPolicy getCloudUserAccessPolicy(final R region,
                                                                                      final PipelineUser user) {
        final CloudAccessManagementService<R> accessManagementService =
                getCloudAccessManagementService(region);
        final String accessPolicyPrefix = preferenceManager.getPreference(
                SystemPreferences.CLOUD_ACCESS_MANAGEMENT_POLICY_PREFIX);
        return accessManagementService.getCloudUserPermissions(region, user.getUserName(),
                String.format("%s-%s", accessPolicyPrefix, user.getUserName()));
    }

    @SuppressWarnings("unchecked")
    private <R extends AbstractCloudRegion> CloudAccessManagementService<R> getCloudAccessManagementService(
            final R region) {
        return Optional.ofNullable(cloudAccessServices.get(region.getProvider()))
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Cloud Provider: %s is not supported.", region.getProvider())));
    }
}

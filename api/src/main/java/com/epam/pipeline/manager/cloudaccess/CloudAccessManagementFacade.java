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

import com.epam.pipeline.entity.cloudaccess.CloudAccessManagementConfig;
import com.epam.pipeline.entity.cloudaccess.key.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicyStatement;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.cloud.CloudAwareService;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudAccessManagementFacade {

    private static final String CP_CLOUD_ACCESS_POLICY_DEFAULT = "cp-cloud-access-policy-";

    private Map<CloudProvider, CloudAccessManagementService> cloudAccessServices;
    private final DataStorageManager storageManager;

    @Autowired
    public void setCloudAccessServices(final List<CloudAccessManagementService<?>> services) {
        cloudAccessServices = services.stream()
                .collect(Collectors.toMap(CloudAwareService::getProvider, s -> s));
    }

    public <R extends AbstractCloudRegion> CloudUserAccessKeys generateAccessKeys(
            final CloudAccessManagementConfig config, final R region, final PipelineUser user) {
        final CloudAccessManagementService<R> accessManagementService = getCloudAccessManagementService(region);

        if (!accessManagementService.doesCloudUserExist(region, getCloudUsername(config, user))) {
            accessManagementService.createCloudUser(region, getCloudUsername(config, user));
        }

        return accessManagementService.generateCloudKeysForUser(region, getCloudUsername(config, user));
    }

    public <R extends AbstractCloudRegion> CloudUserAccessKeys getAccessKeys(final CloudAccessManagementConfig config,
                                                                             final R region,
                                                                             final PipelineUser user,
                                                                             final String keyId) {
        final CloudAccessManagementService<R> accessManagementService = getCloudAccessManagementService(region);

        if (!accessManagementService.doesCloudUserExist(region, getCloudUsername(config, user))) {
            throw new IllegalArgumentException(
                    String.format("There is no cloud user with name: %s!", getCloudUsername(config, user)));
        }
        return accessManagementService.getAccessKeysForUser(region, getCloudUsername(config, user), keyId);
    }

    public <R extends AbstractCloudRegion> List<CloudUserAccessKeys> listKeys(final CloudAccessManagementConfig config,
                                                                              final R region,
                                                                              final PipelineUser user) {
        final CloudAccessManagementService<R> accessManagementService = getCloudAccessManagementService(region);

        if (!accessManagementService.doesCloudUserExist(region, getCloudUsername(config, user))) {
            throw new IllegalArgumentException(
                    String.format("There is no cloud user with name: %s!", getCloudUsername(config, user)));
        }
        return accessManagementService.listAccessKeysForUser(region, getCloudUsername(config, user));
    }

    public <R extends AbstractCloudRegion> void revokeKeys(final CloudAccessManagementConfig config,
                                                           final R region,
                                                           final PipelineUser user,
                                                           final String keysId) {
        final CloudAccessManagementService<R> accessManagementService =
                getCloudAccessManagementService(region);

        validateCloudUser(accessManagementService, config, region, user);
        accessManagementService.revokeCloudKeysForUser(region, getCloudUsername(config, user), keysId);
    }

    public <R extends AbstractCloudRegion> void deleteUser(final CloudAccessManagementConfig config,
                                                           final R region, final PipelineUser user) {
        getCloudAccessManagementService(region).deleteCloudUser(region, getCloudUsername(config, user));
    }

    public <R extends AbstractCloudRegion> CloudAccessPolicy getCloudUserAccessPermissions(
            final CloudAccessManagementConfig config, final R region, final PipelineUser user) {
        final CloudAccessManagementService<R> accessManagementService =
                getCloudAccessManagementService(region);

        final String policyName = constructCloudUserPolicyName(config, user);
        return checkThatStorageExistAndUpdateStatusForStatements(
                accessManagementService.getCloudUserPermissions(region, getCloudUsername(config, user), policyName)
        );
    }

    public <R extends AbstractCloudRegion> CloudAccessPolicy updateCloudUserAccessPolicy(
            final CloudAccessManagementConfig config, final R region, final PipelineUser user,
            final CloudAccessPolicy accessPolicy) {
        final CloudAccessManagementService<R> accessManagementService = getCloudAccessManagementService(region);

        accessPolicy.getStatements().forEach(statement -> validateStorage(region, statement));

        if (!accessManagementService.doesCloudUserExist(region, getCloudUsername(config, user))) {
            accessManagementService.createCloudUser(region, getCloudUsername(config, user));
        }

        final String policyName = constructCloudUserPolicyName(config, user);
        accessManagementService.grantCloudUserPermissions(region, getCloudUsername(config, user),
                policyName, updateAccessPolicyWithResources(accessPolicy));
        return accessPolicy;
    }

    public <R extends AbstractCloudRegion> void revokeCloudUserAccessPermissions(
            final CloudAccessManagementConfig config, final R region, final PipelineUser user) {
        final CloudAccessManagementService<R> accessManagementService =
                getCloudAccessManagementService(region);

        validateCloudUser(accessManagementService, config, region, user);

        final String policyName = constructCloudUserPolicyName(config, user);
        accessManagementService.revokeCloudUserPermissions(region, getCloudUsername(config, user), policyName);
    }

    private <R extends AbstractCloudRegion> void validateStorage(R region, CloudAccessPolicyStatement statement) {
        Assert.notNull(statement.getResourceId(), "ResourceId cannot be null, please specify a resourceId");
        final AbstractDataStorage storage = storageManager.load(statement.getResourceId());
        final DataStorageType requiredStorageType;
        final Long storageRegionId;
        switch (region.getProvider()) {
            case AWS:
                requiredStorageType = DataStorageType.S3;
                storageRegionId = ((S3bucketDataStorage) storage).getRegionId();
                break;
            case GCP:
                requiredStorageType = DataStorageType.GS;
                storageRegionId = ((GSBucketStorage) storage).getRegionId();
                break;
            case AZURE:
                requiredStorageType = DataStorageType.AZ;
                storageRegionId = ((AzureBlobStorage) storage).getRegionId();
                break;
            default:
                throw new IllegalArgumentException("Wrong Cloud Provider: " + region.getProvider());
        }
        Assert.isTrue(requiredStorageType.equals(storage.getType()),
                String.format("Storage %s has wrong type %s, should be %s",
                        storage.getPath(), storage.getType(), requiredStorageType));
        Assert.isTrue(requiredStorageType.equals(storage.getType()),
                String.format("Storage %s from regionId %s, but you try to provide permission in region %s",
                        storage.getPath(), storageRegionId, region.getId()));
    }

    private CloudAccessPolicy checkThatStorageExistAndUpdateStatusForStatements(final CloudAccessPolicy policy) {
        if (policy == null) {
            return null;
        }
        return policy.toBuilder().statements(
                policy.getStatements().stream()
                        .map(statement -> {
                            try {
                                final Long storageId = storageManager.loadByPathOrId(statement.getResource()).getId();
                                return statement.toBuilder().resourceId(storageId).active(true).build();
                            } catch (IllegalArgumentException e) {
                                return statement.toBuilder().active(false).build();
                            }
                        }).collect(Collectors.toList())
        ).build();
    }

    private CloudAccessPolicy updateAccessPolicyWithResources(final CloudAccessPolicy policy) {
        return policy.toBuilder().statements(
                policy.getStatements().stream()
                        .map(statement -> statement.toBuilder()
                                .resource(storageManager.load(statement.getResourceId()).getPath()
                        ).active(true).build()).collect(Collectors.toList())
        ).build();
    }

    private String constructCloudUserPolicyName(final CloudAccessManagementConfig config, final PipelineUser user) {
        final String accessPolicyPrefix = Optional.ofNullable(config.getCloudAccessPolicyPrefix())
                .orElse(CP_CLOUD_ACCESS_POLICY_DEFAULT);
        return String.format("%s%s", accessPolicyPrefix, getCloudUsername(config, user));
    }

    private <R extends AbstractCloudRegion> void validateCloudUser(
            final CloudAccessManagementService<R> accessManagementService,
            final CloudAccessManagementConfig config,
            final R region,
            final PipelineUser user) {
        final String cloudUsername = getCloudUsername(config, user);
        if (!accessManagementService.doesCloudUserExist(region, cloudUsername)) {
            throw new IllegalArgumentException(
                    String.format("There is no cloud user with name: %s!", cloudUsername));
        }
    }

    private String getCloudUsername(final CloudAccessManagementConfig config, final PipelineUser user) {
        return StringUtils.isEmpty(config.getCloudUserNamePrefix())
                ? user.getUserName()
                : String.format("%s%s", config.getCloudUserNamePrefix(), user.getUserName());
    }

    @SuppressWarnings("unchecked")
    private <R extends AbstractCloudRegion> CloudAccessManagementService<R> getCloudAccessManagementService(
            final R region) {
        return Optional.ofNullable(cloudAccessServices.get(region.getProvider()))
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Cloud Provider: %s is not supported.", region.getProvider())));
    }
}

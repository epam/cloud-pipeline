/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.autoscale;

import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.cluster.NetworkConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class IAMProfileVerifier {
    private static final String IAM_INSTANCE_PROFILE = "IamInstanceProfile";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final PreferenceManager preferenceManager;
    private final CloudRegionManager regionManager;
    private final AuthManager authManager;
    private final UserManager userManager;

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public boolean isImageRestricted(final PipelineRun run) {
        try {
            final Long runRegionId = run.getInstance().getCloudRegionId();
            if (Objects.isNull(runRegionId)) {
                log.warn("Cannot determine region for run '{}'", run.getId());
                return false;
            }
            final String runRegion = regionManager.loadOrDefault(runRegionId).getRegionCode();
            final List<AMIConfiguration> amiConfigurations = fetchIamInstanceProfilesConfigs(runRegion);
            if (CollectionUtils.isEmpty(amiConfigurations)) {
                log.debug("No IAM instance profiles found for region '{}'", runRegion);
                return false;
            }
            final String instanceType = run.getInstance().getNodeType();
            if (StringUtils.isBlank(instanceType)) {
                log.warn("Cannot determine instance type for run '{}'", run.getId());
                return false;
            }
            final String targetImage = run.getDockerImage();
            if (StringUtils.isBlank(targetImage)) {
                log.warn("Cannot determine docker image for run '{}'", run.getId());
                return false;
            }
            log.debug("Found '{}' IAM instance profiles found for region '{}'", amiConfigurations.size(), runRegion);
            final PipelineUser permissionUser = resolveUserForPermissions(run);
            if (permissionUser == null) {
                log.warn("Cannot resolve user for IAM profile permission check for run '{}'", run.getId());
                return false;
            }
            final Set<String> roles = Stream.concat(
                            ListUtils.emptyIfNull(permissionUser.getRoles()).stream().map(Role::getName),
                            ListUtils.emptyIfNull(permissionUser.getGroups()).stream())
                    .collect(Collectors.toSet());
            return amiConfigurations.stream()
                    .filter(c -> checkPermissions(c.getPermissions(), roles))
                    .filter(c -> checkDockerImages(c.getDockerImages(), targetImage))
                    .anyMatch(c -> checkInstanceMask(c.getInstanceMask(), instanceType));
        } catch (Exception e) {
            log.error("Failed to check AMI: ", e);
            return false;
        }
    }

    /**
     * Permissions in {@code CLUSTER_NETWORKS_CONFIG} apply to the pipeline run owner (effective user),
     * not the caller's principal — e.g. autoscaling runs under a scheduler admin context.
     */
    private PipelineUser resolveUserForPermissions(final PipelineRun run) {
        if (StringUtils.isNotBlank(run.getOwner())) {
            final PipelineUser ownerUser = userManager.loadUserByName(run.getOwner());
            if (ownerUser != null) {
                return ownerUser;
            }
            log.warn("Run owner '{}' for run '{}' is not a registered user; falling back to current user",
                    run.getOwner(), run.getId());
        }
        return authManager.getCurrentUser();
    }

    private boolean checkPermissions(final List<String> permissions, final Set<String> roles) {
        return CollectionUtils.isEmpty(permissions) || permissions.stream().anyMatch(roles::contains);
    }

    private boolean checkDockerImages(final List<String> dockerImages, final String targetImage) {
        return CollectionUtils.isEmpty(dockerImages) || ListUtils.emptyIfNull(dockerImages).stream()
                .anyMatch(image -> Objects.equals(image, targetImage));
    }

    private boolean checkInstanceMask(final String instanceMask, final String instanceType) {
        return StringUtils.isBlank(instanceMask) || MATCHER.match(instanceMask, instanceType);
    }

    private List<AMIConfiguration> fetchIamInstanceProfilesConfigs(final String region) {
        final CloudRegionsConfiguration networkConfiguration = preferenceManager.getObjectPreferenceAs(
                SystemPreferences.CLUSTER_NETWORKS_CONFIG, new TypeReference<CloudRegionsConfiguration>() {});
        if (Objects.isNull(networkConfiguration)) {
            return Collections.emptyList();
        }
        final List<NetworkConfiguration> configRegions = networkConfiguration.getRegions();
        if (CollectionUtils.isEmpty(configRegions)) {
            return Collections.emptyList();
        }
        return configRegions.stream()
                .filter(config -> Objects.equals(region, config.getName()))
                .flatMap(config -> ListUtils.emptyIfNull(config.getAmis()).stream())
                .filter(amiConfig -> MapUtils.isNotEmpty(amiConfig.getAdditionalSpec()))
                .filter(amiConfig -> amiConfig.getAdditionalSpec().containsKey(IAM_INSTANCE_PROFILE))
                .collect(Collectors.toList());
    }
}

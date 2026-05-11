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
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IAMProfileVerifierTest {
    private static final String IAM_INSTANCE_PROFILE = "IamInstanceProfile";
    private static final String RESTRICTED_ROLE = "ROLE_SECRET";
    private static final String RESTRICTED_INSTANCE_TYPE = "m5.xlarge";
    private static final String RESTRICTED_INSTANCE_MASK = "m5*";
    private static final String RESTRICTED_REGION = "test";
    private static final String RESTRICTED_DOCKER_IMAGE = "ubuntu:latest";
    private static final Long RESTRICTED_REGION_ID = 1L;
    private static final String ALLOWED_ROLE = "ROLE_TEST";
    private static final String ALLOWED_INSTANCE_TYPE = "r5.xlarge";
    private static final String ALLOWED_DOCKER_IMAGE = "centos:latest";
    private static final String RUN_OWNER = "run-owner";

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final CloudRegionManager regionManager = mock(CloudRegionManager.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final UserManager userManager = mock(UserManager.class);

    private final IAMProfileVerifier iamProfileVerifier = new IAMProfileVerifier(
            preferenceManager,
            regionManager,
            authManager,
            userManager);

    @Test
    public void shouldRestrict() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);

        final PipelineRun run = getRestrictedRun();

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isTrue();
    }

    @Test
    public void shouldRestrictUsingRunOwnerEvenIfCurrentUserIsDifferent() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);

        final PipelineRun run = getRestrictedRun();
        run.setOwner(RUN_OWNER);

        final PipelineUser runOwner = new PipelineUser();
        runOwner.setUserName(RUN_OWNER);
        runOwner.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(runOwner).when(userManager).loadUserByName(RUN_OWNER);

        final PipelineUser schedulerUser = new PipelineUser();
        schedulerUser.setGroups(Collections.singletonList(ALLOWED_ROLE));
        doReturn(schedulerUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isTrue();
    }

    @Test
    public void shouldAllowIfUserNotInRestrictedGroup() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(ALLOWED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldAllowIfUserHasNoGroupsOrRoles() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();

        final PipelineUser pipelineUser = new PipelineUser();
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldAllowIfUserHasNotRestrictedRoles() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setRoles(Collections.singletonList(new Role(ALLOWED_ROLE)));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldAllowWithAllowedInstanceType() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();
        run.getInstance().setNodeType(ALLOWED_INSTANCE_TYPE);

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldAllowIfNoInstanceType() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();
        run.getInstance().setNodeType(null);

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldAllowWithAllowedDockerImage() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();
        run.setDockerImage(ALLOWED_DOCKER_IMAGE);

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldAllowIfNoDockerImage() {
        doReturn(getNetworksConfig()).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();
        run.setDockerImage(null);

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldAllowIfNoNetworkConfig() {
        doReturn(null).when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldForbidWithNoInstanceMaskInNetworkConfig() {
        doReturn(getNetworksConfig(null,
                Collections.singletonList(RESTRICTED_DOCKER_IMAGE),
                Collections.singletonList(RESTRICTED_ROLE)))
                .when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isTrue();
    }

    @Test
    public void shouldForbidWithNoDockerImageInNetworkConfig() {
        doReturn(getNetworksConfig(RESTRICTED_INSTANCE_MASK, null,
                Collections.singletonList(RESTRICTED_ROLE)))
                .when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isTrue();
    }

    @Test
    public void shouldForbidWithNoPermissionsInNetworkConfig() {
        doReturn(getNetworksConfig(RESTRICTED_INSTANCE_MASK,
                Collections.singletonList(RESTRICTED_DOCKER_IMAGE), null))
                .when(preferenceManager).getObjectPreferenceAs(any(), any());
        doReturn(getRestrictedRegion()).when(regionManager).loadOrDefault(RESTRICTED_REGION_ID);
        final PipelineRun run = getRestrictedRun();

        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setGroups(Collections.singletonList(RESTRICTED_ROLE));
        doReturn(pipelineUser).when(authManager).getCurrentUser();

        final boolean result = iamProfileVerifier.isImageRestricted(run);
        assertThat(result).isTrue();
    }

    private static CloudRegionsConfiguration getNetworksConfig(final String instanceMask,
                                                               final List<String> dockerImages,
                                                               final List<String> permissions) {
        final AMIConfiguration amiConfig = new AMIConfiguration();
        amiConfig.setAdditionalSpec(Collections.singletonMap(IAM_INSTANCE_PROFILE, null));
        amiConfig.setPermissions(permissions);
        amiConfig.setDockerImages(dockerImages);
        amiConfig.setInstanceMask(instanceMask);

        final NetworkConfiguration regionConfig = new NetworkConfiguration();
        regionConfig.setName(RESTRICTED_REGION);
        regionConfig.setAmis(Collections.singletonList(amiConfig));
        final CloudRegionsConfiguration config = new CloudRegionsConfiguration();
        config.setRegions(Collections.singletonList(regionConfig));

        return config;
    }

    private static CloudRegionsConfiguration getNetworksConfig() {
        return getNetworksConfig(RESTRICTED_INSTANCE_MASK,
                Collections.singletonList(RESTRICTED_DOCKER_IMAGE),
                Collections.singletonList(RESTRICTED_ROLE));
    }

    private static PipelineRun getRestrictedRun() {
        final RunInstance instance = new RunInstance();
        instance.setNodeType(RESTRICTED_INSTANCE_TYPE);
        instance.setCloudRegionId(RESTRICTED_REGION_ID);

        final PipelineRun run = new PipelineRun();
        run.setInstance(instance);
        run.setDockerImage(RESTRICTED_DOCKER_IMAGE);

        return run;
    }

    private static AwsRegion getRestrictedRegion() {
        final AwsRegion awsRegion = new AwsRegion();
        awsRegion.setId(RESTRICTED_REGION_ID);
        awsRegion.setRegionCode(RESTRICTED_REGION);
        return awsRegion;
    }
}

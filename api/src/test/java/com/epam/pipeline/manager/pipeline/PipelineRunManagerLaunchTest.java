/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodePoolLaunchConfig;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.container.RunContainerSpec;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.notification.ContextualNotificationRegistrationManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_FALLBACK_INSTANCE_TYPES_MAX_COUNT;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_SPOT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getPipelineConfiguration;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.region.RegionCreatorUtils.getDefaultAwsRegion;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.quality.Strictness.LENIENT;

/**
 * Covers the {@link PipelineRunManager#CP_CAP_POOL_ID} checks of a launch.
 */
@SuppressWarnings("PMD.UnusedPrivateField")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
public class PipelineRunManagerLaunchTest {
    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final long REGION_ID = 1L;
    private static final long NON_DEFAULT_REGION_ID = 3L;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String OTHER_INSTANCE_TYPE = "other.type";
    private static final String INSTANCE_DISK = "1";
    private static final String DEFAULT_COMMAND = "sleep";
    private static final String IMAGE = "testImage";
    private static final String POOL_NAME = "pool";

    @InjectMocks
    private final PipelineRunManager pipelineRunManager = new PipelineRunManager();

    @Mock
    private ToolManager toolManager;

    @Mock
    private AuthManager authManager;

    @Mock
    private DataStorageManager dataStorageManager;

    @Mock
    private PipelineConfigurationManager pipelineConfigurationManager;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private InstanceOfferManager instanceOfferManager;

    @Mock
    private PipelineLauncher pipelineLauncher;

    @Mock
    private CloudRegionManager cloudRegionManager;

    @Mock
    private CheckPermissionHelper permissionHelper;

    @Mock
    private PipelineRunDao pipelineRunDao;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private ContextualNotificationRegistrationManager contextualNotificationRegistrationManager;

    @Mock
    private NodePoolManager nodePoolManager;

    private final Tool tool = getTool(IMAGE, DEFAULT_COMMAND);
    private final AwsRegion defaultAwsRegion = getDefaultAwsRegion(ID);
    private final PipelineConfiguration configuration = getPipelineConfiguration(
            IMAGE, INSTANCE_DISK, true, defaultAwsRegion.getId());
    private final InstancePrice price = new InstancePrice(configuration.getInstanceType(),
            Integer.parseInt(configuration.getInstanceDisk()), PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR,
            DISK_PRICE_PER_HOUR);

    @BeforeEach
    public void setUp() {
        mock(CLUSTER_DOCKER_EXTRA_MULTI);
        mock(CLUSTER_FALLBACK_INSTANCE_TYPES_MAX_COUNT);
        mock(CLUSTER_INSTANCE_HDD_EXTRA_MULTI);
        mock(CLUSTER_SPOT);

        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());
        doReturn(new PipelineConfiguration()).when(pipelineConfigurationManager)
                .getConfigurationForTool(any(), any());

        doReturn(tool).when(toolManager).loadByNameOrId(eq(IMAGE));
        doReturn(tool).when(toolManager).resolveSymlinks(eq(IMAGE));

        doReturn(defaultAwsRegion).when(cloudRegionManager).load(eq(defaultAwsRegion.getId()));
        doReturn(defaultAwsRegion).when(cloudRegionManager).loadDefaultRegion();

        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), any(), any(), anyBoolean());
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowed(anyString(), any(), any(), anyBoolean());
        doReturn(true).when(instanceOfferManager).isPriceTypeAllowed(anyString(), any(), anyBoolean());
        doReturn(Optional.empty()).when(instanceOfferManager).findOffer(anyString(), anyLong());
        doReturn(price).when(instanceOfferManager).getInstanceEstimatedPrice(nullable(String.class), anyInt(),
                nullable(Boolean.class), nullable(Long.class));
        doReturn(true).when(permissionHelper).isAllowed(any(), any());
        doReturn(ID).when(pipelineRunDao).createRunId();
        doReturn(DEFAULT_COMMAND).when(pipelineLauncher).launch(any(), any(), any(), any());
    }

    @Test
    public void launchPipelineRejectsAnUnknownPool() {
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.empty()).when(nodePoolManager).find(ID_2);

        assertThrows(IllegalArgumentException.class, () -> launchPipeline(configuration));
    }

    @Test
    public void launchPipelineRejectsAPoolWhoseInstanceTypeDoesNotMatchTheRun() {
        configuration.setInstanceType(INSTANCE_TYPE);
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.of(poolWithInstanceType(OTHER_INSTANCE_TYPE))).when(nodePoolManager).find(ID_2);

        assertThrows(IllegalArgumentException.class, () -> launchPipeline(configuration));
    }

    @Test
    public void launchPipelineSucceedsWhenThePoolsInstanceTypeMatchesTheRun() {
        configuration.setInstanceType(INSTANCE_TYPE);
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.of(poolWithInstanceType(INSTANCE_TYPE))).when(nodePoolManager).find(ID_2);

        assertNotNull(launchPipeline(configuration));
    }

    @Test
    public void launchPipelineDoesNotCheckInstanceTypeWhenTheRunDoesNotRequestOne() {
        // configuration.getInstanceType() is null in this fixture, so there is nothing to compare.
        // A pool with any instance type must be accepted then.
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.of(poolWithInstanceType(OTHER_INSTANCE_TYPE))).when(nodePoolManager).find(ID_2);

        assertNotNull(launchPipeline(configuration));
    }

    @Test
    public void launchPipelineRejectsAPoolWhoseRegionDoesNotMatchTheRun() {
        // the shared `configuration` fixture already requests defaultAwsRegion (ID)
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.of(poolWithRegion(NON_DEFAULT_REGION_ID))).when(nodePoolManager).find(ID_2);

        assertThrows(IllegalArgumentException.class, () -> launchPipeline(configuration));
    }

    @Test
    public void launchPipelineSucceedsWhenThePoolsRegionMatchesTheRun() {
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.of(poolWithRegion(REGION_ID))).when(nodePoolManager).find(ID_2);

        assertNotNull(launchPipeline(configuration));
    }

    @Test
    public void launchPipelineRejectsAMalformedPoolId() {
        setPoolIdParameter(configuration, "not-a-number");

        assertThrows(IllegalArgumentException.class, () -> launchPipeline(configuration));
        verify(nodePoolManager, never()).find(any());
    }

    @Test
    public void launchPipelineDoesNotValidateAnyPoolWhenTheParameterIsAbsent() {
        launchPipeline(configuration);

        verify(nodePoolManager, never()).find(any());
    }

    @Test
    public void launchPipelineUsesThePoolsOwnPodAssignPolicyWhenOneExists() {
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.of(poolWithAssignPolicy(poolIdSelectorPolicy(ID_2)))).when(nodePoolManager).find(ID_2);

        assertNotNull(launchPipeline(configuration));

        assertTrue(configuration.getPodAssignPolicy()
                .isMatch(KubernetesConstants.NODE_POOL_ID_LABEL, String.valueOf(ID_2)));
    }

    @Test
    public void launchPipelineOverridesAClientSuppliedPodAssignPolicyWithThePoolsOwn() {
        configuration.setPodAssignPolicy(RunContainerSpec.builder()
                .selector(RunContainerSpec.PodAssignSelector.builder()
                        .label("custom-label").value("custom-value").build())
                .build());
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.of(poolWithAssignPolicy(poolIdSelectorPolicy(ID_2)))).when(nodePoolManager).find(ID_2);

        assertNotNull(launchPipeline(configuration));

        assertTrue(configuration.getPodAssignPolicy()
                .isMatch(KubernetesConstants.NODE_POOL_ID_LABEL, String.valueOf(ID_2)));
    }

    @Test
    public void launchPipelineKeepsTheDefaultPodAssignPolicyWhenThePoolHasNoLaunchConfig() {
        setPoolIdParameter(configuration, String.valueOf(ID_2));
        doReturn(Optional.of(poolWithInstanceType(null))).when(nodePoolManager).find(ID_2);

        assertNotNull(launchPipeline(configuration));

        assertTrue(configuration.getPodAssignPolicy().isMatch(KubernetesConstants.RUN_ID_LABEL));
    }

    private void setPoolIdParameter(final PipelineConfiguration configuration, final String value) {
        final Map<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(PipelineRunManager.CP_CAP_POOL_ID, new PipeConfValueVO(value));
        configuration.setParameters(parameters);
    }

    private NodePool poolWithInstanceType(final String instanceType) {
        final NodePool pool = pool();
        pool.setInstanceType(instanceType);
        return pool;
    }

    private NodePool poolWithRegion(final Long regionId) {
        final NodePool pool = pool();
        pool.setRegionId(regionId);
        return pool;
    }

    private NodePool poolWithAssignPolicy(final RunContainerSpec policy) {
        final NodePool pool = pool();
        final NodePoolLaunchConfig launchConfig = new NodePoolLaunchConfig();
        launchConfig.setKubeAssignPolicy(policy);
        pool.setLaunchConfig(launchConfig);
        return pool;
    }

    private NodePool pool() {
        final NodePool pool = new NodePool();
        pool.setId(ID_2);
        pool.setName(POOL_NAME);
        return pool;
    }

    private RunContainerSpec poolIdSelectorPolicy(final Long poolId) {
        return RunContainerSpec.builder()
                .selector(RunContainerSpec.PodAssignSelector.builder()
                        .label(KubernetesConstants.NODE_POOL_ID_LABEL)
                        .value(String.valueOf(poolId))
                        .build())
                .build();
    }

    private <T> void mock(final AbstractSystemPreference<T> preference) {
        doReturn(preference.getDefaultValue()).when(preferenceManager).getPreference(eq(preference));
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration) {
        return pipelineRunManager.launchPipeline(configuration, new Pipeline(), null, INSTANCE_TYPE,
                null, null, null, null, null, null, null);
    }
}

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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunInstanceConfigVO;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.exception.ToolExecutionDeniedException;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.credits.PlatformUsageCreditsLaunchService;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
@Transactional
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.UnusedPrivateField", "PMD.AvoidDuplicateLiterals"})
public class PipelineRunManagerTest extends AbstractManagerTest {
    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final long REGION_ID = 1L;
    private static final long NOT_ALLOWED_REGION_ID = 2L;
    private static final long NON_DEFAULT_REGION_ID = 3L;
    private static final String TEST_IMAGE = "testImage";
    private static final String INSTANCE_DISK = "1";

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private RunStatusManager runStatusManager;

    @MockBean
    private ToolManager toolManager;

    @SpyBean
    private PipelineConfigurationManager pipelineConfigurationManager;

    @MockBean
    private InstanceOfferManager instanceOfferManager;

    @MockBean
    private PipelineLauncher pipelineLauncher;

    @MockBean
    private FolderManager folderManager;

    @MockBean
    private NotificationManager notificationManager;

    @MockBean
    private CloudRegionManager cloudRegionManager;

    @MockBean
    private EntityManager entityManager;

    @MockBean
    private ResourceMonitoringManager resourceMonitoringManager; // mock out this bean, because it depends on
    // instanceOfferManager during initialization
    @MockBean
    private ToolVersionManager toolVersionManager;

    @MockBean
    private CheckPermissionHelper permissionHelper;

    @MockBean
    private PlatformUsageCreditsLaunchService platformUsageCreditsLaunchService;

    @MockBean
    private MonitoringNotificationDao monitoringNotificationDao;

    @MockBean
    private ToolScanInfoManager toolScanInfoManager;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PreferenceManager preferenceManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Tool notScannedTool = new Tool();
        notScannedTool.setId(1L);
        notScannedTool.setImage(TEST_IMAGE);
        notScannedTool.setDefaultCommand("sleep");

        AwsRegion defaultAwsRegion = defaultRegion(REGION_ID);
        when(cloudRegionManager.load(eq(REGION_ID))).thenReturn(defaultAwsRegion);
        when(cloudRegionManager.loadDefaultRegion()).thenReturn(defaultAwsRegion);
        when(cloudRegionManager.load(eq(NOT_ALLOWED_REGION_ID))).thenReturn(nonDefaultRegion(NOT_ALLOWED_REGION_ID));
        when(cloudRegionManager.load(eq(NON_DEFAULT_REGION_ID))).thenReturn(nonDefaultRegion(NON_DEFAULT_REGION_ID));

        PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setDockerImage(TEST_IMAGE);
        configuration.setInstanceDisk(INSTANCE_DISK);
        configuration.setIsSpot(true);
        configuration.setCloudRegionId(defaultAwsRegion.getId());

        InstancePrice price = new InstancePrice(
                configuration.getInstanceType(), Integer.parseInt(configuration.getInstanceDisk()), PRICE_PER_HOUR,
                COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR);

        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(notScannedTool);
        when(toolManager.resolveSymlinks(TEST_IMAGE)).thenReturn(notScannedTool);
        when(toolManager.findToolVersion(notScannedTool)).thenReturn(Optional.empty());
        when(instanceOfferManager.isInstanceAllowed(anyString(), eq(REGION_ID), eq(true))).thenReturn(true);
        when(instanceOfferManager.isInstanceAllowed(anyString(), eq(REGION_ID), eq(false))).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(anyString(), any(), eq(REGION_ID), eq(true))).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(anyString(), any(), eq(REGION_ID), eq(false))).thenReturn(true);
        when(instanceOfferManager
                .isInstanceAllowed(anyString(), eq(NOT_ALLOWED_REGION_ID), eq(true))).thenReturn(false);
        when(instanceOfferManager
                .isToolInstanceAllowed(anyString(), any(), eq(NOT_ALLOWED_REGION_ID), eq(true))).thenReturn(false);
        when(instanceOfferManager
                .isInstanceAllowed(anyString(), eq(NON_DEFAULT_REGION_ID), eq(false))).thenReturn(true);
        when(instanceOfferManager.findOffer(anyString(), anyLong())).thenReturn(Optional.empty());
        when(instanceOfferManager.isPriceTypeAllowed(anyString(), any(), anyBoolean())).thenReturn(true);
        when(instanceOfferManager.getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong()))
                .thenReturn(price);
        when(pipelineLauncher.launch(any(PipelineRun.class), any(), any(), anyString())).thenReturn("sleep");
        when(toolScanInfoManager.loadToolVersionScanInfo(notScannedTool.getId(), null))
                .thenReturn(Optional.empty());
        final ToolVersion toolVersion = ToolVersion.builder().size(1L).platform("linux").build();
        when(toolVersionManager.loadToolVersion(anyLong(), anyString()))
                .thenReturn(toolVersion);
        when(toolVersionManager.findToolVersion(anyLong(), anyString()))
                .thenReturn(Optional.of(toolVersion));
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());
        doReturn(new PipelineConfiguration()).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());

        doReturn(true).when(permissionHelper).isAllowed(any(), any());
        preferenceManager.update(Collections.singletonList(new Preference(
                SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_DENY_NOT_SCANNED.getKey(), Boolean.toString(false))));
    }

    /**
     * Tests that Aspect will deny PipelineRunManager::runCmd method execution
     */
    @Test(expected = ToolExecutionDeniedException.class)
    public void testRunCmdFailed() {
        PipelineStart startVO = new PipelineStart();
        preferenceManager.update(Collections.singletonList(new Preference(
                SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_DENY_NOT_SCANNED.getKey(), Boolean.toString(true))));
        startVO.setDockerImage(TEST_IMAGE);
        pipelineRunManager.runCmd(startVO);
    }

    @WithMockUser(roles = "ADMIN")
    @Test(expected = IllegalArgumentException.class)
    public void testRunCmdFailsWhenFallbackTypesExceedLimit() {
        preferenceManager.update(Collections.singletonList(new Preference(
                SystemPreferences.CLUSTER_FALLBACK_INSTANCE_TYPES_MAX_COUNT.getKey(), "2")));
        final PipelineConfiguration configWithFallbacks = new PipelineConfiguration();
        configWithFallbacks.setDockerImage(TEST_IMAGE);
        configWithFallbacks.setInstanceDisk(INSTANCE_DISK);
        configWithFallbacks.setIsSpot(true);
        configWithFallbacks.setCloudRegionId(REGION_ID);
        configWithFallbacks.setFallbackInstanceTypes(Arrays.asList("m5.large", "c5.large", "r4.large"));
        doReturn(configWithFallbacks).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());

        final PipelineStart startVO = new PipelineStart();
        startVO.setDockerImage(TEST_IMAGE);
        startVO.setInstanceType(INSTANCE_TYPE);
        startVO.setHddSize(1);
        pipelineRunManager.runCmd(startVO);
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    public void testRunCmdSucceedsWhenFallbackTypesWithinLimit() {
        preferenceManager.update(Collections.singletonList(new Preference(
                SystemPreferences.CLUSTER_FALLBACK_INSTANCE_TYPES_MAX_COUNT.getKey(), "2")));
        final PipelineConfiguration configWithFallbacks = new PipelineConfiguration();
        configWithFallbacks.setDockerImage(TEST_IMAGE);
        configWithFallbacks.setInstanceDisk(INSTANCE_DISK);
        configWithFallbacks.setIsSpot(true);
        configWithFallbacks.setCloudRegionId(REGION_ID);
        configWithFallbacks.setFallbackInstanceTypes(Arrays.asList("m5.large", "c5.large"));
        doReturn(configWithFallbacks).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());

        final PipelineStart startVO = new PipelineStart();
        startVO.setDockerImage(TEST_IMAGE);
        startVO.setInstanceType(INSTANCE_TYPE);
        startVO.setHddSize(1);
        pipelineRunManager.runCmd(startVO);
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    public void testRunCmdDoesNotSetFallbackTypesWhenFeatureIsDisabled() {
        preferenceManager.update(Collections.singletonList(new Preference(
                SystemPreferences.CLUSTER_FALLBACK_INSTANCE_TYPES_MAX_COUNT.getKey(), "-1")));
        final List<String> fallbackTypes = Arrays.asList("m5.large", "c5.large", "r4.large");
        final PipelineConfiguration configWithFallbacks = new PipelineConfiguration();
        configWithFallbacks.setDockerImage(TEST_IMAGE);
        configWithFallbacks.setInstanceDisk(INSTANCE_DISK);
        configWithFallbacks.setIsSpot(true);
        configWithFallbacks.setCloudRegionId(REGION_ID);
        configWithFallbacks.setFallbackInstanceTypes(fallbackTypes);
        doReturn(configWithFallbacks).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());

        final PipelineStart startVO = new PipelineStart();
        startVO.setDockerImage(TEST_IMAGE);
        startVO.setInstanceType(INSTANCE_TYPE);
        startVO.setHddSize(1);
        final PipelineRun run = pipelineRunManager.runCmd(startVO);

        assertThat(run.getInstance().getFallbackInstanceTypes()).isNullOrEmpty();
    }

    @WithMockUser(roles = "ADMIN")
    @Test(expected = IllegalArgumentException.class)
    public void testApplyRunInstanceConfigFailsWhenInstanceTypeNotAllowed() {
        when(instanceOfferManager.isToolInstanceAllowed(eq("r4.large"), any(), eq(REGION_ID), eq(false)))
                .thenReturn(false);
        final PipelineRun run = savedToolRun();

        final RunInstanceConfigVO vo = new RunInstanceConfigVO();
        vo.setInstanceType("r4.large");
        pipelineRunManager.applyRunInstanceConfig(run.getId(), vo);
    }

    @WithMockUser(roles = "ADMIN")
    @Test(expected = IllegalArgumentException.class)
    public void testApplyRunInstanceConfigFailsWhenFallbackTypeNotAllowed() {
        when(instanceOfferManager.isToolInstanceAllowed(eq("r4.large"), any(), eq(REGION_ID), eq(false)))
                .thenReturn(false);
        final PipelineRun run = savedToolRun();

        final RunInstanceConfigVO vo = new RunInstanceConfigVO();
        vo.setFallbackInstanceTypes(Arrays.asList(INSTANCE_TYPE, "r4.large"));
        pipelineRunManager.applyRunInstanceConfig(run.getId(), vo);
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    public void testApplyRunInstanceConfigSucceedsForToolRun() {
        final PipelineRun run = savedToolRun();

        final RunInstanceConfigVO vo = new RunInstanceConfigVO();
        vo.setInstanceType(INSTANCE_TYPE);
        vo.setFallbackInstanceTypes(Arrays.asList("m5.xlarge", "c5.large"));
        pipelineRunManager.applyRunInstanceConfig(run.getId(), vo);
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    public void testApplyRunInstanceConfigUsesIsInstanceAllowedForPipelineRun() {
        when(instanceOfferManager.isInstanceAllowed(anyString(), any(), eq(REGION_ID), eq(false))).thenReturn(true);
        final PipelineRun run = savedToolRun();
        run.setPipelineId(1L);
        pipelineRunDao.updateRun(run);

        final RunInstanceConfigVO vo = new RunInstanceConfigVO();
        vo.setInstanceType(INSTANCE_TYPE);
        pipelineRunManager.applyRunInstanceConfig(run.getId(), vo);

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(REGION_ID), eq(false));
    }

    @WithMockUser(roles = "ADMIN")
    @Test(expected = IllegalArgumentException.class)
    public void testApplyRunInstanceConfigRejectsGpuMainTypeForCpuRun() {
        final InstanceOffer cpuOffer = InstanceOffer.builder().gpu(0).build();
        final InstanceOffer gpuOffer = InstanceOffer.builder().gpu(1).build();
        when(instanceOfferManager.findOffer(eq(INSTANCE_TYPE), eq(REGION_ID))).thenReturn(Optional.of(cpuOffer));
        when(instanceOfferManager.findOffer(eq("p3.2xlarge"), eq(REGION_ID))).thenReturn(Optional.of(gpuOffer));
        when(instanceOfferManager.isToolInstanceAllowed(eq("p3.2xlarge"), any(), eq(REGION_ID), anyBoolean()))
                .thenReturn(true);
        final PipelineRun run = savedToolRun();

        final RunInstanceConfigVO vo = new RunInstanceConfigVO();
        vo.setInstanceType("p3.2xlarge");
        pipelineRunManager.applyRunInstanceConfig(run.getId(), vo);
    }

    @WithMockUser(roles = "ADMIN")
    @Test(expected = IllegalArgumentException.class)
    public void testApplyRunInstanceConfigRejectsGpuFallbackTypeForCpuRun() {
        final InstanceOffer cpuOffer = InstanceOffer.builder().gpu(0).build();
        final InstanceOffer gpuOffer = InstanceOffer.builder().gpu(1).build();
        when(instanceOfferManager.findOffer(eq(INSTANCE_TYPE), eq(REGION_ID))).thenReturn(Optional.of(cpuOffer));
        when(instanceOfferManager.findOffer(eq("p3.2xlarge"), eq(REGION_ID))).thenReturn(Optional.of(gpuOffer));
        when(instanceOfferManager.isToolInstanceAllowed(eq("p3.2xlarge"), any(), eq(REGION_ID), anyBoolean()))
                .thenReturn(true);
        final PipelineRun run = savedToolRun();

        final RunInstanceConfigVO vo = new RunInstanceConfigVO();
        vo.setFallbackInstanceTypes(Arrays.asList(INSTANCE_TYPE, "p3.2xlarge"));
        pipelineRunManager.applyRunInstanceConfig(run.getId(), vo);
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    public void testApplyRunInstanceConfigAllowsCpuTypesForCpuRun() {
        final InstanceOffer cpuOffer = InstanceOffer.builder().gpu(0).build();
        when(instanceOfferManager.findOffer(anyString(), eq(REGION_ID))).thenReturn(Optional.of(cpuOffer));
        final PipelineRun run = savedToolRun();

        final RunInstanceConfigVO vo = new RunInstanceConfigVO();
        vo.setInstanceType("c5.xlarge");
        vo.setFallbackInstanceTypes(Arrays.asList("m5.large", "r5.xlarge"));
        pipelineRunManager.applyRunInstanceConfig(run.getId(), vo);
    }

    private PipelineRun savedToolRun() {
        final PipelineRun run = ObjectCreatorUtils.createPipelineRun(null, null, null, REGION_ID);
        run.setDockerImage(TEST_IMAGE);
        run.getInstance().setSpot(false);
        run.getInstance().setNodeType(INSTANCE_TYPE);
        pipelineRunDao.createPipelineRun(run);
        return run;
    }

    /**
     * Tests that Admin can run any tool
     */
    @WithMockUser(roles = "ADMIN")
    @Test
    public void testAdminRunForce() {
        PipelineStart startVO = new PipelineStart();
        startVO.setDockerImage(TEST_IMAGE);
        startVO.setForce(true);
        startVO.setInstanceType(INSTANCE_TYPE);
        startVO.setHddSize(1);
        pipelineRunManager.runCmd(startVO);

        verify(notificationManager).notifyRunStatusChanged(any(), eq(Collections.emptyMap()));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    public void testProlongIdleRunClearsLongRunningNotificationTimestamps() {
        final PipelineRun run = savedToolRun();

        pipelineRunManager.prolongIdleRun(run.getId());

        verify(monitoringNotificationDao).deleteNotificationTimestampsForIdAndType(
                run.getId(), NotificationType.LONG_RUNNING);
        verify(monitoringNotificationDao).deleteNotificationTimestampsForIdAndType(
                run.getId(), NotificationType.LONG_INIT);
    }

    private AwsRegion defaultRegion(final long id) {
        final AwsRegion defaultAwsRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        defaultAwsRegion.setId(id);
        return defaultAwsRegion;
    }

    private AwsRegion nonDefaultRegion(final long id) {
        final AwsRegion parentAwsRegion = defaultRegion(id);
        parentAwsRegion.setDefault(false);
        return parentAwsRegion;
    }
}

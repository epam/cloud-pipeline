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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStartNotificationRequest;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.notification.ContextualNotificationRegistrationManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.security.run.RunPermissionManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.entity.contextual.ContextualPreferenceLevel.REGION;
import static com.epam.pipeline.entity.contextual.ContextualPreferenceLevel.TOOL;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_SPOT;
import static com.epam.pipeline.manager.preference.SystemPreferences.COMMIT_TIMEOUT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getPipelineConfiguration;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRunWithInstance;
import static com.epam.pipeline.test.creator.region.RegionCreatorUtils.getDefaultAwsRegion;
import static com.epam.pipeline.test.creator.region.RegionCreatorUtils.getNonDefaultAwsRegion;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

@SuppressWarnings("PMD.UnusedPrivateField")
public class PipelineRunManagerLaunchTest {
    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final long REGION_ID = 1L;
    private static final long NON_ALLOWED_REGION_ID = 2L;
    private static final long NON_DEFAULT_REGION_ID = 3L;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final long PARENT_RUN_ID = 5L;
    private static final String INSTANCE_DISK = "1";
    private static final String PARENT_RUN_ID_PARAMETER = "parent-id";
    private static final String PERMISSION_NAME = "READ";
    private static final String SPOT = PriceType.SPOT.getLiteral();
    private static final String ON_DEMAND = PriceType.ON_DEMAND.getLiteral();
    private static final String DEFAULT_COMMAND = "sleep";
    private static final String TEST_USER = "user";
    private static final String IMAGE = "testImage";
    private static final LocalDateTime TEST_PERIOD = LocalDateTime.of(2019, 4, 2, 0, 0);
    private static final LocalDateTime TEST_PERIOD_18 = TEST_PERIOD.plusHours(18);
    private static final int HOURS_18 = 18;
    private static final RunStatus TEST_STATUS_1 = new RunStatus(ID, TaskStatus.RUNNING, null, TEST_PERIOD);
    private static final RunStatus TEST_STATUS_2 = new RunStatus(ID, TaskStatus.STOPPED, null,
            TEST_PERIOD.plusHours(HOURS_18));
    private static final RunStatus TEST_STATUS_3 = new RunStatus(ID_2, TaskStatus.RUNNING, null,
            TEST_PERIOD.plusHours(HOURS_18));

    @InjectMocks
    private final PipelineRunManager pipelineRunManager = new PipelineRunManager();

    @Mock
    private ToolManager toolManager;

    @Mock
    private AuthManager securityManager;

    @Mock
    private RunPermissionManager permissionManager;

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
    private ToolVersionManager toolVersionManager;

    @Mock
    private CheckPermissionHelper permissionHelper;

    @Mock
    private PipelineRunDao pipelineRunDao;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private RunStatusManager runStatusManager;

    @Mock
    private PipelineVersionManager pipelineVersionManager;

    @Mock
    private ContextualNotificationRegistrationManager contextualNotificationRegistrationManager;

    private final Tool tool = getTool(IMAGE, DEFAULT_COMMAND);
    private final AwsRegion defaultAwsRegion = getDefaultAwsRegion(ID);
    private final AwsRegion nonAllowedAwsRegion = getNonDefaultAwsRegion(ID_2);
    private final AwsRegion notDefaultAwsRegion = getNonDefaultAwsRegion(ID_3);
    private final PipelineConfiguration configuration = getPipelineConfiguration(
            IMAGE, INSTANCE_DISK, true, defaultAwsRegion.getId());
    private final PipelineConfiguration configurationWithoutRegion = getPipelineConfiguration(IMAGE, INSTANCE_DISK);
    private final InstancePrice price = new InstancePrice(configuration.getInstanceType(),
            parseInt(configuration.getInstanceDisk()), PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR);
    private final PipelineRun parentRun = getPipelineRunWithInstance(PARENT_RUN_ID, TEST_USER, NON_DEFAULT_REGION_ID);
    private final ContextualPreferenceExternalResource toolResource = new ContextualPreferenceExternalResource(
            TOOL, tool.getId().toString());
    private final ContextualPreferenceExternalResource defaultRegionResource =
            new ContextualPreferenceExternalResource(REGION, defaultAwsRegion.getId().toString());
    private final ContextualPreferenceExternalResource nonAllowedRegionResource =
            new ContextualPreferenceExternalResource(REGION, nonAllowedAwsRegion.getId().toString());
    private final List<ContextualPreferenceExternalResource> defaultRegionResources =
            singletonList(defaultRegionResource);
    private final List<ContextualPreferenceExternalResource> resources = asList(toolResource, defaultRegionResource);
    public static final PipelineStartNotificationRequest NOTIFICATION_REQUEST = new PipelineStartNotificationRequest(
            NotificationType.PIPELINE_RUN_STATUS, emptyList(), emptyList(), TEST_STRING, TEST_STRING);
    public static final List<PipelineStartNotificationRequest> NOTIFICATION_REQUESTS =
            singletonList(NOTIFICATION_REQUEST);

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        mock(CLUSTER_DOCKER_EXTRA_MULTI);
        mock(CLUSTER_INSTANCE_HDD_EXTRA_MULTI);
        mock(CLUSTER_SPOT);
        mock(COMMIT_TIMEOUT);
        mock(configuration);
        mock(tool);
        mock(parentRun);
        mock(price);
        mock(defaultAwsRegion);
        mockRestricted(nonAllowedAwsRegion);
        mock(notDefaultAwsRegion);
        doReturn(defaultAwsRegion).when(cloudRegionManager).loadDefaultRegion();

        doReturn(true).when(instanceOfferManager)
                .isInstanceAllowed(anyString(), any(), any(), anyBoolean());
        doReturn(true).when(instanceOfferManager).isPriceTypeAllowed(anyString(), any(), anyBoolean());
        doReturn(true).when(permissionHelper).isAllowed(any(), any());
        doReturn(Optional.empty()).when(pipelineVersionManager).resolvePipelineVersion(any(), any());
    }

    @Test
    public void launchPipelineShouldValidateToolInstanceType() {
        launchTool(configuration, INSTANCE_TYPE);

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(resources), eq(REGION_ID), eq(true));
    }

    @Test
    public void launchPipelineShouldValidatePriceType() {
        launchTool(configuration, INSTANCE_TYPE);

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(resources), eq(false));
    }

    @Test
    public void launchPipelineShouldFailOnNotAllowedToolInstanceType() {
        doReturn(false).when(instanceOfferManager)
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), eq(resources), eq(REGION_ID), eq(true));

        assertThrows(() -> launchTool(configuration, INSTANCE_TYPE));
        verify(instanceOfferManager)
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), eq(resources), eq(REGION_ID), eq(true));
    }

    @Test
    public void launchPipelineShouldNotValidateToolInstanceTypeIfItIsNotSpecified() {
        launchTool(configuration, null);

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(REGION_ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(REGION_ID), eq(true));
    }

    @Test
    public void launchPipelineShouldValidateToolInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(NON_ALLOWED_REGION_ID);

        assertThrows(() -> launchTool(configuration, INSTANCE_TYPE));
        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(asList(toolResource, nonAllowedRegionResource)), eq(NON_ALLOWED_REGION_ID), eq(true));
    }

    @Test
    public void launchPipelineShouldValidatePipelineInstanceType() {
        launchPipeline(configuration, INSTANCE_TYPE);

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE),
                eq(defaultRegionResources), eq(REGION_ID), eq(true));
    }

    @Test
    public void launchPipelineShouldValidatePipelineInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(NON_ALLOWED_REGION_ID);
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE),
                eq(singletonList(nonAllowedRegionResource)), eq(NON_ALLOWED_REGION_ID), eq(true));

        assertThrows(() -> launchPipeline(configuration, INSTANCE_TYPE));
        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE),
                eq(singletonList(nonAllowedRegionResource)), eq(NON_ALLOWED_REGION_ID), eq(true));
    }

    @Test
    public void launchPipelineShouldFailOnNotAllowedInstanceType() {
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE),
                eq(defaultRegionResources), eq(REGION_ID), eq(true));

        assertThrows(() -> launchPipeline(configuration, INSTANCE_TYPE));
        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE),
                eq(defaultRegionResources), eq(REGION_ID), eq(true));
    }

    @Test
    public void launchPipelineShouldNotValidatePipelineInstanceTypeIfItIsNotSpecified() {
        launchPipeline(configuration, null);

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(REGION_ID), eq(true));
    }

    @Test
    public void launchPipelineShouldValidatePriceTypeAsOnDemandIfItIsNotSpecified() {
        configuration.setIsSpot(null);

        launchPipeline(configuration, INSTANCE_TYPE);
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(defaultRegionResources), eq(false));
    }

    @Test
    public void launchPipelineShouldFailOnNotAllowedPriceType() {
        doReturn(false).when(instanceOfferManager).isPriceTypeAllowed(eq(SPOT),
                eq(defaultRegionResources), eq(false));

        assertThrows(() -> launchPipeline(configuration, INSTANCE_TYPE));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(defaultRegionResources), eq(false));
    }

    @Test
    public void launchPipelineShouldFailIfToolCloudRegionIsConfiguredAndItDiffersFromRunConfigurationOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(NON_DEFAULT_REGION_ID);
        doReturn(toolConfiguration).when(pipelineConfigurationManager)
                .getConfigurationForTool(eq(tool), eq(configuration));

        assertThrows(() -> launchPipeline(configuration, null));
        assertThrows(() -> launchTool(configuration, null));
    }

    @Test
    public void launchPipelineShouldNotFailIfToolCloudRegionIsConfiguredAndItDiffersFromDefaultOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(NON_DEFAULT_REGION_ID);
        doReturn(toolConfiguration).when(pipelineConfigurationManager)
                .getConfigurationForTool(eq(tool), eq(configurationWithoutRegion));

        launchPipeline(configurationWithoutRegion, null);
        launchTool(configurationWithoutRegion, null);

        verify(cloudRegionManager, times(4)).load(eq(NON_DEFAULT_REGION_ID));
        verify(permissionHelper, times(2)).isAdmin();
        verify(permissionHelper, times(2)).isAllowed(eq(PERMISSION_NAME), eq(notDefaultAwsRegion));
    }

    @Test
    public void launchPipelineShouldFailIfCloudRegionIsNotAllowed() {
        doReturn(false).when(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        assertThrows(() -> launchPipeline(configuration, null));
    }

    @Test
    public void runShouldUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));

        verify(cloudRegionManager).load(eq(REGION_ID));
    }

    @Test
    public void workerRunShouldUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipelineWithParentId(configuration);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));
    }

    @Test
    public void runShouldUseDefaultCloudRegionIfThereIsNoParentRunAndNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configurationWithoutRegion, INSTANCE_TYPE);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));

        verify(cloudRegionManager).loadDefaultRegion();
    }

    @Test
    public void workerRunShouldUseParentRunCloudRegionWithParentRunIdPassedExplicitlyIfThereIsNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipelineWithParentId(configurationWithoutRegion);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(NON_DEFAULT_REGION_ID));
    }

    @Test
    public void workerRunShouldUseParentRunCloudRegionWithParentRunIdPassedAsParameterIfThereIsNoRegionConfiguration() {
        final PipelineConfiguration configurationWithParentId = configurationWithoutRegion;
        final HashMap<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(PARENT_RUN_ID_PARAMETER, new PipeConfValueVO(Long.toString(PARENT_RUN_ID)));
        configurationWithParentId.setParameters(parameters);
        final PipelineRun pipelineRun = launchPipeline(configurationWithParentId, INSTANCE_TYPE);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(NON_DEFAULT_REGION_ID));
    }

    @Test
    public void launchPipelineShouldRegisterNotificationsRequestsIfSpecified() {
        final PipelineRun pipelineRun = launchPipelineWithNotificationRequests(configuration);

        verify(contextualNotificationRegistrationManager).register(eq(NOTIFICATION_REQUESTS), eq(pipelineRun));
    }

    @Test
    public void shouldLoadRunsActivityStats() {
        doReturn(asList(getPipelineRun(ID, TEST_USER), getPipelineRun(ID_2, TEST_USER)))
                .when(pipelineRunDao).loadPipelineRunsActiveInPeriod(eq(TEST_PERIOD), eq(TEST_PERIOD_18), eq(false));
        doReturn(getStatusMap()).when(runStatusManager).loadRunStatus(anyListOf(Long.class), anyBoolean());

        Map<Long, PipelineRun> runMap = pipelineRunManager
                .loadRunsActivityStats(TEST_PERIOD, TEST_PERIOD_18, false).stream()
                .collect(toMap(BaseEntity::getId, identity()));

        assertEquals(asList(TEST_STATUS_1, TEST_STATUS_2), runMap.get(ID).getRunStatuses());
        assertEquals(singletonList(TEST_STATUS_3), runMap.get(ID_2).getRunStatuses());

        verify(pipelineRunDao).loadPipelineRunsActiveInPeriod(
                any(LocalDateTime.class), any(LocalDateTime.class), anyBoolean());
        verify(runStatusManager).loadRunStatus(anyListOf(Long.class), anyBoolean());
    }

    private void mock(final InstancePrice price) {
        doReturn(price).when(instanceOfferManager)
                .getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong());
    }

    private <T> void mock(final AbstractSystemPreference<T> preference) {
        doReturn(preference.getDefaultValue()).when(preferenceManager).getPreference(eq(preference));
    }

    private void mock(final AwsRegion region, final boolean isInstanceAllowed) {
        Long id = region.getId();
        doReturn(region).when(cloudRegionManager).load(eq(id));
        doReturn(isInstanceAllowed).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(id), anyBoolean());
        doReturn(isInstanceAllowed).when(instanceOfferManager)
                .isToolInstanceAllowed(anyString(), any(), eq(id), anyBoolean());
    }

    private void mockRestricted(final AwsRegion region) {
        mock(region, false);
    }

    private void mock(final AwsRegion region) {
        mock(region, true);
    }

    private void mock(final PipelineConfiguration config) {
        doReturn(config).when(pipelineConfigurationManager).getPipelineConfiguration(any());
        doReturn(config).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());
        doReturn(new PipelineConfiguration()).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());
    }

    private void mock(final PipelineRun parentRun) {
        doReturn(parentRun).when(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
    }

    private void mock(final Tool tool) {
        doReturn(tool).when(toolManager).loadByNameOrId(eq(IMAGE));
        doReturn(tool).when(toolManager).resolveSymlinks(eq(IMAGE));
        doReturn(Optional.empty()).when(toolManager).findToolVersion(eq(tool));
    }

    private Map<Long, List<RunStatus>> getStatusMap() {
        final Map<Long, List<RunStatus>> map = new HashMap<>();
        map.put(ID, asList(TEST_STATUS_1, TEST_STATUS_2));
        map.put(ID_2, singletonList(TEST_STATUS_3));
        return map;
    }

    private void launchTool(final PipelineConfiguration configuration, final String instanceType) {
        launchPipeline(configuration, null, instanceType, null, null);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final String instanceType) {
        return launchPipeline(configuration, new Pipeline(), instanceType, null, null);
    }

    private PipelineRun launchPipelineWithParentId(final PipelineConfiguration configuration) {
        return launchPipeline(configuration, new Pipeline(), INSTANCE_TYPE, PARENT_RUN_ID, null);
    }

    private PipelineRun launchPipelineWithNotificationRequests(final PipelineConfiguration configuration) {
        return launchPipeline(configuration, new Pipeline(), null, null, NOTIFICATION_REQUESTS);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final Pipeline pipeline,
                                       final String instanceType, final Long parentRunId,
                                       final List<PipelineStartNotificationRequest> notificationRequests) {
        return pipelineRunManager.launchPipeline(configuration, pipeline, null, instanceType,
                null, null, parentRunId, null, null, null,
                notificationRequests);
    }
}

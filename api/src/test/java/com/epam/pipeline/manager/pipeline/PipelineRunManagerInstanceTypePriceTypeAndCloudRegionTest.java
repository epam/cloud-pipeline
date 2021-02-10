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
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.security.run.RunPermissionManager;
import com.epam.pipeline.util.TestUtils;
import io.reactivex.subjects.BehaviorSubject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.ap.internal.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.common.MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED;
import static com.epam.pipeline.common.MessageConstants.ERROR_PRICE_TYPE_IS_NOT_ALLOWED;
import static com.epam.pipeline.common.MessageConstants.ERROR_RUN_CLOUD_REGION_NOT_ALLOWED;
import static com.epam.pipeline.common.MessageConstants.ERROR_TOOL_CLOUD_REGION_NOT_ALLOWED;
import static com.epam.pipeline.entity.contextual.ContextualPreferenceLevel.TOOL;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_SPOT;
import static com.epam.pipeline.manager.preference.SystemPreferences.COMMIT_TIMEOUT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getPipelineConfiguration;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipeline;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRunWithInstance;
import static com.epam.pipeline.test.creator.region.RegionCreatorUtils.getDefaultAwsRegion;
import static com.epam.pipeline.test.creator.region.RegionCreatorUtils.getNonDefaultAwsRegion;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static java.lang.Integer.parseInt;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mapstruct.ap.internal.util.Collections.*;
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

@SuppressWarnings("PMD.UnusedPrivateField")
public class PipelineRunManagerInstanceTypePriceTypeAndCloudRegionTest {
    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String NOT_ALLOWED = "not allowed";
    private static final String NO_PERMISSIONS = "no permissions";
    private static final long PARENT_RUN_ID = 5L;
    private static final String INSTANCE_DISK = "1";
    private static final String PARENT_RUN_ID_PARAMETER = "parent-id";
    private static final String PERMISSION_NAME = "READ";
    private static final String SPOT = PriceType.SPOT.getLiteral();
    private static final String ON_DEMAND = PriceType.ON_DEMAND.getLiteral();
    private static final String DEFAULT_COMMAND = "sleep";
    private static final String TEST_USER = "user";
    private static final LocalDateTime TEST_PERIOD = LocalDateTime.of(2019, 4, 2, 0, 0);
    private static final int HOURS_12 = 12;
    private static final int HOURS_18 = 18;

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

    private static final String IMAGE = "testImage";
    private final Tool tool = getTool(IMAGE, DEFAULT_COMMAND);
    private final AwsRegion defaultAwsRegion = getDefaultAwsRegion(ID);
    private final AwsRegion nonAllowedAwsRegion = getNonDefaultAwsRegion(ID_2);
    private final AwsRegion notDefaultAwsRegion = getNonDefaultAwsRegion(ID_3);
    private final PipelineConfiguration configuration = getPipelineConfiguration(
            IMAGE, INSTANCE_DISK, true, defaultAwsRegion.getId());
    private final PipelineConfiguration configurationWithoutRegion = getPipelineConfiguration(IMAGE, INSTANCE_DISK);
    private final InstancePrice price = new InstancePrice(configuration.getInstanceType(),
            parseInt(configuration.getInstanceDisk()), PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR);
    private final PipelineRun parentRun = getPipelineRunWithInstance(PARENT_RUN_ID, TEST_USER, ID_3);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mockCloudRegionManager();
        mockInstanceOfferManager();
        mockPreferenceManager();
        mockPipelineConfigurationManager();
        mockToolManager();
        mockParentRun();

        doReturn(true).when(permissionHelper).isAllowed(any(), any());
    }

    @Test
    public void launchPipelineShouldValidateToolInstanceTypeAndPriceType() {
        launchTool(configuration, INSTANCE_TYPE);

        verify(toolManager).loadByNameOrId(eq(IMAGE));
        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE), eq(getResource()), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(getResource()), eq(false));
    }

    @Test
    public void launchPipelineShouldFailOnNotAllowedToolInstanceType() {
        doReturn(false).when(instanceOfferManager)
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), eq(getResource()), eq(ID), eq(true));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        final Runnable result = () -> launchTool(configuration, INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), result);

        verify(toolManager).loadByNameOrId(eq(IMAGE));
        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE), eq(getResource()), eq(ID), eq(true));
    }

    @Test
    public void launchPipelineShouldNotValidateToolInstanceTypeIfItIsNotSpecified() {
        launchTool(configuration, null);

        verify(toolManager).loadByNameOrId(eq(IMAGE));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(getResource()), eq(false));
    }

    @Test
    public void launchPipelineShouldValidateToolInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(ID_2);
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        final Runnable result = () -> launchTool(configuration, INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), result);

        verify(toolManager).loadByNameOrId(eq(IMAGE));
        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE), eq(getResource()), eq(ID_2), eq(true));
    }

    @Test
    public void launchPipelineShouldValidatePipelineInstanceType() {
        launchPipeline(configuration, INSTANCE_TYPE);

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
    }

    @Test
    public void launchPipelineShouldValidatePipelineInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(ID_2);
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchPipeline(configuration, INSTANCE_TYPE));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID_2), eq(true));
    }

    @Test
    public void launchPipelineShouldFailOnNotAllowedInstanceType() {
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchPipeline(configuration, INSTANCE_TYPE));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
    }

    @Test
    public void launchPipelineShouldNotValidatePipelineInstanceTypeIfItIsNotSpecified() {
        launchPipeline(configuration, null);

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
    }

    @Test
    public void launchPipelineShouldValidatePriceTypeAsOnDemandIfItIsNotSpecified() {
        configuration.setIsSpot(null);

        launchPipeline(configuration, INSTANCE_TYPE);

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(false));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));
    }

    @Test
    public void launchPipelineShouldFailOnNotAllowedPriceType() {
        doReturn(false).when(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_PRICE_TYPE_IS_NOT_ALLOWED), eq(PriceType.SPOT));

        final Runnable result = () -> launchPipeline(configuration, INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), result);

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
    }

    @Test
    public void launchPipelineShouldFailIfToolCloudRegionIsConfiguredAndItDiffersFromRunConfigurationOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(ID_3);
        doReturn(toolConfiguration).when(pipelineConfigurationManager)
                .getConfigurationForTool(eq(tool), eq(configuration));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_TOOL_CLOUD_REGION_NOT_ALLOWED), any(), any());

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchPipeline(configuration, null));
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchTool(configuration, null));

        verify(cloudRegionManager, times(2)).load(eq(ID));
        verify(permissionHelper, times(2)).isAdmin();
        verify(cloudRegionManager, times(2)).load(eq(ID_3));
    }

    @Test
    public void launchPipelineShouldNotFailIfToolCloudRegionIsConfiguredAndItDiffersFromDefaultOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(ID_3);
        doReturn(toolConfiguration).when(pipelineConfigurationManager)
                .getConfigurationForTool(eq(tool), eq(configurationWithoutRegion));

        launchPipeline(configurationWithoutRegion, null);
        launchTool(configurationWithoutRegion, null);

        verify(cloudRegionManager, times(4)).load(eq(ID_3));
        verify(permissionHelper, times(2)).isAdmin();
        verify(permissionHelper, times(2)).isAllowed(eq(PERMISSION_NAME), eq(notDefaultAwsRegion));
    }

    @Test
    public void launchPipelineShouldFailIfCloudRegionIsNotAllowed() {
        doReturn(false).when(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        doReturn(NO_PERMISSIONS).when(messageHelper).getMessage(eq(ERROR_RUN_CLOUD_REGION_NOT_ALLOWED), any());

        final Runnable result = () -> launchPipeline(configuration, null);
        assertThrows(e -> e.getMessage().contains(NO_PERMISSIONS), result);
    }

    @Test
    public void runShouldUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID));

        verify(cloudRegionManager).load(eq(ID));
    }

    @Test
    public void workerRunShouldUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipelineWithParentId(configuration);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID));

        verify(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
        verify(permissionManager).isRunSshAllowed(eq(parentRun));
        verify(cloudRegionManager).load(eq(ID));
    }

    @Test
    public void runShouldUseDefaultCloudRegionIfThereIsNoParentRunAndNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configurationWithoutRegion, INSTANCE_TYPE);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID));

        verify(cloudRegionManager).loadDefaultRegion();
    }

    @Test
    public void workerRunShouldUseParentRunCloudRegionWithParentRunIdPassedExplicitlyIfThereIsNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipelineWithParentId(configurationWithoutRegion);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID_3));

        verify(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
        verify(permissionManager).isRunSshAllowed(eq(parentRun));
        verify(cloudRegionManager).load(eq(ID_3));
    }

    @Test
    public void workerRunShouldUseParentRunCloudRegionWithParentRunIdPassedAsParameterIfThereIsNoRegionConfiguration() {
        final PipelineConfiguration configurationWithParentId = configurationWithoutRegion;
        final HashMap<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(PARENT_RUN_ID_PARAMETER, new PipeConfValueVO(Long.toString(PARENT_RUN_ID)));
        configurationWithParentId.setParameters(parameters);
        final PipelineRun pipelineRun = launchPipeline(configurationWithParentId, INSTANCE_TYPE);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID_3));

        verify(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
        verify(permissionManager).isRunSshAllowed(eq(parentRun));
        verify(cloudRegionManager).load(eq(ID_3));
    }

    @Test
    public void testLoadRunsActivityStats() {
        final PipelineRun pipelineRun = getPipelineRun(ID, TEST_USER);
        final PipelineRun pipelineRun2 = getPipelineRun(ID_2, TEST_USER);
        doReturn(pipelineRun).when(pipelineRunDao).loadPipelineRun(anyLong());
        doReturn(newArrayList(pipelineRun, pipelineRun2)).when(pipelineRunDao)
                .loadPipelineRunsActiveInPeriod(eq(TEST_PERIOD), eq(TEST_PERIOD.plusHours(HOURS_18)));
        doReturn(getStatusMap()).when(runStatusManager).loadRunStatus(anyListOf(Long.class));

        final PipelineRun run1 = launchPipelineRun(TEST_PERIOD, TEST_PERIOD.plusHours(HOURS_12));
        final PipelineRun run2 = launchPipelineRun(TEST_PERIOD.plusHours(HOURS_18), null);

        final Map<Long, PipelineRun> stats =
                pipelineRunManager.loadRunsActivityStats(TEST_PERIOD, TEST_PERIOD.plusHours(HOURS_18)).stream()
                        .collect(Collectors.toMap(BaseEntity::getId,
                                Function.identity()));

        Assert.assertEquals(2, stats.size());
        Assert.assertEquals(2, stats.get(run1.getId()).getRunStatuses().size());
        Assert.assertEquals(1, stats.get(run2.getId()).getRunStatuses().size());
    }

    private PipelineRun launchPipelineRun(final LocalDateTime startDate, final LocalDateTime stopDate) {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE);
        pipelineRun.setStartDate(TestUtils.convertLocalDateTimeToDate(startDate));
        saveStatusForRun(pipelineRun.getId(), TaskStatus.RUNNING, startDate);
        if (stopDate != null) {
            pipelineRun.setStatus(TaskStatus.STOPPED);
            pipelineRun.setEndDate(TestUtils.convertLocalDateTimeToDate(stopDate));
            saveStatusForRun(pipelineRun.getId(), TaskStatus.STOPPED, stopDate);
        } else {
            pipelineRun.setEndDate(null);
        }
        pipelineRunManager.updateRunInfo(pipelineRun);
        return pipelineRun;
    }

    //TODO: не забыть
    private Object getStatusMap() {
        return null;
    }

    private void saveStatusForRun(final Long runId, final TaskStatus status, final LocalDateTime timePoint) {
        runStatusManager.saveStatus(new RunStatus(runId, status, null, timePoint));
    }


    private void mockToolManager() {
        doReturn(tool).when(toolManager).loadByNameOrId(eq(IMAGE));
        doReturn(tool).when(toolManager).resolveSymlinks(eq(IMAGE));
        doReturn(Optional.empty()).when(toolManager).loadToolVersionScan(eq(tool.getId()), eq(null));
    }

    private void mockCloudRegionManager() {
        doReturn(defaultAwsRegion).when(cloudRegionManager).load(eq(ID));
        doReturn(defaultAwsRegion).when(cloudRegionManager).loadDefaultRegion();
        doReturn(nonAllowedAwsRegion).when(cloudRegionManager).load(eq(ID_2));
        doReturn(notDefaultAwsRegion).when(cloudRegionManager).load(eq(ID_3));
    }

    private void mockPipelineConfigurationManager() {
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());
        doReturn(new PipelineConfiguration()).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());
    }

    private void mockPreferenceManager() {
        doReturn(CLUSTER_DOCKER_EXTRA_MULTI.getDefaultValue())
                .when(preferenceManager).getPreference(eq(CLUSTER_DOCKER_EXTRA_MULTI));
        doReturn(CLUSTER_INSTANCE_HDD_EXTRA_MULTI.getDefaultValue())
                .when(preferenceManager).getPreference(eq(CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        doReturn(CLUSTER_SPOT.getDefaultValue()).when(preferenceManager).getPreference(eq(CLUSTER_SPOT));
        doReturn(COMMIT_TIMEOUT.getDefaultValue()).when(preferenceManager).getPreference(eq(COMMIT_TIMEOUT));
    }

    private void mockInstanceOfferManager() {
        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID), eq(true));
        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID), eq(false));
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowed(anyString(), any(), eq(ID), eq(true));
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowed(anyString(), any(), eq(ID), eq(false));
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID_2), eq(true));
        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID_3), eq(false));
        doReturn(true).when(instanceOfferManager).isPriceTypeAllowed(anyString(), any(), anyBoolean());
        doReturn(BehaviorSubject.create()).when(instanceOfferManager).getAllInstanceTypesObservable();
        doReturn(price).when(instanceOfferManager)
                .getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong());
    }

    private void mockParentRun() {
        doReturn(parentRun).when(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
        doReturn(false).when(permissionManager).isRunSshAllowed(eq(parentRun));
    }

    private void launchTool(final PipelineConfiguration configuration, final String instanceType) {
        launchPipeline(configuration, null, instanceType, null);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final String instanceType) {
        return launchPipeline(configuration, new Pipeline(), instanceType, null);
    }

    private PipelineRun launchPipelineWithParentId(final PipelineConfiguration configuration) {
        return launchPipeline(configuration, new Pipeline(), INSTANCE_TYPE, PARENT_RUN_ID);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final Pipeline pipeline,
                                       final String instanceType, final Long parentRunId) {
        return pipelineRunManager.launchPipeline(configuration, pipeline, null, instanceType, null,
                null, null, parentRunId, null, null, null);
    }

    private ContextualPreferenceExternalResource getResource() {
        return new ContextualPreferenceExternalResource(TOOL, tool.getId().toString());
    }
}

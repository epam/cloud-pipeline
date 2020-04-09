/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.controller.vo.TagsVO;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.ToolExecutionDeniedException;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.util.TestUtils;
import io.reactivex.subjects.BehaviorSubject;
import org.apache.commons.collections.CollectionUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DirtiesContext //TODO: find a better workaround, this may make tests slower. Maybe, create a special package for
                // integration tests, so they will be executed one after another and the context will remain?
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
@Transactional
@SuppressWarnings("PMD.TooManyStaticImports")
public class PipelineRunManagerTest extends AbstractManagerTest {
    private static final String PARAM_NAME_1 = "param-1";
    private static final String ENV_VAR_NAME = "TEST_ENV";
    private static final String ENV_VAR_VALUE = "value";
    private static final float PRICE_PER_HOUR = 12F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String SPOT = PriceType.SPOT.getLiteral();
    private static final String ON_DEMAND = PriceType.ON_DEMAND.getLiteral();
    private static final long REGION_ID = 1L;
    private static final long NOT_ALLOWED_REGION_ID = 2L;
    private static final long NON_DEFAULT_REGION_ID = 3L;
    private static final String NOT_ALLOWED_MESSAGE = "not allowed";
    private static final long PARENT_RUN_ID = 5L;
    private static final String INSTANCE_DISK = "1";
    private static final String PARENT_RUN_ID_PARAMETER = "parent-id";
    private static final LocalDateTime SYNC_PERIOD_START = LocalDateTime.of(2019, 4, 2, 0, 0);
    private static final LocalDateTime SYNC_PERIOD_END = LocalDateTime.of(2019, 4, 3, 0, 0);
    private static final int HOURS_12 = 12;
    private static final int HOURS_18 = 18;
    private static final int HOURS_24 = 24;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private RunStatusManager runStatusManager;

    @MockBean
    private ToolManager toolManager;

    @MockBean
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

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PreferenceManager preferenceManager;

    private static final String TEST_IMAGE = "testImage";
    private Tool notScannedTool;
    private PipelineConfiguration configuration;
    private InstancePrice price;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        notScannedTool = new Tool();
        notScannedTool.setId(1L);
        notScannedTool.setImage(TEST_IMAGE);
        notScannedTool.setDefaultCommand("sleep");

        AwsRegion defaultAwsRegion = defaultRegion(REGION_ID);
        when(cloudRegionManager.load(eq(REGION_ID))).thenReturn(defaultAwsRegion);
        when(cloudRegionManager.loadDefaultRegion()).thenReturn(defaultAwsRegion);
        when(cloudRegionManager.load(eq(NOT_ALLOWED_REGION_ID))).thenReturn(nonDefaultRegion(NOT_ALLOWED_REGION_ID));
        when(cloudRegionManager.load(eq(NON_DEFAULT_REGION_ID))).thenReturn(nonDefaultRegion(NON_DEFAULT_REGION_ID));

        configuration = new PipelineConfiguration();
        configuration.setDockerImage(TEST_IMAGE);
        configuration.setInstanceDisk(INSTANCE_DISK);
        configuration.setIsSpot(true);
        configuration.setCloudRegionId(defaultAwsRegion.getId());

        price = new InstancePrice(
                configuration.getInstanceType(), Integer.valueOf(configuration.getInstanceDisk()), PRICE_PER_HOUR);

        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(notScannedTool);
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
        when(instanceOfferManager.isPriceTypeAllowed(anyString(), any())).thenReturn(true);
        when(instanceOfferManager.getAllInstanceTypesObservable()).thenReturn(BehaviorSubject.create());
        when(instanceOfferManager.getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong()))
                .thenReturn(price);
        when(pipelineLauncher.launch(any(PipelineRun.class), any(), any(), anyString(), anyString()))
            .thenReturn("sleep");
        when(toolManager.loadToolVersionScan(notScannedTool.getId(), null))
                .thenReturn(Optional.empty());
        when(toolVersionManager.loadToolVersion(anyLong(), anyString()))
                .thenReturn(ToolVersion.builder().size(1L).build());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());
        doReturn(new PipelineConfiguration()).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());

        AwsRegion region = new AwsRegion();
        region.setRegionCode("us-east-1");
        doNothing().when(entityManager).setManagers(any());
        doNothing().when(resourceMonitoringManager).monitorResourceUsage();

        PipelineRun parentRun = new PipelineRun();
        parentRun.setId(PARENT_RUN_ID);
        RunInstance parentRunInstance = new RunInstance();
        parentRunInstance.setCloudRegionId(NON_DEFAULT_REGION_ID);
        parentRunInstance.setCloudProvider(CloudProvider.AWS);
        parentRun.setInstance(parentRunInstance);
        parentRun.setStatus(TaskStatus.RUNNING);
        parentRun.setCommitStatus(CommitStatus.NOT_COMMITTED);
        parentRun.setStartDate(DateUtils.now());
        parentRun.setPodId("podId");
        parentRun.setOwner("owner");
        parentRun.setLastChangeCommitTime(DateUtils.now());
        pipelineRunDao.createPipelineRun(parentRun);
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

        verify(notificationManager).notifyRunStatusChanged(any());
    }

    @Test
    public void testEnvVarsReplacement() {
        List<PipelineRunParameter> parameters = new ArrayList<>();
        Map<String, String> envVars = new HashMap<>();
        // case: empty collections
        List<PipelineRunParameter> actualParameters =
            pipelineRunManager.replaceParametersWithEnvVars(parameters, envVars);
        Assert.assertTrue(CollectionUtils.isEmpty(actualParameters));
        // case: empty env_vars
        String paramValue = "simple";
        parameters.add(new PipelineRunParameter(PARAM_NAME_1, paramValue));
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(parameters, envVars);
        Assert.assertEquals(parameters, actualParameters);
        // case: empty params
        envVars.put(ENV_VAR_NAME, ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(new ArrayList<>(), envVars);
        Assert.assertTrue(CollectionUtils.isEmpty(actualParameters));
        // case: replacement no needed
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(parameters, envVars);
        Assert.assertEquals(paramValue, actualParameters.get(0).getValue());
        // case: ${TEST_ENV}
        paramValue = String.format("test/${%s}", ENV_VAR_NAME);
        String expectedValue = String.format("test/%s", ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
            Collections.singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);
        checkResolvedValue(actualParameters, paramValue, expectedValue);

        // case: $TEST_ENV at the end of the line
        paramValue = String.format("test/$%s", ENV_VAR_NAME);
        expectedValue = String.format("test/%s", ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
            Collections.singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);
        checkResolvedValue(actualParameters, paramValue, expectedValue);

        // case: $TEST_ENV at the middle of the line
        paramValue = String.format("test/$%s/", ENV_VAR_NAME);
        expectedValue = String.format("test/%s/", ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
            Collections.singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);
        checkResolvedValue(actualParameters, paramValue, expectedValue);

        // case: several variables
        paramValue = String.format("test/$%s/${%s}/$%s/", ENV_VAR_NAME, ENV_VAR_NAME, ENV_VAR_NAME);
        expectedValue = String.format("test/%s/%s/%s/", ENV_VAR_VALUE, ENV_VAR_VALUE, ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
            Collections.singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);
        checkResolvedValue(actualParameters, paramValue, expectedValue);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testResolveProjectFiltering() {
        Folder project = new Folder();
        project.setId(1L);

        Folder child = new Folder();
        project.setId(2L);
        project.getChildFolders().add(child);

        Pipeline pipeline1 = new Pipeline();
        pipeline1.setId(2L);
        project.getPipelines().add(pipeline1);

        Pipeline pipeline2 = new Pipeline();
        pipeline2.setId(3L);
        child.getPipelines().add(pipeline2);

        RunConfiguration configuration1 = new RunConfiguration();
        configuration1.setId(4L);
        project.getConfigurations().add(configuration1);

        RunConfiguration configuration2 = new RunConfiguration();
        configuration2.setId(5L);
        child.getConfigurations().add(configuration2);

        when(folderManager.load(project.getId())).thenReturn(project);

        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setProjectIds(Collections.singletonList(project.getId()));

        PipelineRunFilterVO.ProjectFilter projectFilter = pipelineRunManager.resolveProjectFiltering(filterVO);
        Assert.assertEquals(2, projectFilter.getPipelineIds().size());
        Assert.assertEquals(2, projectFilter.getConfigurationIds().size());

    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesToolInstanceType() {
        launchTool(INSTANCE_TYPE);

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineFailsOnNotAllowedToolInstanceType() {
        when(instanceOfferManager
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(REGION_ID), eq(true))).thenReturn(false);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchTool(INSTANCE_TYPE));
        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineDoesNotValidateToolInstanceTypeIfItIsNotSpecified() {
        launchTool((String) null);

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(REGION_ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesToolInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(NOT_ALLOWED_REGION_ID);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchTool(INSTANCE_TYPE));
        verify(instanceOfferManager)
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(NOT_ALLOWED_REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesPipelineInstanceType() {
        launchPipeline(INSTANCE_TYPE);

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesPipelineInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(NOT_ALLOWED_REGION_ID);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchPipeline(INSTANCE_TYPE));
        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(NOT_ALLOWED_REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineFailsOnNotAllowedInstanceType() {
        when(instanceOfferManager.isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true))).thenReturn(false);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchPipeline(INSTANCE_TYPE));
        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineDoesNotValidatePipelineInstanceTypeIfItIsNotSpecified() {
        launchPipeline((String) null);

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(REGION_ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesPriceType() {
        launchPipeline(INSTANCE_TYPE);

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), any());
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesPriceTypeAsOnDemandIfItIsNotSpecified() {
        configuration.setIsSpot(null);

        launchPipeline(INSTANCE_TYPE);

        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), any());
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineFailsOnNotAllowedPriceType() {
        when(instanceOfferManager.isPriceTypeAllowed(eq(SPOT), any())).thenReturn(false);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchPipeline(INSTANCE_TYPE));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), any());
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineFailsIfToolCloudRegionIsConfiguredAndItDiffersFromRunConfigurationOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(NON_DEFAULT_REGION_ID);
        doReturn(toolConfiguration).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE), this::launchPipeline);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE), this::launchTool);
        verify(pipelineConfigurationManager, times(2)).getConfigurationForTool(any(), any());
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineDoesNotFailIfToolCloudRegionIsConfiguredAndItDiffersFromDefaultOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(NON_DEFAULT_REGION_ID);
        doReturn(toolConfiguration).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());

        launchPipeline(configurationWithoutRegion());
        launchTool(configurationWithoutRegion());
        verify(pipelineConfigurationManager, times(2)).getConfigurationForTool(any(), any());
    }

    @Test
    @WithMockUser
    public void runShouldUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE, null);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));
    }

    @Test
    @WithMockUser
    public void workerRunShouldUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE, PARENT_RUN_ID);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));
    }

    @Test
    @WithMockUser
    public void runShouldUseDefaultCloudRegionIfThereIsNoParentRunAndNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configurationWithoutRegion(), INSTANCE_TYPE, null);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));
    }

    @Test
    @WithMockUser
    public void workerRunShouldUseParentRunCloudRegionWithParentRunIdPassedExplicitlyIfThereIsNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configurationWithoutRegion(), INSTANCE_TYPE, PARENT_RUN_ID);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(NON_DEFAULT_REGION_ID));
    }

    @Test
    @WithMockUser
    public void workerRunShouldUseParentRunCloudRegionWithParentRunIdPassedAsParameterIfThereIsNoRegionConfiguration() {
        final PipelineConfiguration configurationWithParentId = configurationWithoutRegion();
        final HashMap<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(PARENT_RUN_ID_PARAMETER, new PipeConfValueVO(Long.toString(PARENT_RUN_ID)));
        configurationWithParentId.setParameters(parameters);
        final PipelineRun pipelineRun = launchPipeline(configurationWithParentId, new Pipeline(), INSTANCE_TYPE, null);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(NON_DEFAULT_REGION_ID));
    }

    @Test (expected = IllegalArgumentException.class)
    @WithMockUser
    public void shouldThrowExceptionOnInexistentRunTagUpdate() {
        pipelineRunManager.updateTags(-1L, new TagsVO(null));
    }

    @Test
    @WithMockUser
    public void testUpdateRunTags() {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE, PARENT_RUN_ID);
        loadRunAndAssertTags(pipelineRun.getId(), Collections.emptyMap());

        final Map<String, String> tags = new HashMap<>();
        tags.put("newKey", "newValue");
        updateTagsForRunAndAssertWithExpected(pipelineRun, tags, tags);
        tags.clear();
        updateTagsForRunAndAssertWithExpected(pipelineRun, tags, Collections.emptyMap());
        pipelineRun.setTags(null);
        updateTagsForRunAndAssertWithExpected(pipelineRun, null, Collections.emptyMap());
    }

    @Test
    public void testLoadRunsActivityStats() {
        final LocalDateTime beforeSyncStart = SYNC_PERIOD_START.minusHours(HOURS_12);
        final LocalDateTime afterSyncStart = SYNC_PERIOD_START.plusHours(HOURS_12);

        final PipelineRun run1 = launchPipelineRun(beforeSyncStart.minusHours(12), beforeSyncStart);
        final PipelineRun run2 = launchPipelineRun(beforeSyncStart, afterSyncStart);
        final PipelineRun run3 = launchPipelineRun(afterSyncStart, afterSyncStart.plusHours(HOURS_12));
        final PipelineRun run4 = launchPipelineRun(afterSyncStart, null);
        final PipelineRun run5 = launchPipelineRun(beforeSyncStart.minusHours(HOURS_24), null);
        saveStatusForRun(run5.getId(), TaskStatus.PAUSED, beforeSyncStart.minusHours(HOURS_18));
        saveStatusForRun(run5.getId(), TaskStatus.RUNNING, beforeSyncStart.minusHours(HOURS_12));
        final PipelineRun run6 = launchPipelineRun(beforeSyncStart.minusHours(HOURS_24), null);
        saveStatusForRun(run6.getId(), TaskStatus.RUNNING, beforeSyncStart.minusHours(HOURS_24));
        saveStatusForRun(run6.getId(), TaskStatus.PAUSED, beforeSyncStart.minusHours(HOURS_12));

        final Map<Long, PipelineRun> stats =
            pipelineRunManager.loadRunsActivityStats(SYNC_PERIOD_START, SYNC_PERIOD_END).stream()
                .collect(Collectors.toMap(BaseEntity::getId,
                                          Function.identity()));

        Assert.assertEquals(5, stats.size());
        Assert.assertNull(stats.get(run1.getId()));
        Assert.assertEquals(2, stats.get(run2.getId()).getRunStatuses().size());
        Assert.assertEquals(2, stats.get(run3.getId()).getRunStatuses().size());
        Assert.assertEquals(1, stats.get(run4.getId()).getRunStatuses().size());
        Assert.assertEquals(1, stats.get(run5.getId()).getRunStatuses().size());
    }

    @Test
    public void testAdjustStatuses() {
        final List<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(PARENT_RUN_ID, TaskStatus.RUNNING, null, SYNC_PERIOD_START.minusHours(HOURS_24)));
        statuses.add(new RunStatus(PARENT_RUN_ID, TaskStatus.PAUSED, null, SYNC_PERIOD_START.minusHours(HOURS_18)));
        statuses.add(new RunStatus(PARENT_RUN_ID, TaskStatus.RUNNING, null, SYNC_PERIOD_START.minusHours(HOURS_12)));
        statuses.add(new RunStatus(PARENT_RUN_ID, TaskStatus.PAUSED, null, SYNC_PERIOD_START.plusHours(HOURS_12)));
        statuses.add(new RunStatus(PARENT_RUN_ID, TaskStatus.RUNNING, null, SYNC_PERIOD_START.plusHours(HOURS_18)));
        statuses.add(new RunStatus(PARENT_RUN_ID, TaskStatus.PAUSED, null, SYNC_PERIOD_END.plusHours(HOURS_12)));

        final List<RunStatus> adjustedStatuses =
            pipelineRunManager.adjustStatuses(statuses, SYNC_PERIOD_START, SYNC_PERIOD_END);

        Assert.assertEquals(3, adjustedStatuses.size());

        Assert.assertEquals(TaskStatus.RUNNING, adjustedStatuses.get(0).getStatus());
        Assert.assertEquals(SYNC_PERIOD_START, adjustedStatuses.get(0).getTimestamp());

        Assert.assertEquals(TaskStatus.PAUSED, adjustedStatuses.get(1).getStatus());
        Assert.assertEquals(SYNC_PERIOD_START.plusHours(HOURS_12), adjustedStatuses.get(1).getTimestamp());

        Assert.assertEquals(TaskStatus.RUNNING, adjustedStatuses.get(2).getStatus());
        Assert.assertEquals(SYNC_PERIOD_START.plusHours(HOURS_18), adjustedStatuses.get(2).getTimestamp());
    }

    @Test
    public void testCreateRunStatuses() {
        final LocalDateTime syncStartMinus24 = SYNC_PERIOD_START.minusHours(HOURS_24);
        final LocalDateTime syncStartMinus12 = SYNC_PERIOD_START.minusHours(HOURS_12);
        final LocalDateTime syncStartPlus12 = SYNC_PERIOD_START.plusHours(HOURS_12);
        final LocalDateTime syncStartPlus18 = SYNC_PERIOD_START.plusHours(HOURS_18);
        final LocalDateTime syncEndPlus12 = SYNC_PERIOD_END.plusHours(HOURS_12);
        final LocalDateTime syncEndPlus18 = SYNC_PERIOD_END.plusHours(HOURS_18);

        final PipelineRun run1 = launchPipeline(configuration, INSTANCE_TYPE, null);
        run1.setStartDate(Timestamp.valueOf(syncStartMinus12));
        pipelineRunManager.createRunStatusesForRun(run1, SYNC_PERIOD_START, SYNC_PERIOD_END);
        assertCreatedRunStatuses(run1, SYNC_PERIOD_START, SYNC_PERIOD_END);

        final PipelineRun run2 = launchRunWithStartEndDate(syncStartMinus24, syncStartMinus12);
        pipelineRunManager.createRunStatusesForRun(run2, SYNC_PERIOD_START, SYNC_PERIOD_END);
        final List<RunStatus> runStatuses2 = run2.getRunStatuses();
        Assert.assertNull(runStatuses2);

        final PipelineRun run3 = launchRunWithStartEndDate(syncStartMinus24, syncStartPlus12);
        pipelineRunManager.createRunStatusesForRun(run3, SYNC_PERIOD_START, SYNC_PERIOD_END);
        assertCreatedRunStatuses(run3, SYNC_PERIOD_START, syncStartPlus12);

        final PipelineRun run4 = launchRunWithStartEndDate(syncStartPlus12, syncStartPlus18);
        pipelineRunManager.createRunStatusesForRun(run4, SYNC_PERIOD_START, SYNC_PERIOD_END);
        assertCreatedRunStatuses(run4, syncStartPlus12, syncStartPlus18);

        final PipelineRun run5 = launchRunWithStartEndDate(syncStartPlus12, syncEndPlus18);
        pipelineRunManager.createRunStatusesForRun(run5, SYNC_PERIOD_START, SYNC_PERIOD_END);
        assertCreatedRunStatuses(run5, syncStartPlus12, SYNC_PERIOD_END);

        final PipelineRun run6 = launchRunWithStartEndDate(syncEndPlus12, syncEndPlus18);
        pipelineRunManager.createRunStatusesForRun(run6, SYNC_PERIOD_START, SYNC_PERIOD_END);
        Assert.assertNull(run6.getRunStatuses());
    }

    private PipelineRun launchRunWithStartEndDate(final LocalDateTime startDate, final LocalDateTime endDate) {
        final PipelineRun run = launchPipeline(configuration, INSTANCE_TYPE, null);
        run.setStartDate(Timestamp.valueOf(startDate));
        run.setEndDate(Timestamp.valueOf(endDate));
        return run;
    }

    private void assertCreatedRunStatuses(final PipelineRun run,
                                          final LocalDateTime firstStatusTimestamp,
                                          final LocalDateTime secondStatusTimestamp) {
        final List<RunStatus> runStatuses = run.getRunStatuses();
        Assert.assertEquals(2, runStatuses.size());
        Assert.assertEquals(firstStatusTimestamp, runStatuses.get(0).getTimestamp());
        Assert.assertEquals(secondStatusTimestamp, runStatuses.get(1).getTimestamp());
    }

    private PipelineRun launchPipelineRun(final LocalDateTime startDate, final LocalDateTime stopDate) {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE, null);
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

    private void saveStatusForRun(final Long runId, final TaskStatus status, final LocalDateTime timePoint) {
        runStatusManager.saveStatus(new RunStatus(runId, status, null, timePoint));
    }

    private void updateTagsForRunAndAssertWithExpected(final PipelineRun run,
                                                       final Map<String, String> newTags,
                                                       final Map<String, String> expectedTags) {
        final TagsVO tagsVO = new TagsVO(newTags);
        pipelineRunManager.updateTags(run.getId(), tagsVO);
        loadRunAndAssertTags(run.getId(), expectedTags);
    }

    private void loadRunAndAssertTags(final Long runId, Map<String, String> expectedTags) {
        final PipelineRun loadedRun = pipelineRunManager.loadPipelineRun(runId);
        assertThat(expectedTags, CoreMatchers.is(loadedRun.getTags()));
    }


    private void checkResolvedValue(List<PipelineRunParameter> actualParameters, String paramValue,
            String expectedValue) {
        Assert.assertEquals(expectedValue, actualParameters.get(0).getResolvedValue());
        Assert.assertEquals(paramValue, actualParameters.get(0).getValue());
    }

    private void launchTool() {
        launchPipeline(configuration, null, null, null);
    }

    private void launchTool(final PipelineConfiguration configuration) {
        launchPipeline(configuration, null, null, null);
    }

    private void launchTool(final String instanceType) {
        launchPipeline(configuration, null, instanceType, null);
    }

    private void launchPipeline() {
        launchPipeline(configuration, new Pipeline(), null, null);
    }

    private void launchPipeline(final PipelineConfiguration configuration) {
        launchPipeline(configuration, new Pipeline(), null, null);
    }

    private void launchPipeline(final String instanceType) {
        launchPipeline(configuration, new Pipeline(), instanceType, null);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final String instanceType,
                                       final Long parentRunId) {
        return launchPipeline(configuration, new Pipeline(), instanceType, parentRunId);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final Pipeline pipeline,
                                       final String instanceType, final Long parentRunId) {
        return pipelineRunManager.launchPipeline(configuration, pipeline, null, instanceType, null, null, null,
                parentRunId, null, null, null);
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

    private PipelineConfiguration configurationWithoutRegion() {
        final PipelineConfiguration configurationWithoutRegion = new PipelineConfiguration();
        configurationWithoutRegion.setDockerImage(TEST_IMAGE);
        configurationWithoutRegion.setInstanceDisk(INSTANCE_DISK);
        return configurationWithoutRegion;
    }
}

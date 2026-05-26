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

import com.epam.pipeline.acl.folder.FolderApiService;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.controller.vo.TagsVO;
import com.epam.pipeline.controller.vo.run.RunChartFilterVO;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.metadata.PipeConfValueType;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineRunWithTool;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import com.epam.pipeline.entity.pipeline.run.RunChartInfo;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.run.RunChartInfoEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.audit.CommonAuditClient;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.security.run.RunPermissionManager;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.pipeline.PipelineRunManager.BYTES_PER_GIB;
import static com.epam.pipeline.manager.pipeline.PipelineRunManager.CP_CAP_REQUESTS_CPU;
import static com.epam.pipeline.manager.pipeline.PipelineRunManager.CP_CAP_REQUESTS_GPU;
import static com.epam.pipeline.manager.pipeline.PipelineRunManager.CP_CAP_REQUESTS_RAM;
import static com.epam.pipeline.manager.pipeline.PipelineRunManager.GIB_UNIT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_DATE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_DATE_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_NAME;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_NAME_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.IMAGE1;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.IMAGE2;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.REGISTRY1;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.REGISTRY2;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.VERSION;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getDockerRegistry;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unused")
public class PipelineRunManagerUnitTest {
    private static final Long RUN_ID = 1L;
    private static final long NOT_EXISTING_RUN_ID = -1L;
    private static final String NODE_NAME = "node_name";
    private static final Long SIZE = 10L;
    private static final Long ID_4 = 4L;
    private static final String OWNER = "USER";
    private static final String USER1 = "USER1";
    private static final String USER2 = "USER2";
    private static final String USER3 = "USER3";
    private static final String ORIGINAL_OWNER = "RUNNER";
    private static final String GROUP1 = "GROUP1";
    private static final String GROUP2 = "GROUP2";
    private static final String PARAM_NAME_1 = "param-1";
    private static final String ENV_VAR_NAME = "TEST_ENV";
    private static final String ENV_VAR_VALUE = "value";
    private static final String PROCESSED_VALUE = "Processed";
    private static final String CP_REPORT_RUN_PROCESSED_DATE = "CP_REPORT_RUN_PROCESSED_DATE";
    private static final String CP_REPORT_RUN_STATUS = "CP_REPORT_RUN_STATUS";
    private static final String INSTANCE_TYPE = "m5.large";
    public static final String DOCKER_IMAGE = "Docker Image";
    private static final int MAX_PAGE_SIZE = 100;

    @Mock
    private NodesManager nodesManager;

    @Mock
    private PipelineRunDao pipelineRunDao;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private MessageHelper messageHelper;

    @Mock
    private PipelineRunCRUDService runCRUDService;

    @Mock
    private DockerRegistryManager dockerRegistryManager;

    @Mock
    private ToolManager toolManager;

    @Mock
    private FolderApiService folderApiService;

    @Mock
    private MetadataEntityManager metadataEntityManager;

    @Mock
    private RestartRunManager restartRunManager;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private RunPermissionManager runPermissionManager;

    @Mock
    private DataStorageManager dataStorageManager;

    @Mock
    private RunStatusManager runStatusManager;

    @InjectMocks
    private PipelineRunManager pipelineRunManager;

    @Mock
    private MetadataManager metadataManager;

    @Mock
    private PipelineVersionManager pipelineVersionManager;

    @Mock
    private PipelineConfigurationManager pipelineConfigurationManager;

    @Mock
    private CommonAuditClient auditClient;

    private final Map<String, String> envVars = singletonMap(ENV_VAR_NAME, ENV_VAR_VALUE);
    private final List<PipelineRunParameter> parameters = singletonList(
            new PipelineRunParameter(PARAM_NAME_1, TEST_STRING));

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldSkipCapacityRequirementsValidationIfOfferIsMissing() {
        checkCapacityRequirements(configuration(param(CP_CAP_REQUESTS_CPU, "128")), Optional.empty());
    }

    @Test
    public void shouldPassCapacityRequirementsValidationForAllowedRequests() {
        checkCapacityRequirements(configuration(
                param(CP_CAP_REQUESTS_CPU, "2"),
                param(CP_CAP_REQUESTS_GPU, "1"),
                param(CP_CAP_REQUESTS_RAM, String.valueOf(8L * BYTES_PER_GIB))),
                Optional.of(instanceOffer(2, 1, 8D, GIB_UNIT)));
    }

    @Test
    public void shouldFailCapacityRequirementsValidationIfCpuRequestExceedsInstanceCapacity() {
        assertThrows(IllegalArgumentException.class, () -> checkCapacityRequirements(
                configuration(param(CP_CAP_REQUESTS_CPU, "3")),
                Optional.of(instanceOffer(2, 0, 8F, GIB_UNIT))));
    }

    @Test
    public void shouldFailCapacityRequirementsValidationIfGpuRequestExceedsInstanceCapacity() {
        assertThrows(IllegalArgumentException.class, () -> checkCapacityRequirements(
                configuration(param(CP_CAP_REQUESTS_GPU, "1")),
                Optional.of(instanceOffer(2, 0, 8F, GIB_UNIT))));
    }

    @Test
    public void shouldFailCapacityRequirementsValidationIfRamRequestExceedsInstanceCapacity() {
        assertThrows(IllegalArgumentException.class, () -> checkCapacityRequirements(
                configuration(param(CP_CAP_REQUESTS_RAM, String.valueOf(8L * BYTES_PER_GIB + 1))),
                Optional.of(instanceOffer(2, 0, 8F, GIB_UNIT))));
    }

    @Test
    public void shouldParseRamRequestAsLongBytes() {
        checkCapacityRequirements(
                configuration(param(CP_CAP_REQUESTS_RAM, String.valueOf(8L * BYTES_PER_GIB))),
                Optional.of(instanceOffer(2, 0, 8F, GIB_UNIT)));
    }

    @Test
    public void shouldFailCapacityRequirementsValidationIfRamRequestIsNotANumber() {
        assertThrows(IllegalArgumentException.class, () -> checkCapacityRequirements(
                configuration(param(CP_CAP_REQUESTS_RAM, "8Gi")),
                Optional.of(instanceOffer(2, 0, 8F, GIB_UNIT))));
    }

    @Test
    public void shouldFailCapacityRequirementsValidationIfRamUnitIsNotGiB() {
        assertThrows(t -> t instanceof IllegalArgumentException &&
            t.getMessage().contains("memory unit MB is not supported"),
            () -> checkCapacityRequirements(
                configuration(param(CP_CAP_REQUESTS_RAM, String.valueOf(8L * BYTES_PER_GIB))),
            Optional.of(instanceOffer(2, 0, 8F, "MB"))));
    }

    @Test
    public void shouldNotCheckMemoryUnitIfRamRequestIsMissing() {
        checkCapacityRequirements(
                configuration(param(CP_CAP_REQUESTS_CPU, "2")),
                Optional.of(instanceOffer(2, 0, 8D, "MB")));
    }

    @Test
    public void testTerminateNotExistingRun() {
        assertThrows(() -> pipelineRunManager.terminateRun(NOT_EXISTING_RUN_ID));
    }

    @Test
    public void testTerminateNotPausedRun() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run(TaskStatus.RUNNING));

        assertThrows(() -> pipelineRunManager.terminateRun(RUN_ID));
    }

    @Test
    public void testTerminatePausedRunTerminatesInstanceNode() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run(TaskStatus.PAUSED));

        pipelineRunManager.terminateRun(RUN_ID);

        verify(nodesManager).terminateRun(argThat(matches(run -> run.getId().equals(RUN_ID))));
    }

    @Test
    public void testTerminatePausedRunChangesRunStatusToStopped() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run(TaskStatus.PAUSED));

        pipelineRunManager.terminateRun(RUN_ID);

        verify(runCRUDService).updateRunStatus(argThat(matches(run -> run.getStatus() == TaskStatus.STOPPED)));
    }

    @Test
    public void testAttachDiskToNotExistingRun() {
        assertAttachFails(NOT_EXISTING_RUN_ID, diskAttachRequest());
    }

    @Test
    public void testAttachDiskToInvalidRuns() {
        assertAttachFails(run(TaskStatus.STOPPED));
        assertAttachFails(run(TaskStatus.FAILURE));
        assertAttachFails(run(TaskStatus.SUCCESS));
    }

    @Test
    public void testAttachDiskWithInvalidSize() {
        assertAttachFails(diskAttachRequest(null));
        assertAttachFails(diskAttachRequest(-SIZE));
    }

    @Test
    public void testAttachDiskToValidRuns() {
        assertAttachSucceed(run(TaskStatus.RUNNING));
        assertAttachSucceed(run(TaskStatus.PAUSING));
        assertAttachSucceed(run(TaskStatus.PAUSED));
        assertAttachSucceed(run(TaskStatus.RESUMING));
    }

    @Test
    public void shouldLoadRunsWithTools() {
        final List<Long> runIds = Arrays.asList(ID, ID_2, ID_3, ID_4);
        doReturn(Arrays.asList(dockerRegistry(ID, REGISTRY1), dockerRegistry(ID_2, REGISTRY2)))
                .when(dockerRegistryManager).loadAllDockerRegistry();
        doReturn(Arrays.asList(
                pipelineRun(ID, buildDockerImage(REGISTRY1, IMAGE1 + VERSION)),
                pipelineRun(ID_2, buildDockerImage(REGISTRY2, IMAGE1)),
                pipelineRun(ID_3, buildDockerImage(REGISTRY1, IMAGE2)),
                pipelineRun(ID_4, buildDockerImage(REGISTRY1, IMAGE2))))
                .when(runCRUDService).loadRunsByIds(runIds);
        doReturn(Arrays.asList(tool(ID, REGISTRY1, IMAGE1), tool(ID_2, REGISTRY1, IMAGE2))).when(toolManager)
                .loadAllByRegistryAndImageIn(eq(ID), any());
        doReturn(singletonList(tool(ID, REGISTRY2, IMAGE1))).when(toolManager)
                .loadAllByRegistryAndImageIn(eq(ID_2), any());

        final List<PipelineRunWithTool> result = pipelineRunManager.loadRunsWithTools(runIds);
        assertThat(result).hasSize(4);
        final Map<Long, PipelineRunWithTool> resultByRunId = result.stream()
                .collect(Collectors.toMap(runWithTool -> runWithTool.getPipelineRun().getId(), Function.identity()));
        verifyRunWithTool(resultByRunId.get(ID), REGISTRY1, IMAGE1);
        verifyRunWithTool(resultByRunId.get(ID_2), REGISTRY2, IMAGE1);
        verifyRunWithTool(resultByRunId.get(ID_3), REGISTRY1, IMAGE2);
        verifyRunWithTool(resultByRunId.get(ID_4), REGISTRY1, IMAGE2);
    }

    @Test
    public void testResolveProjectFiltering() {
        final Folder project = new Folder();
        project.setId(1L);

        final Folder child = new Folder();
        project.setId(2L);
        project.getChildFolders().add(child);

        final Pipeline pipeline1 = new Pipeline();
        pipeline1.setId(2L);
        project.getPipelines().add(pipeline1);

        final Pipeline pipeline2 = new Pipeline();
        pipeline2.setId(3L);
        child.getPipelines().add(pipeline2);

        final RunConfiguration configuration1 = new RunConfiguration();
        configuration1.setId(4L);
        project.getConfigurations().add(configuration1);

        final RunConfiguration configuration2 = new RunConfiguration();
        configuration2.setId(5L);
        child.getConfigurations().add(configuration2);

        when(folderApiService.load(project.getId())).thenReturn(project);

        final PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setProjectIds(singletonList(project.getId()));

        final PipelineRunFilterVO.ProjectFilter projectFilter = pipelineRunManager.resolveProjectFiltering(filterVO);
        assertEquals(2, projectFilter.getPipelineIds().size());
        assertEquals(2, projectFilter.getConfigurationIds().size());
    }

    @Test
    public void shouldThrowExceptionOnInexistentRunTagUpdate() {
        doReturn(null).when(pipelineRunDao).loadPipelineRun(eq(NOT_EXISTING_RUN_ID));

        assertThrows(IllegalArgumentException.class, () -> pipelineRunManager
                .updateTags(NOT_EXISTING_RUN_ID, new TagsVO(null), true));
    }

    @Test
    public void shouldReplaceEnvVarsWithEmptyCollections() {
        assertTrue(isEmpty(pipelineRunManager.replaceParametersWithEnvVars(new ArrayList<>(), new HashMap<>())));
    }

    @Test
    public void shouldReplaceEnvVarsWithEmptyEnvVars() {
        assertEquals(parameters, pipelineRunManager.replaceParametersWithEnvVars(parameters, new HashMap<>()));
    }

    @Test
    public void shouldReplaceEnvVarsWithEmptyParams() {
        assertTrue(isEmpty(pipelineRunManager.replaceParametersWithEnvVars(new ArrayList<>(), envVars)));
    }

    @Test
    public void shouldNotReplaceEnvVarsIfNoNeeded() {
        assertEquals(TEST_STRING, pipelineRunManager
                .replaceParametersWithEnvVars(parameters, envVars).get(0).getValue());
    }

    @Test
    public void shouldReplaceEnvVarsWithTestEnv() {
        assertEnvVarsReplacement("test/${%s}", "test/%s");
    }

    @Test
    public void shouldReplaceEnvVarsWithTestEnvAtTheEndOfTheLine() {
        assertEnvVarsReplacement("test/$%s", "test/%s");
    }

    @Test
    public void shouldReplaceEnvVarsWithTestEnvAtTheMiddleOfTheLine() {
        assertEnvVarsReplacement("test/$%s/", "test/%s/");
    }

    @Test
    public void shouldReplaceEnvVarsWithTestEnvWithSeveralVariables() {
        assertEnvVarsReplacement("test/$%1$s/${%1$s}/$%1$s/", "test/%1$s/%1$s/%1$s/");
    }

    @Test
    public void shouldUpdateLastProcessedDate() {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(ID);
        pipelineRun.setEntitiesIds(singletonList(ID));
        pipelineRun.setStatus(TaskStatus.STOPPED);
        pipelineRun.setEndDate(TEST_DATE);

        pipelineRun.setPipelineRunParameters(Arrays.asList(
                new PipelineRunParameter(CP_REPORT_RUN_PROCESSED_DATE, PROCESSED_VALUE),
                new PipelineRunParameter(CP_REPORT_RUN_STATUS, "Status")));

        final MetadataEntity currentMetadata = new MetadataEntity();
        currentMetadata.setData(new HashMap<>());
        doReturn(Collections.singleton(currentMetadata)).when(metadataEntityManager)
                .loadEntitiesByIds(Collections.singleton(ID));

        new JsonMapper().init();

        pipelineRunManager.updatePipelineStatus(pipelineRun);
        final ArgumentCaptor<List<MetadataEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(metadataEntityManager).loadEntitiesByIds(Collections.singleton(ID));
        verify(metadataEntityManager).updateMetadataEntities(captor.capture());
        final MetadataEntity updatedMetadataEntity = captor.getValue().get(0);
        assertThat(updatedMetadataEntity.getData())
                .hasSize(2)
                .containsKey(PROCESSED_VALUE);
    }

    @Test
    public void shouldUpdateMetadataRunStatus() {
        final String parameterValue = "Analysis status";
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(ID);
        pipelineRun.setEntitiesIds(singletonList(ID));
        pipelineRun.setStatus(TaskStatus.STOPPED);
        pipelineRun.setStartDate(TEST_DATE);
        pipelineRun.setPipelineRunParameters(singletonList(
                new PipelineRunParameter(CP_REPORT_RUN_STATUS, parameterValue)));

        final Map<String, PipeConfValue> currentData = new HashMap<>();
        currentData.put(TEST_STRING, new PipeConfValue(PipeConfValueType.STRING.toString(), TEST_STRING));
        final MetadataEntity currentMetadata = new MetadataEntity();
        currentMetadata.setData(currentData);

        doReturn(Collections.singleton(currentMetadata)).when(metadataEntityManager)
                .loadEntitiesByIds(Collections.singleton(ID));

        new JsonMapper().init();

        pipelineRunManager.updatePipelineStatus(pipelineRun);

        final String expectedDataValue = String.format(
                "[{\"runId\":1,\"status\":\"STOPPED\",\"startDate\":\"%s\"}]", TEST_DATE_STRING);
        final ArgumentCaptor<List<MetadataEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(metadataEntityManager).loadEntitiesByIds(Collections.singleton(ID));
        verify(metadataEntityManager).updateMetadataEntities(captor.capture());
        verify(runCRUDService).updateRunStatus(any());
        final List<MetadataEntity> updatedMetadataEntities = captor.getValue();
        assertThat(updatedMetadataEntities.size()).isEqualTo(1);
        final MetadataEntity updatedMetadataEntity = updatedMetadataEntities.get(0);
        assertThat(updatedMetadataEntity.getData())
                .hasSize(2)
                .containsKey(TEST_STRING)
                .containsKey(parameterValue);
        assertThat(updatedMetadataEntity.getData().get(TEST_STRING).getValue()).isEqualTo(TEST_STRING);
        assertThat(updatedMetadataEntity.getData().get(parameterValue).getValue()).isEqualTo(expectedDataValue);
    }

    @Test
    public void shouldLoadRestartedRunsWithRegions() {
        final RunInstance runInstance = new RunInstance();
        runInstance.setCloudRegionId(ID_4);

        final PipelineRun restartedRun1 = new PipelineRun();
        restartedRun1.setId(ID_2);
        restartedRun1.setInstance(new RunInstance());
        final RestartRun restartRun1 = new RestartRun();
        restartRun1.setParentRunId(ID);
        restartRun1.setRestartedRunId(ID_2);

        final PipelineRun restartedRun2 = new PipelineRun();
        restartedRun2.setId(ID_3);
        restartedRun2.setInstance(runInstance);
        final RestartRun restartRun2 = new RestartRun();
        restartRun2.setParentRunId(ID_2);
        restartRun2.setRestartedRunId(ID_3);

        final PipelineRun parentRun = new PipelineRun();
        parentRun.setId(ID);
        parentRun.setInstance(runInstance);
        parentRun.setLastChangeCommitTime(DateUtils.now());

        doReturn(parentRun).when(pipelineRunDao).loadPipelineRun(ID);
        doReturn(0).when(preferenceManager).getPreference(any());
        doReturn(false).when(runPermissionManager).isRunSshAllowed((PipelineRun) any());
        doNothing().when(dataStorageManager).analyzePipelineRunsParameters(any());
        final List<RestartRun> restartRuns = Arrays.asList(restartRun1, restartRun2);
        doReturn(restartRuns).when(restartRunManager).loadRestartedRunsForInitialRun(ID);
        final List<PipelineRun> loadedRuns = Arrays.asList(parentRun, restartedRun1, restartedRun2);
        doReturn(loadedRuns).when(pipelineRunDao).loadRunByIdIn(any());
        doReturn(null).when(runStatusManager).loadRunStatus(ID);

        final RestartRun expectedRestartRun1 = new RestartRun();
        expectedRestartRun1.setParentRunId(ID);
        expectedRestartRun1.setParentRunRegionId(ID_4);
        expectedRestartRun1.setRestartedRunId(ID_2);

        final RestartRun expectedRestartRun2 = new RestartRun();
        expectedRestartRun2.setParentRunId(ID_2);
        expectedRestartRun2.setRestartedRunId(ID_3);
        expectedRestartRun2.setRestartedRunRegionId(ID_4);

        final PipelineRun resultRun = pipelineRunManager.loadPipelineRunWithRestartedRuns(ID);

        final List<RestartRun> actualRestartRuns = resultRun.getRestartedRuns();
        assertThat(actualRestartRuns).hasSize(2)
                .contains(expectedRestartRun1)
                .contains(expectedRestartRun2);
    }

    @Test
    public void shouldLoadActiveRunsChart() {
        final RunChartInfoEntity runningUser1 = runningChart(RunChartInfoEntity.ColumnName.owner, TEST_NAME);
        final RunChartInfoEntity runningUser2 = runningChart(RunChartInfoEntity.ColumnName.owner, TEST_NAME_2);
        final RunChartInfoEntity pausingUser = pausingChart(RunChartInfoEntity.ColumnName.owner, TEST_NAME);

        final RunChartInfoEntity runningDocker1 = runningChart(RunChartInfoEntity.ColumnName.docker_image, TEST_NAME);
        final RunChartInfoEntity runningDocker2 = runningChart(RunChartInfoEntity.ColumnName.docker_image, TEST_NAME_2);
        final RunChartInfoEntity pausingDocker = pausingChart(RunChartInfoEntity.ColumnName.docker_image, TEST_NAME);

        final RunChartInfoEntity runningInstance1 = runningChart(RunChartInfoEntity.ColumnName.node_type, TEST_NAME);
        final RunChartInfoEntity runningInstance2 = runningChart(RunChartInfoEntity.ColumnName.node_type, TEST_NAME_2);
        final RunChartInfoEntity pausingInstance = pausingChart(RunChartInfoEntity.ColumnName.node_type, TEST_NAME);

        final RunChartInfoEntity runningTags1 = runningChart(RunChartInfoEntity.ColumnName.tags, TEST_NAME);
        final RunChartInfoEntity runningTags2 = runningChart(RunChartInfoEntity.ColumnName.tags, TEST_NAME_2);
        final RunChartInfoEntity pausingTags = pausingChart(RunChartInfoEntity.ColumnName.tags, TEST_NAME);

        final RunChartFilterVO runChartFilterVO = new RunChartFilterVO();
        runChartFilterVO.setStatuses(Arrays.asList(TaskStatus.RUNNING, TaskStatus.PAUSING, TaskStatus.PAUSED,
                        TaskStatus.RESUMING));
        final List<RunChartInfoEntity> entities = Arrays.asList(
                runningUser1, runningUser2, pausingUser,
                runningDocker1, runningDocker2, pausingDocker,
                runningInstance1, runningInstance2, pausingInstance,
                runningTags1, runningTags2, pausingTags);
        doReturn(entities).when(pipelineRunDao).loadRunsCharts(runChartFilterVO);

        final RunChartInfo resultChart = pipelineRunManager.loadActiveRunsCharts(new RunChartFilterVO());
        assertRunChartElements(resultChart.getOwners());
        assertRunChartElements(resultChart.getDockerImages());
        assertRunChartElements(resultChart.getInstanceTypes());
        assertRunChartElements(resultChart.getTags());
    }

    @Test
    public void testShouldExportPipelineRuns() {
        final PagingRunFilterVO filter = new PagingRunFilterVO();
        filter.setPage(1);
        filter.setPageSize(10);

        final RunInstance runInstance = new RunInstance();
        runInstance.setNodeType("node_type");

        final PipelineRun parentRun = pipelineRun(ID, DOCKER_IMAGE);

        final PipelineRun pipelineRun1 = pipelineRun(ID_2, DOCKER_IMAGE);
        pipelineRun1.setPipelineName("Pipeline name");
        pipelineRun1.setVersion("draft");
        pipelineRun1.setInstance(runInstance);

        final PipelineRun pipelineRun2 = pipelineRun(ID_3, DOCKER_IMAGE);
        final Map<String, String> tags = new HashMap<>();
        tags.put("key", "value");
        tags.put("key1", "value1");
        pipelineRun2.setTags(tags);

        final PipelineRun pipelineRun3 = pipelineRun(ID_4, DOCKER_IMAGE);
        pipelineRun3.setParentRunId(ID);

        final List<PipelineRun> loadedRuns = Arrays.asList(pipelineRun1, pipelineRun2, pipelineRun3);
        doReturn(MAX_PAGE_SIZE).when(preferenceManager).getPreference(any());
        when(pipelineRunDao.eagerSearchPipelineParentRuns(any(), any())).thenReturn(loadedRuns);
        final String[] result = new String(pipelineRunManager.exportPipelineRuns(filter, ",", "|"))
                .split("\n");
        assertEquals(4, result.length);
    }

    @Test
    public void testThrowExceptionOnSearchWithMaxPageSizeExceeded() {
        final PagingRunFilterVO filter = new PagingRunFilterVO();
        filter.setPage(1);
        filter.setPageSize(MAX_PAGE_SIZE + 1);
        doReturn(MAX_PAGE_SIZE).when(preferenceManager).getPreference(any());
        assertThrows(() -> pipelineRunManager.searchPipelineRuns(filter, false));
    }

    @Test
    public void shouldUpdateRunSidsForTool() {
        final PipelineRun run = toolRun();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);

        final RunSid userSid = userRunSid(USER1);
        final RunSid roleSid = groupRunSid(GROUP1);
        final List<RunSid> newSids = Arrays.asList(userSid, roleSid);

        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        when(toolManager.loadByNameOrId(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForToolVersion(eq(ID), eq(DOCKER_IMAGE), eq(null)))
                .thenReturn(emptyToolConfiguration());
        doNothing().when(pipelineRunDao).deleteRunSids(eq(RUN_ID));
        doNothing().when(pipelineRunDao).createRunSids(eq(RUN_ID), eq(newSids));
        doNothing().when(auditClient).log(anyString());

        final PipelineRun result = pipelineRunManager.updateRunSids(RUN_ID, newSids);

        assertNotNull(result);
        assertEquals(RUN_ID, result.getId());
        assertEquals(newSids, result.getRunSids());
        verify(pipelineRunDao).deleteRunSids(eq(RUN_ID));
        verify(pipelineRunDao).createRunSids(eq(RUN_ID), eq(newSids));
    }

    @Test
    public void shouldUpdateRunSidsIfNoConfigurationForTool() {
        final PipelineRun run = toolRun();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);

        final RunSid userSid = userRunSid(USER1);
        final RunSid roleSid = groupRunSid(GROUP1);
        final List<RunSid> newSids = Arrays.asList(userSid, roleSid);

        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        when(toolManager.loadByNameOrId(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForToolVersion(eq(ID), eq(DOCKER_IMAGE), eq(null)))
                .thenReturn(null);
        doNothing().when(pipelineRunDao).deleteRunSids(eq(RUN_ID));
        doNothing().when(pipelineRunDao).createRunSids(eq(RUN_ID), eq(newSids));
        doNothing().when(auditClient).log(anyString());

        final PipelineRun result = pipelineRunManager.updateRunSids(RUN_ID, newSids);

        assertNotNull(result);
        assertEquals(RUN_ID, result.getId());
        assertEquals(newSids, result.getRunSids());
        verify(pipelineRunDao).deleteRunSids(eq(RUN_ID));
        verify(pipelineRunDao).createRunSids(eq(RUN_ID), eq(newSids));
    }

    @Test
    public void shouldUpdateRunSidsForPipeline() {
        final PipelineRun run = toolRun();
        run.setPipelineId(ID);
        run.setConfigName(TEST_NAME);

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);

        final RunSid userSid = userRunSid(USER1);
        final RunSid roleSid = groupRunSid(GROUP1);
        final List<RunSid> newSids = Arrays.asList(userSid, roleSid);

        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        when(toolManager.loadByNameOrId(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForToolVersion(eq(ID), eq(DOCKER_IMAGE), eq(null)))
                .thenReturn(emptyToolConfiguration());
        when(pipelineVersionManager.loadParametersFromScript(eq(ID), anyString(), eq(TEST_NAME)))
                .thenReturn(new PipelineConfiguration());
        doNothing().when(pipelineRunDao).deleteRunSids(eq(RUN_ID));
        doNothing().when(pipelineRunDao).createRunSids(eq(RUN_ID), eq(newSids));
        doNothing().when(auditClient).log(anyString());

        final PipelineRun result = pipelineRunManager.updateRunSids(RUN_ID, newSids);

        assertNotNull(result);
        assertEquals(RUN_ID, result.getId());
        assertEquals(newSids, result.getRunSids());
        verify(pipelineRunDao).deleteRunSids(eq(RUN_ID));
        verify(pipelineRunDao).createRunSids(eq(RUN_ID), eq(newSids));
    }

    @Test
    public void shouldFailUpdateRunSidsWhenRunNotFound() {
        when(pipelineRunDao.loadPipelineRun(eq(NOT_EXISTING_RUN_ID))).thenReturn(null);

        final RunSid userSid = userRunSid(USER1);

        assertThrows(IllegalArgumentException.class, () ->
                pipelineRunManager.updateRunSids(NOT_EXISTING_RUN_ID, singletonList(userSid)));
    }

    @Test
    public void shouldFailUpdateRunSidsWhenToolHasSharedUsers() {
        final PipelineRun run = toolRun();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);

        final RunSid newSid = userRunSid(USER2);

        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        when(toolManager.loadByNameOrId(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForToolVersion(eq(ID), eq(DOCKER_IMAGE), eq(null)))
                .thenReturn(toolConfigurationWithSharedUsers(USER1));

        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.updateRunSids(RUN_ID, singletonList(newSid)));
    }

    @Test
    public void shouldFailUpdateRunSidsWhenToolHasSharedRoles() {
        final PipelineRun run = toolRun();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);

        final RunSid newSid = userRunSid(USER2);

        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        when(toolManager.loadByNameOrId(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForToolVersion(eq(ID), eq(DOCKER_IMAGE), eq(null)))
                .thenReturn(toolConfigurationWithSharedGroup(GROUP1));
    
        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.updateRunSids(RUN_ID, singletonList(newSid)));
    }

    @Test
    public void shouldFailUpdateRunSidsWhenPipelineHasSharedUsers() {
        final PipelineRun run = toolRun();
        run.setPipelineId(ID);
        run.setVersion(VERSION);
        run.setConfigName(TEST_NAME);

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);

        final RunSid newSid = userRunSid(USER2);

        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        when(pipelineVersionManager.loadParametersFromScript(eq(ID), eq(VERSION), eq(TEST_NAME)))
                .thenReturn(configWithSharedUsers(USER1));
        when(toolManager.loadByNameOrId(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForToolVersion(eq(ID), eq(DOCKER_IMAGE), eq(null)))
                .thenReturn(emptyToolConfiguration());
    
        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.updateRunSids(RUN_ID, singletonList(newSid)));
    }

    @Test
    public void shouldMergeRunSidsWhenPipelineHasUser1AndParentToolHasUser2() {
        // Given: Pipeline with USER1, Parent tool with USER2
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        final RunSid pipelineUser = createUserSid(USER1, RunAccessType.ENDPOINT);
        pipelineConfig.setSharedWithUsers(singletonList(pipelineUser));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        final RunSid toolUser = createUserSid(USER2, RunAccessType.ENDPOINT);
        toolConfig.setSharedWithUsers(singletonList(toolUser));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: Both users should be present
        assertThat(result).hasSize(2);
        assertContainsUser(result, USER1, RunAccessType.ENDPOINT);
        assertContainsUser(result, USER2, RunAccessType.ENDPOINT);
    }

    @Test
    public void shouldReturnEmptyRunSidsWhenNoRunSidsProvided() {
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, null,
                null);

        assertThat(result).hasSize(0);
    }

    @Test
    public void shouldUseSshAccessTypeWhenUserHasDifferentAccessTypes() {
        // Given: Pipeline with USER1 (ENDPOINT), Parent tool with USER1 (SSH)
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        final RunSid pipelineUser = createUserSid(USER1, RunAccessType.ENDPOINT);
        pipelineConfig.setSharedWithUsers(singletonList(pipelineUser));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        final RunSid toolUser = createUserSid(USER1, RunAccessType.SSH);
        toolConfig.setSharedWithUsers(singletonList(toolUser));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: Single user with SSH access
        assertThat(result).hasSize(1);
        assertContainsUser(result, USER1, RunAccessType.SSH);
    }

    @Test
    public void shouldKeepSameAccessTypeWhenUserHasSameAccessType() {
        // Given: Pipeline with USER1 (ENDPOINT), Parent tool with USER1 (ENDPOINT)
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        final RunSid pipelineUser = createUserSid(USER1, RunAccessType.ENDPOINT);
        pipelineConfig.setSharedWithUsers(singletonList(pipelineUser));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        final RunSid toolUser = createUserSid(USER1, RunAccessType.ENDPOINT);
        toolConfig.setSharedWithUsers(singletonList(toolUser));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: Single user with ENDPOINT access
        assertThat(result).hasSize(1);
        assertContainsUser(result, USER1, RunAccessType.ENDPOINT);
    }

    @Test
    public void shouldMergeRolesFromParentTool() {
        // Given: Pipeline with GROUP1, Parent tool with GROUP2
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        final RunSid pipelineRole = createRoleSid(GROUP1, RunAccessType.ENDPOINT);
        pipelineConfig.setSharedWithRoles(singletonList(pipelineRole));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        final RunSid toolRole = createRoleSid(GROUP2, RunAccessType.ENDPOINT);
        toolConfig.setSharedWithRoles(singletonList(toolRole));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: Both roles should be present
        assertThat(result).hasSize(2);
        assertContainsRole(result, GROUP1, RunAccessType.ENDPOINT);
        assertContainsRole(result, GROUP2, RunAccessType.ENDPOINT);
    }

    @Test
    public void shouldMergeBothUsersAndRoles() {
        // Given: Pipeline with USER1 and GROUP1, Parent tool with USER2 and GROUP2
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(singletonList(createUserSid(USER1, RunAccessType.ENDPOINT)));
        pipelineConfig.setSharedWithRoles(singletonList(createRoleSid(GROUP1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        toolConfig.setSharedWithUsers(singletonList(createUserSid(USER2, RunAccessType.SSH)));
        toolConfig.setSharedWithRoles(singletonList(createRoleSid(GROUP2, RunAccessType.SSH)));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: All users and roles should be present
        assertThat(result).hasSize(4);
        assertContainsUser(result, USER1, RunAccessType.ENDPOINT);
        assertContainsUser(result, USER2, RunAccessType.SSH);
        assertContainsRole(result, GROUP1, RunAccessType.ENDPOINT);
        assertContainsRole(result, GROUP2, RunAccessType.SSH);
    }

    @Test
    public void shouldFailWhenExternalRunSidsAndConfigurationHasSharedUsers() {
        // Given: External RunSid with USER3, Pipeline with USER1 (has sharing)
        final List<RunSid> externalRunSids = singletonList(createUserSid(USER3, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(singletonList(createUserSid(USER1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, externalRunSids, null));
    }

    @Test
    public void shouldFailsWhenExternalRunSidsAndParentToolHasSharedUsers() {
        // Given: External RunSid with USER3, no sharing in pipeline, Parent tool with USER2 (has sharing)
        final List<RunSid> externalRunSids = singletonList(createUserSid(USER3, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        toolConfig.setSharedWithUsers(singletonList(createUserSid(USER2, RunAccessType.ENDPOINT)));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, externalRunSids, null));
    }

    @Test
    public void shouldFailWhenExternalRunSidsAndConfigurationHasSharedRoles() {
        // Given: External RunSid with USER3, Pipeline with GROUP1 (has sharing)
        final List<RunSid> externalRunSids = singletonList(createUserSid(USER3, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithRoles(singletonList(createRoleSid(GROUP1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, externalRunSids, null));
    }

    @Test
    public void shouldApplyExternalRunSidsWhenNoConfigurationHasSharing() {
        // Given: External RunSid with USER3, no sharing in pipeline or parent tool
        final List<RunSid> externalRunSids = singletonList(createUserSid(USER3, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setDockerImage(DOCKER_IMAGE);
        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, externalRunSids,
                null);

        // Then: External USER3 should be present
        assertThat(result).hasSize(1);
        assertContainsUser(result, USER3, RunAccessType.ENDPOINT);
    }

    @Test
    public void shouldApplyOriginalOwnerWhenNoConfigurationHasSharing() {
        final List<RunSid> externalRunSids = singletonList(createUserSid(ORIGINAL_OWNER, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setDockerImage(DOCKER_IMAGE);
        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, externalRunSids,
                ORIGINAL_OWNER);

        assertThat(result).hasSize(1);
        assertContainsUser(result, ORIGINAL_OWNER, RunAccessType.ENDPOINT);
    }

    @Test
    public void shouldFailWhenExternalRunSidsAndBothConfigurationsHaveSharing() {
        // Given: External RunSid with USER3, Pipeline with USER1, Parent tool with USER2
        final List<RunSid> externalRunSids = singletonList(createUserSid(USER3, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(singletonList(createUserSid(USER1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        toolConfig.setSharedWithUsers(singletonList(createUserSid(USER2, RunAccessType.ENDPOINT)));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, externalRunSids, null));
    }

    @Test
    public void shouldAllowOriginalOwnerWhenConfigurationHasSharedUsers() {
        final List<RunSid> externalRunSids = singletonList(createUserSid(ORIGINAL_OWNER, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(singletonList(createUserSid(USER1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(
                pipelineConfig, externalRunSids, ORIGINAL_OWNER);

        assertThat(result).hasSize(2);
        assertContainsUser(result, USER1, RunAccessType.ENDPOINT);
        assertContainsUser(result, ORIGINAL_OWNER, RunAccessType.ENDPOINT);
    }

    @Test
    public void shouldFailWhenExternalContainsOtherUserWithOriginalOwnerSet() {
        final List<RunSid> externalRunSids = singletonList(createUserSid(USER3, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(singletonList(createUserSid(USER1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, externalRunSids, ORIGINAL_OWNER));
    }

    @Test
    public void shouldFailWhenExternalContainsOtherUserAndOriginalOwner() {
        final List<RunSid> externalRunSids = Arrays.asList(
                createUserSid(ORIGINAL_OWNER, RunAccessType.ENDPOINT),
                createUserSid(USER3, RunAccessType.ENDPOINT)
        );

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(singletonList(createUserSid(USER1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        assertThrows(IllegalStateException.class, () ->
                pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, externalRunSids, ORIGINAL_OWNER));
    }

    @Test
    public void shouldMergeOriginalOwnerWhenOnlyToolHasSharing() {
        final List<RunSid> externalRunSids = singletonList(createUserSid(ORIGINAL_OWNER, RunAccessType.SSH));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        toolConfig.setSharedWithUsers(singletonList(createUserSid(USER2, RunAccessType.ENDPOINT)));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(
                pipelineConfig, externalRunSids, ORIGINAL_OWNER);

        assertThat(result).hasSize(2);
        assertContainsUser(result, USER2, RunAccessType.ENDPOINT);
        assertContainsUser(result, ORIGINAL_OWNER, RunAccessType.SSH);
    }

    @Test
    public void shouldMergeOriginalOwnerWhenDuplicates() {
        final List<RunSid> externalRunSids = Arrays.asList(
                createUserSid(ORIGINAL_OWNER, RunAccessType.SSH),
                createUserSid(ORIGINAL_OWNER, RunAccessType.ENDPOINT));

        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        toolConfig.setSharedWithUsers(singletonList(createUserSid(USER2, RunAccessType.ENDPOINT)));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(
                pipelineConfig, externalRunSids, ORIGINAL_OWNER);

        assertThat(result).hasSize(2);
        assertContainsUser(result, USER2, RunAccessType.ENDPOINT);
        assertContainsUser(result, ORIGINAL_OWNER, RunAccessType.SSH);
    }

    @Test
    public void shouldReturnConfigurationRunSidsWhenParentToolHasNoSharing() {
        // Given: Pipeline with USER1, Parent tool without shared users/roles
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(singletonList(createUserSid(USER1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: Only pipeline user should be present
        assertThat(result).hasSize(1);
        assertContainsUser(result, USER1, RunAccessType.ENDPOINT);
    }

    @Test
    public void shouldMergeComplexScenarioWithMultipleUsersAndAccessTypes() {
        // Given: Complex scenario with multiple users and different access types
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(Arrays.asList(
                createUserSid(USER1, RunAccessType.ENDPOINT),
                createUserSid(USER2, RunAccessType.SSH)
        ));
        pipelineConfig.setSharedWithRoles(singletonList(createRoleSid(GROUP1, RunAccessType.ENDPOINT)));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        toolConfig.setSharedWithUsers(Arrays.asList(
                createUserSid(USER1, RunAccessType.SSH),  // Different access type - should become SSH
                createUserSid(USER3, RunAccessType.ENDPOINT)
        ));
        toolConfig.setSharedWithRoles(singletonList(createRoleSid(GROUP2, RunAccessType.SSH)));

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: Merged result with correct access types
        assertThat(result).hasSize(5);
        assertContainsUser(result, USER1, RunAccessType.SSH);  // Upgraded to SSH
        assertContainsUser(result, USER2, RunAccessType.SSH);
        assertContainsUser(result, USER3, RunAccessType.ENDPOINT);
        assertContainsRole(result, GROUP1, RunAccessType.ENDPOINT);
        assertContainsRole(result, GROUP2, RunAccessType.SSH);
    }

    @Test
    public void shouldHandleEmptyConfigurationLists() {
        // Given: Configurations with empty shared lists
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        pipelineConfig.setSharedWithUsers(Collections.emptyList());
        pipelineConfig.setSharedWithRoles(Collections.emptyList());
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();
        toolConfig.setSharedWithUsers(Collections.emptyList());
        toolConfig.setSharedWithRoles(Collections.emptyList());

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: Result should be empty
        assertThat(result).isEmpty();
    }

    @Test
    public void shouldPreserveIsPrincipalFlagForUsers() {
        // Given: User in pipeline configuration
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        final RunSid user = createUserSid(USER1, RunAccessType.ENDPOINT);
        pipelineConfig.setSharedWithUsers(singletonList(user));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: User should have isPrincipal = true
        assertThat(result).hasSize(1);
        final RunSid resultUser = result.get(0);
        assertEquals(USER1, resultUser.getName());
        assertTrue(resultUser.getIsPrincipal());
    }

    @Test
    public void shouldPreserveIsPrincipalFlagForRoles() {
        // Given: Role in pipeline configuration
        final PipelineConfiguration pipelineConfig = new PipelineConfiguration();
        final RunSid role = createRoleSid(GROUP1, RunAccessType.ENDPOINT);
        pipelineConfig.setSharedWithRoles(singletonList(role));
        pipelineConfig.setDockerImage(DOCKER_IMAGE);

        final PipelineConfiguration toolConfig = new PipelineConfiguration();

        final Tool tool = getTool(ID, OWNER);
        tool.setImage(DOCKER_IMAGE);
        when(toolManager.resolveSymlinks(eq(DOCKER_IMAGE))).thenReturn(tool);
        when(pipelineConfigurationManager.getConfigurationForTool(eq(tool), any(PipelineConfiguration.class)))
                .thenReturn(toolConfig);

        final List<RunSid> result = pipelineRunManager.mergeRunSidsWithParents(pipelineConfig, Collections.emptyList(),
                null);

        // Then: Role should have isPrincipal = false
        assertThat(result).hasSize(1);
        final RunSid resultRole = result.get(0);
        assertEquals(GROUP1, resultRole.getName());
        assertEquals(Boolean.FALSE, resultRole.getIsPrincipal());
    }

    private void assertEnvVarsReplacement(final String paramValuePattern, final String expectedValuePattern) {
        final String paramValue = String.format(paramValuePattern, ENV_VAR_NAME);
        final String expectedValue = String.format(expectedValuePattern, ENV_VAR_VALUE);
        final List<PipelineRunParameter> actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
                singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);

        assertEquals(expectedValue, actualParameters.get(0).getResolvedValue());
        assertEquals(paramValue, actualParameters.get(0).getValue());
    }

    private void assertAttachFails(final DiskAttachRequest request) {
        assertAttachFails(RUN_ID, request);
    }

    private void assertAttachFails(final Long runId, final DiskAttachRequest request) {
        assertThrows(() -> pipelineRunManager.attachDisk(runId, request));
    }

    private void assertAttachFails(final PipelineRun run) {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        assertAttachFails(diskAttachRequest());
    }

    private void assertAttachSucceed(final PipelineRun run) {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(run);
        pipelineRunManager.attachDisk(RUN_ID, diskAttachRequest());
        verify(nodesManager).attachDisk(argThat(matches(r -> r.getStatus() == run.getStatus())),
                eq(diskAttachRequest()), any());
    }

    private RunSid userRunSid(final String userName) {
        final RunSid runSid = new RunSid();
        runSid.setName(userName);
        runSid.setIsPrincipal(true);
        return runSid;
    }

    private RunSid groupRunSid(final String groupName) {
        final RunSid runSid = new RunSid();
        runSid.setName(groupName);
        runSid.setIsPrincipal(false);
        return runSid;
    }

    private PipelineRun toolRun() {
        final PipelineRun run = run(TaskStatus.RUNNING);
        run.setDockerImage(DOCKER_IMAGE);
        return run;
    }

    private ConfigurationEntry emptyToolConfiguration() {
        final PipelineConfiguration noSidsConfig = new PipelineConfiguration();
        final ConfigurationEntry config = new ConfigurationEntry();
        config.setConfiguration(noSidsConfig);
        return config;
    }

    private ConfigurationEntry toolConfigurationWithSharedUsers(final String sharedUser) {
        final ConfigurationEntry config = new ConfigurationEntry();
        config.setConfiguration(configWithSharedUsers(sharedUser));
        return config;
    }

    private ConfigurationEntry toolConfigurationWithSharedGroup(final String groupName) {
        final ConfigurationEntry config = new ConfigurationEntry();
        config.setConfiguration(configWithSharedRoles(groupName));
        return config;
    }

    private PipelineConfiguration configWithSharedUsers(final String userName) {
        final PipelineConfiguration configWithSharedUsers = new PipelineConfiguration();
        configWithSharedUsers.setSharedWithUsers(singletonList(userRunSid(userName)));
        return configWithSharedUsers;
    }

    private PipelineConfiguration configWithSharedRoles(final String groupName) {
        final PipelineConfiguration configWithSharedGroups = new PipelineConfiguration();
        configWithSharedGroups.setSharedWithRoles(singletonList(groupRunSid(groupName)));
        return configWithSharedGroups;
    }

    private PipelineRun run(final TaskStatus status) {
        final PipelineRun run = run();
        run.setStatus(status);
        return run;
    }

    private PipelineRun run() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        final RunInstance instance = new RunInstance();
        instance.setNodeName(NODE_NAME);
        run.setInstance(instance);
        return run;
    }

    private DiskAttachRequest diskAttachRequest() {
        return diskAttachRequest(SIZE);
    }

    private DiskAttachRequest diskAttachRequest(final Long size) {
        return new DiskAttachRequest(size);
    }

    private DockerRegistry dockerRegistry(final Long id, final String path) {
        final DockerRegistry registry = getDockerRegistry(id, OWNER);
        registry.setPath(path);
        return registry;
    }

    private PipelineRun pipelineRun(final Long id, final String dockerImage) {
        final PipelineRun pipelineRun = getPipelineRun(id, OWNER);
        pipelineRun.setDockerImage(dockerImage);
        return pipelineRun;
    }

    private Tool tool(final Long id, final String registry, final String image) {
        final Tool tool = getTool(id, OWNER);
        tool.setImage(image);
        tool.setRegistry(registry);
        return tool;
    }

    private void verifyRunWithTool(final PipelineRunWithTool result, final String expectedRegistry,
                                   final String expectedImage) {
        assertThat(result.getPipelineRun().getDockerImage())
                .contains(expectedRegistry)
                .contains(expectedImage);
        assertNotNull(result.getTool());
        assertThat(result.getTool().getImage()).contains(expectedImage);
    }

    private String buildDockerImage(final String registry, final String image) {
        return String.format("%s/%s", registry, image);
    }

    private RunChartInfoEntity runChart(final TaskStatus status, final RunChartInfoEntity.ColumnName columnName,
                                        final String value) {
        return RunChartInfoEntity.builder()
                .columnName(columnName)
                .status(status)
                .value(value)
                .count(SIZE)
                .build();
    }

    private RunChartInfoEntity runningChart(final RunChartInfoEntity.ColumnName columnName, final String value) {
        return runChart(TaskStatus.RUNNING, columnName, value);
    }

    private RunChartInfoEntity pausingChart(final RunChartInfoEntity.ColumnName columnName, final String value) {
        return runChart(TaskStatus.PAUSING, columnName, value);
    }

    private void checkCapacityRequirements(final PipelineConfiguration configuration,
                                           final Optional<InstanceOffer> offer) {
        ReflectionTestUtils.invokeMethod(pipelineRunManager, "checkCapacityRequirements", configuration, offer);
    }

    @SafeVarargs
    private static PipelineConfiguration configuration(final Pair<String, PipeConfValueVO>... params) {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        final Map<String, PipeConfValueVO> parameters = Arrays.stream(params)
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        configuration.setParameters(parameters);
        return configuration;
    }

    private static Pair<String, PipeConfValueVO> param(final String name, final String value) {
        return Pair.of(name, new PipeConfValueVO(value));
    }

    private static InstanceOffer instanceOffer(final int cpu, final int gpu, final double memory,
                                               final String memoryUnit) {
        final InstanceOffer offer = new InstanceOffer();
        offer.setInstanceType(INSTANCE_TYPE);
        offer.setVCPU(cpu);
        offer.setGpu(gpu);
        offer.setMemory(memory);
        offer.setMemoryUnit(memoryUnit);
        return offer;
    }

    private void assertRunChartElements(final Map<TaskStatus, Map<String, Long>> elements) {
        assertThat(elements)
                .hasSize(2)
                .containsKeys(TaskStatus.RUNNING, TaskStatus.PAUSING);
        assertThat(elements.get(TaskStatus.RUNNING))
                .hasSize(2)
                .contains(Maps.immutableEntry(TEST_NAME, SIZE))
                .contains(Maps.immutableEntry(TEST_NAME_2, SIZE));
        assertThat(elements.get(TaskStatus.PAUSING))
                .hasSize(1)
                .contains(Maps.immutableEntry(TEST_NAME, SIZE));
    }

    private RunSid createUserSid(final String name, final RunAccessType accessType) {
        final RunSid sid = new RunSid();
        sid.setName(name);
        sid.setIsPrincipal(true);
        sid.setAccessType(accessType);
        return sid;
    }

    private RunSid createRoleSid(final String name, final RunAccessType accessType) {
        final RunSid sid = new RunSid();
        sid.setName(name);
        sid.setIsPrincipal(false);
        sid.setAccessType(accessType);
        return sid;
    }

    private void assertContainsUser(final List<RunSid> runSids, final String userName,
                                    final RunAccessType accessType) {
        final RunSid user = findRunSid(runSids, userName, true);
        assertNotNull(String.format("User %s not found", userName), user);
        assertEquals(String.format("User %s has wrong access type", userName), accessType, user.getAccessType());
    }

    private void assertContainsRole(final List<RunSid> runSids, final String roleName,
                                    final RunAccessType accessType) {
        final RunSid role = findRunSid(runSids, roleName, false);
        assertNotNull(String.format("Role %s not found", roleName), role);
        assertEquals(String.format("Role %s has wrong access type", roleName), accessType, role.getAccessType());
    }

    private RunSid findRunSid(final List<RunSid> runSids, final String name, final boolean isPrincipal) {
        return runSids.stream()
                .filter(sid -> sid.getName().equals(name) && sid.getIsPrincipal() == isPrincipal)
                .findFirst()
                .orElse(null);
    }
}

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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static com.epam.pipeline.entity.cloud.CloudInstanceOperationResult.Status;
import static com.epam.pipeline.entity.utils.DateUtils.convertDateToLocalDateTime;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DockerContainerOperationManagerTest extends AbstractManagerTest {

    private static final String INSUFFICIENT_INSTANCE_CAPACITY = "InsufficientInstanceCapacity";
    private static final String NODE_NAME = "node-1";
    private static final String NODE_ID = "node-id";
    private static final String POD_ID = "pipeline";
    private static final String TEST_TAG = "tag";
    private static final long REGION_ID = 1L;
    private static final Long RUN_ID = 2L;
    private static final int CANNOT_EXECUTE_EXIT_CODE = 126;
    private static final String PAUSE_TASK_NAME = "PausePipelineRun";
    private static final String RESUME_TASK_NAME = "ResumePipelineRun";
    private static final Date FIRST_DATE = new Date();
    private static final Date SECOND_DATE = DateUtils.addMinutes(FIRST_DATE, 1);

    @InjectMocks
    @Spy
    @Autowired
    private DockerContainerOperationManager operationManager;

    @Mock
    private CloudFacade cloudFacade;

    @Mock
    private CloudRegionManager regionManager;

    @Mock
    private PipelineRunManager runManager;

    @Mock
    private RunLogManager logManager;

    @Mock
    private KubernetesManager kubernetesManager;

    @Mock
    private NodesManager nodesManager;

    @Mock (answer = Answers.RETURNS_DEEP_STUBS)
    private AuthManager authManager;

    @Mock
    private RunStatusManager runStatusManager;

    @Mock
    private PipelineLauncher pipelineLauncher;

    @Mock
    private PipelineConfigurationManager pipelineConfigurationManager;

    @Before
    public void setUp() {
        when(runManager.updatePipelineStatus(any())).thenReturn(null);
    }

    @Ignore("test will be fixed in a separate branch")
    @Test
    public void resumeRunRestorePausedStatusIfFail() {
        AwsRegion region = new AwsRegion();
        region.setRegionCode("eu-central-1");
        region.setId(1L);
        when(regionManager.load(any())).thenReturn(region);
        when(cloudFacade.startInstance(Mockito.anyLong(), anyString()))
                .thenReturn(CloudInstanceOperationResult.builder().status(Status.ERROR)
                        .message(INSUFFICIENT_INSTANCE_CAPACITY).build());

        PipelineRun run = pausingRun();
        operationManager.resumeRun(run, Collections.emptyList());

        assertEquals(run.getStatus(), TaskStatus.PAUSED);
    }

    @Test
    public void pauseRun() throws IOException {
        final PipelineRun idledRun =
            createPausingRunWithTags(ResourceMonitoringManager.UTILIZATION_LEVEL_LOW, TEST_TAG);
        final PipelineRun pressuredRun =
            createPausingRunWithTags(ResourceMonitoringManager.UTILIZATION_LEVEL_HIGH, TEST_TAG);

        when(kubernetesManager.getContainerIdFromKubernetesPod(anyString(), anyString())).thenReturn(TEST_TAG);
        when(nodesManager.terminateNode(NODE_NAME)).thenReturn(new NodeInstance());
        when(authManager.issueTokenForCurrentUser().getToken()).thenReturn(TEST_TAG);
        final Process sshConnection = Mockito.mock(Process.class);
        doReturn(sshConnection).when(operationManager).submitCommandViaSSH(anyString(), anyString());
        when(sshConnection.exitValue()).thenReturn(0);
        when(cloudFacade.instanceExists(anyLong(), anyString())).thenReturn(true);
        when(runStatusManager.loadRunStatus(anyLong()))
                .thenReturn(Collections.singletonList(pausingRunStatus(convertDateToLocalDateTime(FIRST_DATE))));

        operationManager.pauseRun(idledRun);
        operationManager.pauseRun(pressuredRun);

        assertRunStateAfterPause(idledRun, TEST_TAG);
        assertRunStateAfterPause(pressuredRun, TEST_TAG);

        verify(operationManager, times(2)).submitCommandViaSSH(anyString(), anyString());
        verifyPostPauseProcessing(2);
        final ArgumentCaptor<RunLog> runLogCaptor = ArgumentCaptor.forClass(RunLog.class);
        verify(logManager, times(2)).saveLog(runLogCaptor.capture());
        assertEquals(2, runLogCaptor.getAllValues().size());
        runLogCaptor.getAllValues()
                .forEach(result -> assertEquals(TaskStatus.SUCCESS, result.getStatus()));
    }

    @Test
    public void runShouldBeStillRunningIfCommandCannotExecute() throws IOException, InterruptedException {
        when(kubernetesManager.getContainerIdFromKubernetesPod(anyString(), anyString())).thenReturn(TEST_TAG);
        when(nodesManager.terminateNode(NODE_NAME)).thenReturn(new NodeInstance());
        when(authManager.issueTokenForCurrentUser().getToken()).thenReturn(TEST_TAG);
        when(runStatusManager.loadRunStatus(anyLong()))
                .thenReturn(Collections.singletonList(pausingRunStatus(convertDateToLocalDateTime(FIRST_DATE))));
        final Process sshConnection = Mockito.mock(Process.class);
        doReturn(sshConnection).when(operationManager).submitCommandViaSSH(anyString(), anyString());
        when(sshConnection.waitFor(anyLong(), any())).thenReturn(true);
        when(sshConnection.exitValue()).thenReturn(CANNOT_EXECUTE_EXIT_CODE);
        final PipelineRun run = pausingRun();

        operationManager.pauseRun(run);

        verify(operationManager).submitCommandViaSSH(anyString(), anyString());
        verifyPostPauseProcessing(0);
        verify(runManager).updatePipelineStatus(any());
        assertEquals(TaskStatus.RUNNING, run.getStatus());
        verify(logManager, never()).saveLog(any());
    }

    @Test
    public void shouldResumeRun() throws InterruptedException {
        when(regionManager.load(REGION_ID)).thenReturn(region());
        when(cloudFacade.instanceExists(REGION_ID, NODE_ID)).thenReturn(false);
        when(cloudFacade.startInstance(REGION_ID, NODE_ID)).thenReturn(CloudInstanceOperationResult.success(TEST_TAG));
        when(runStatusManager.loadRunStatus(anyLong()))
                .thenReturn(Collections.singletonList(resumingRunStatus(convertDateToLocalDateTime(FIRST_DATE))));
        when(pipelineConfigurationManager.getConfigurationFromRun(any())).thenReturn(new PipelineConfiguration());
        final PipelineRun run = resumingRun();
        run.setId(RUN_ID);

        operationManager.resumeRun(run, Collections.emptyList());

        verifyResumeProcessing(1);
        verify(cloudFacade).startInstance(REGION_ID, NODE_ID);
        verify(pipelineLauncher)
                .launch(any(), any(), any(), anyString(), anyBoolean(), anyString(), anyString(), anyBoolean());
        verify(runManager).updatePipelineStatus(any());
        assertEquals(TaskStatus.RUNNING, run.getStatus());
        final ArgumentCaptor<RunLog> runLogCaptor = ArgumentCaptor.forClass(RunLog.class);
        verify(logManager).saveLog(runLogCaptor.capture());
        assertEquals(TaskStatus.SUCCESS, runLogCaptor.getValue().getStatus());
        assertEquals(RESUME_TASK_NAME, runLogCaptor.getValue().getTaskName());
    }

    @Test
    public void pauseRunShouldNotRelaunchScript() throws IOException {
        final RunLog runningPause = pauseRunLog(FIRST_DATE, TaskStatus.RUNNING);
        final RunLog firstSuccess = pauseRunLog(FIRST_DATE, TaskStatus.SUCCESS);
        final RunStatus firstPausingStatus = pausingRunStatus(convertDateToLocalDateTime(FIRST_DATE));
        final RunStatus lastPausingStatus = pausingRunStatus(convertDateToLocalDateTime(SECOND_DATE));
        final PipelineRun run = pausingRun();
        run.setId(RUN_ID);

        when(runStatusManager.loadRunStatus(run.getId()))
                .thenReturn(Arrays.asList(firstPausingStatus, lastPausingStatus));
        when(logManager.loadAllLogsForTask(run.getId(), PAUSE_TASK_NAME))
                .thenReturn(Arrays.asList(runningPause, firstSuccess));
        when(cloudFacade.instanceExists(anyLong(), anyString())).thenReturn(true);

        operationManager.pauseRun(run);

        verify(operationManager, never()).submitCommandViaSSH(anyString(), anyString());
        verify(logManager, never()).saveLog(any());
        verifyPostPauseProcessing(1);
        verify(runManager).updatePipelineStatus(any());
        assertEquals(TaskStatus.PAUSED, run.getStatus());
    }

    @Test
    public void resumeRunShouldSkipInstanceRestart() throws InterruptedException {
        when(regionManager.load(REGION_ID)).thenReturn(region());
        when(cloudFacade.instanceExists(REGION_ID, NODE_ID)).thenReturn(true);
        when(runStatusManager.loadRunStatus(anyLong()))
                .thenReturn(Collections.singletonList(resumingRunStatus(convertDateToLocalDateTime(FIRST_DATE))));
        when(pipelineConfigurationManager.getConfigurationFromRun(any())).thenReturn(new PipelineConfiguration());
        final PipelineRun run = resumingRun();
        run.setId(RUN_ID);

        operationManager.resumeRun(run, Collections.emptyList());

        verifyResumeProcessing(1);
        verify(cloudFacade, never()).startInstance(REGION_ID, NODE_ID);
        verify(pipelineLauncher)
                .launch(any(), any(), any(), anyString(), anyBoolean(), anyString(), anyString(), anyBoolean());
        verify(runManager).updatePipelineStatus(any());
        assertEquals(TaskStatus.RUNNING, run.getStatus());
        final ArgumentCaptor<RunLog> runLogCaptor = ArgumentCaptor.forClass(RunLog.class);
        verify(logManager).saveLog(runLogCaptor.capture());
        assertEquals(TaskStatus.SUCCESS, runLogCaptor.getValue().getStatus());
        assertEquals(RESUME_TASK_NAME, runLogCaptor.getValue().getTaskName());
    }

    @Test
    public void resumeRunShouldSkipConfigurationRelaunch() throws InterruptedException {
        final RunStatus firstResumingStatus = resumingRunStatus(convertDateToLocalDateTime(FIRST_DATE));
        final RunStatus lastResumingStatus = resumingRunStatus(convertDateToLocalDateTime(SECOND_DATE));
        final RunLog runningResume = resumeRunLog(FIRST_DATE, TaskStatus.RUNNING);
        final RunLog firstSuccess = resumeRunLog(FIRST_DATE, TaskStatus.SUCCESS);
        final PipelineRun run = resumingRun();
        run.setId(RUN_ID);

        when(runStatusManager.loadRunStatus(RUN_ID))
                .thenReturn(Arrays.asList(firstResumingStatus, lastResumingStatus));
        when(regionManager.load(REGION_ID)).thenReturn(region());
        when(cloudFacade.instanceExists(REGION_ID, NODE_ID)).thenReturn(true);
        when(logManager.loadAllLogsForTask(run.getId(), RESUME_TASK_NAME))
                .thenReturn(Arrays.asList(runningResume, firstSuccess));

        operationManager.resumeRun(run, Collections.emptyList());

        verifyResumeProcessing(1);
        verify(pipelineLauncher, never())
                .launch(any(), any(), any(), anyString(), anyBoolean(), anyString(), anyString(), anyBoolean());
        verify(logManager, never()).saveLog(any());
        verify(runManager).updatePipelineStatus(any());
        assertEquals(TaskStatus.RUNNING, run.getStatus());
    }

    private void assertRunStateAfterPause(final PipelineRun run, final String ... expectedTags) {
        final Map<String, String> updatedTags = run.getTags();
        assertEquals(expectedTags.length, updatedTags.size());
        for (String expectedTag : expectedTags) {
            Assert.assertTrue(updatedTags.containsKey(expectedTag));
        }
        assertEquals(TaskStatus.PAUSED, run.getStatus());
    }

    private PipelineRun createPausingRunWithTags(final String ... tags) {
        final PipelineRun result = pausingRun();
        for (String tag : tags) {
            result.addTag(tag, ResourceMonitoringManager.TRUE_VALUE_STRING);
        }
        return result;
    }

    private PipelineRun pausingRun() {
        final PipelineRun run = pipelineRun();
        run.setStatus(TaskStatus.PAUSING);
        run.setRunStatuses(Collections.singletonList(pausingRunStatus(LocalDateTime.now())));
        return run;
    }

    private PipelineRun resumingRun() {
        final PipelineRun run = pipelineRun();
        run.setStatus(TaskStatus.RESUMING);
        run.setRunStatuses(Collections.singletonList(pausingRunStatus(LocalDateTime.now())));
        return run;
    }

    private PipelineRun pipelineRun() {
        final PipelineRun run = new PipelineRun();
        run.setPodId(POD_ID);
        final RunInstance instance = new RunInstance();
        instance.setNodeName(NODE_NAME);
        instance.setNodeId(NODE_ID);
        instance.setCloudRegionId(REGION_ID);
        run.setInstance(instance);
        return run;
    }

    private void verifyPostPauseProcessing(final int wantedNumberOfInvocations) {
        verify(kubernetesManager, times(wantedNumberOfInvocations)).deletePodIfExists(POD_ID);
        verify(cloudFacade, times(wantedNumberOfInvocations)).instanceExists(REGION_ID, NODE_ID);
        verify(cloudFacade, times(wantedNumberOfInvocations)).stopInstance(REGION_ID, NODE_ID);
        verify(kubernetesManager, times(wantedNumberOfInvocations)).deleteNodeIfExists(NODE_NAME);
    }

    private void verifyResumeProcessing(final int wantedNumberOfInvocations) throws InterruptedException {
        verify(regionManager, times(wantedNumberOfInvocations)).load(REGION_ID);
        verify(kubernetesManager, times(wantedNumberOfInvocations))
                .waitForNodeReady(NODE_NAME, RUN_ID.toString(), CommonCreatorConstants.TEST_STRING);
        verify(cloudFacade, times(wantedNumberOfInvocations)).instanceExists(REGION_ID, NODE_ID);
        verify(kubernetesManager, times(wantedNumberOfInvocations))
                .removeNodeLabel(NODE_NAME, KubernetesConstants.PAUSED_NODE_LABEL);
    }

    private RunStatus pausingRunStatus(final LocalDateTime timestamp) {
        return runStatus(timestamp, TaskStatus.PAUSING);
    }

    private RunStatus resumingRunStatus(final LocalDateTime timestamp) {
        return runStatus(timestamp, TaskStatus.RESUMING);
    }

    private RunStatus runStatus(final LocalDateTime timestamp, final TaskStatus taskStatus) {
        return RunStatus.builder()
                .timestamp(timestamp)
                .status(taskStatus)
                .build();
    }

    private RunLog pauseRunLog(final Date date, final TaskStatus status) {
        return runLog(date, status, PAUSE_TASK_NAME);
    }

    private RunLog resumeRunLog(final Date date, final TaskStatus status) {
        return runLog(date, status, RESUME_TASK_NAME);
    }

    private RunLog runLog(final Date date, final TaskStatus status, final String taskName) {
        return RunLog.builder()
                .date(date)
                .status(status)
                .taskName(taskName)
                .build();
    }

    private AwsRegion region() {
        final AwsRegion region = RegionCreatorUtils.getDefaultAwsRegion();
        region.setId(REGION_ID);
        return region;
    }
}

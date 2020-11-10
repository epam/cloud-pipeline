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
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
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
import java.util.Collections;
import java.util.Map;

import static com.epam.pipeline.entity.cloud.CloudInstanceOperationResult.Status;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
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
    private static final int CANNOT_EXECUTE_EXIT_CODE = 126;

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

        PipelineRun run = createRun();
        operationManager.resumeRun(run, Collections.emptyList());

        assertEquals(run.getStatus(), TaskStatus.PAUSED);
    }

    @Test
    public void pauseRun() throws IOException {
        final PipelineRun idledRun =
            createRunWithTags(ResourceMonitoringManager.UTILIZATION_LEVEL_LOW, TEST_TAG);
        final PipelineRun pressuredRun =
            createRunWithTags(ResourceMonitoringManager.UTILIZATION_LEVEL_HIGH, TEST_TAG);

        when(kubernetesManager.getContainerIdFromKubernetesPod(anyString(), anyString())).thenReturn(TEST_TAG);
        when(nodesManager.terminateNode(NODE_NAME)).thenReturn(new NodeInstance());
        when(authManager.issueTokenForCurrentUser().getToken()).thenReturn(TEST_TAG);
        final Process sshConnection = Mockito.mock(Process.class);
        doReturn(sshConnection).when(operationManager).submitCommandViaSSH(anyString(), anyString());
        when(sshConnection.exitValue()).thenReturn(0);

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
        final Process sshConnection = Mockito.mock(Process.class);
        doReturn(sshConnection).when(operationManager).submitCommandViaSSH(anyString(), anyString());
        when(sshConnection.waitFor(anyLong(), any())).thenReturn(true);
        when(sshConnection.exitValue()).thenReturn(CANNOT_EXECUTE_EXIT_CODE);
        final PipelineRun run = createRun();

        operationManager.pauseRun(run);

        verify(operationManager).submitCommandViaSSH(anyString(), anyString());
        verifyPostPauseProcessing(0);
        verify(runManager).updatePipelineStatus(any());
        assertEquals(TaskStatus.RUNNING, run.getStatus());
        verify(logManager, never()).saveLog(any());
    }

    private void assertRunStateAfterPause(final PipelineRun run, final String ... expectedTags) {
        final Map<String, String> updatedTags = run.getTags();
        assertEquals(expectedTags.length, updatedTags.size());
        for (String expectedTag : expectedTags) {
            Assert.assertTrue(updatedTags.containsKey(expectedTag));
        }
        assertEquals(TaskStatus.PAUSED, run.getStatus());
    }

    private PipelineRun createRunWithTags(final String ... tags) {
        final PipelineRun result = createRun();
        for (String tag : tags) {
            result.addTag(tag, ResourceMonitoringManager.TRUE_VALUE_STRING);
        }
        return result;
    }

    private PipelineRun createRun() {
        final PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.PAUSING);
        run.setPodId(POD_ID);
        final RunInstance instance = new RunInstance();
        instance.setNodeName(NODE_NAME);
        instance.setNodeId(NODE_ID);
        instance.setCloudRegionId(REGION_ID);
        run.setInstance(instance);
        return run;
    }

    private void verifyPostPauseProcessing(final int wantedNumberOfInvocations) {
        verify(kubernetesManager, times(wantedNumberOfInvocations)).deletePod(POD_ID);
        verify(cloudFacade, times(wantedNumberOfInvocations)).stopInstance(REGION_ID, NODE_ID);
        verify(kubernetesManager, times(wantedNumberOfInvocations)).deleteNode(NODE_NAME);
    }
}

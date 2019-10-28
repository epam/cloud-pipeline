package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.epam.pipeline.entity.cloud.CloudInstanceOperationResult.Status;


public class DockerContainerOperationManagerTest extends AbstractManagerTest {

    private static final String INSUFFICIENT_INSTANCE_CAPACITY = "InsufficientInstanceCapacity";
    private static final String NODE_NAME = "node-1";
    private static final String NODE_ID = "node-id";
    private static final String TEST_TAG = "tag";
    private static final long REGION_ID = 1L;

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
        Mockito.when(runManager.updatePipelineStatus(Mockito.any())).thenReturn(null);
        Mockito.when(logManager.saveLog(Mockito.any())).thenReturn(null);
    }

    @Ignore("test will be fixed in a separate branch")
    @Test
    public void resumeRunRestorePausedStatusIfFail() {
        AwsRegion region = new AwsRegion();
        region.setRegionCode("eu-central-1");
        region.setId(1L);
        Mockito.when(regionManager.load(Mockito.any())).thenReturn(region);
        Mockito.when(cloudFacade.startInstance(Mockito.anyLong(), Mockito.anyString()))
                .thenReturn(CloudInstanceOperationResult.builder().status(Status.ERROR)
                        .message(INSUFFICIENT_INSTANCE_CAPACITY).build());

        PipelineRun run = createRun();
        operationManager.resumeRun(run, Collections.emptyList());

        Assert.assertEquals(run.getStatus(), TaskStatus.PAUSED);
    }

    @Test
    public void pauseRun() throws IOException {
        final PipelineRun idledRun =
            createRunWithTags(ResourceMonitoringManager.UTILIZATION_LEVEL_LOW, TEST_TAG);
        final PipelineRun pressuredRun =
            createRunWithTags(ResourceMonitoringManager.UTILIZATION_LEVEL_HIGH, TEST_TAG);

        Mockito.when(kubernetesManager.getContainerIdFromKubernetesPod(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(TEST_TAG);
        Mockito.when(nodesManager.terminateNode(NODE_NAME)).thenReturn(new NodeInstance());
        Mockito.when(authManager.issueTokenForCurrentUser().getToken()).thenReturn(TEST_TAG);
        Process sshConnection = Mockito.mock(Process.class);
        Mockito.doReturn(sshConnection).when(operationManager)
            .submitCommandViaSSH(Mockito.anyString(), Mockito.anyString());
        Mockito.when(sshConnection.exitValue()).thenReturn(0);

        operationManager.pauseRun(idledRun);
        operationManager.pauseRun(pressuredRun);

        assertRunStateAfterPause(idledRun, TEST_TAG);
        assertRunStateAfterPause(pressuredRun, TEST_TAG);
    }

    private void assertRunStateAfterPause(final PipelineRun run, final String ... expectedTags) {
        final Map<String, String> updatedTags = run.getTags();
        Assert.assertEquals(expectedTags.length, updatedTags.size());
        for (String expectedTag : expectedTags) {
            Assert.assertTrue(updatedTags.containsKey(expectedTag));
        }
        Assert.assertEquals(TaskStatus.PAUSED, run.getStatus());
    }

    private PipelineRun createRunWithTags(final String ... tags) {
        final PipelineRun result = createRun();
        for (String tag : tags) {
            result.addTag(tag, ResourceMonitoringManager.TRUE_VALUE_STRING);
        }
        return result;
    }

    private PipelineRun createRun() {
        PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.PAUSING);
        RunInstance instance = new RunInstance();
        instance.setNodeName(NODE_NAME);
        instance.setNodeId(NODE_ID);
        instance.setCloudRegionId(REGION_ID);
        run.setInstance(instance);
        return run;
    }
}

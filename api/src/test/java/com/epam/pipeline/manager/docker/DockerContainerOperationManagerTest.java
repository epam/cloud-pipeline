package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;

import static com.epam.pipeline.entity.cloud.CloudInstanceOperationResult.Status;


public class DockerContainerOperationManagerTest extends AbstractManagerTest {

    private static final String INSUFFICIENT_INSTANCE_CAPACITY = "InsufficientInstanceCapacity";
    private static final String NODE_NAME = "node-1";
    private static final String NODE_ID = "node-id";
    private static final long REGION_ID = 1L;

    @InjectMocks
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

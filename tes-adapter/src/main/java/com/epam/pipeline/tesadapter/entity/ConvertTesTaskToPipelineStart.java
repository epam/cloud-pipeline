package com.epam.pipeline.tesadapter.entity;

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

public class ConvertTesTaskToPipelineStart {
    private Long pipelineId;
    private String version;
    private Long timeout;
    private String instanceType;
    private Integer hddSize;
    private String dockerImage;
    private String cmdTemplate;
    private Long useRunId;
    private Long parentNodeId;
    private String configurationName; //stubbed
    private Integer nodeCount;
    private String workerCmd;
    private Long parentRunId;
    private Boolean isSpot;
    private List<RunSid> runSids;
    private Long cloudRegionId; //stubbed
    private boolean force;
    private ExecutionEnvironment executionEnvironment;
    private String prettyUrl;
    private boolean nonPause;
    private Map<String, PipeConfValueVO> params;

    private PipelineStart pipelineStart;

    public ConvertTesTaskToPipelineStart(@Value("${cloud.pipeline.instanceType}") String instanceType,
                                         @Value("${cloud.pipeline.hddSize}") Integer hddSize, TesTask tesTask) {
        this.pipelineId = null;
        this.version = null;
        this.timeout = null;
        this.instanceType = instanceType;
        this.hddSize = hddSize;
        this.dockerImage = tesTask.getExecutors().get(0).getImage();
        this.cmdTemplate = String.join(" ", tesTask.getExecutors().get(0).getCommand());//List of Executors?
        this.useRunId = null;
        this.parentNodeId = null;
        this.nodeCount = null;
        this.workerCmd = null;
        this.parentRunId = null;
        this.isSpot = tesTask.getResources().getPreemptible();
        this.runSids = null;
        this.force = false;
        this.executionEnvironment = ExecutionEnvironment.CLOUD_PLATFORM;
        this.prettyUrl = null;
        this.nonPause = true;
        this.params.put("", tesTask.getInputs().get(0).getContent());
    }

    public void getPipelineStartFromTesTask() {

    }

    private PipelineStart setParamsToPipelineStart() {
        pipelineStart.setPipelineId(pipelineId);
        pipelineStart.setVersion(version);
        pipelineStart.setTimeout(timeout);
        pipelineStart.setInstanceType(instanceType);
        pipelineStart.setHddSize(hddSize);
        pipelineStart.setDockerImage(dockerImage);
        pipelineStart.setCmdTemplate(cmdTemplate);
        pipelineStart.setUseRunId(useRunId);
        pipelineStart.setParentNodeId(parentNodeId);
        pipelineStart.setNodeCount(nodeCount);
        pipelineStart.setWorkerCmd(workerCmd);
        pipelineStart.setParentRunId(parentRunId);
        pipelineStart.setIsSpot(isSpot);
        pipelineStart.setRunSids(runSids);
        pipelineStart.setForce(force);
        pipelineStart.setExecutionEnvironment(executionEnvironment);
        pipelineStart.setPrettyUrl(prettyUrl);
        pipelineStart.setNonPause(nonPause);
        pipelineStart.setParams();
    }


}

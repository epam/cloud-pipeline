package com.epam.pipeline.tesadapter.entity;

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

public class TaskMapper {
    private Long pipelineId;
    private String version;
    private Long timeout;

    @Value("${cloud.pipeline.instanceType}")
    private String instanceType;

    @Value("${cloud.pipeline.hddSize}")
    private Integer hddSize;

    private String dockerImage;
    private String cmdTemplate;
    private Long useRunId;
    private Long parentNodeId;
    // private String configurationName; //stubbed
    private Integer nodeCount;
    private String workerCmd;
    private Long parentRunId;
    private Boolean isSpot;
    private List<RunSid> runSids;
    // private Long cloudRegionId; //stubbed
    private boolean force;
    private ExecutionEnvironment executionEnvironment;
    private String prettyUrl;
    private boolean nonPause;
    private Map<String, PipeConfValueVO> params;

    private PipelineStart pipelineStart;

    public TaskMapper() {
        this.pipelineId = null;
        this.version = null;
        this.timeout = null;
        this.useRunId = null;
        this.parentNodeId = null;
        this.nodeCount = null;
        this.workerCmd = null;
        this.parentRunId = null;
        this.runSids = null;
        this.force = false;
        this.executionEnvironment = ExecutionEnvironment.CLOUD_PLATFORM;
        this.prettyUrl = null;
        this.nonPause = true;
    }

    public PipelineStart mapToPipelineStart(TesTask tesTask) {
        this.dockerImage = tesTask.getExecutors().get(0).getImage(); //List of Executors
        this.cmdTemplate = String.join(" ", tesTask.getExecutors().get(0).getCommand());//List of Executors?
        this.isSpot = tesTask.getResources().getPreemptible();
        //map params
        this.params.put("inputs", new PipeConfValueVO(tesTask.getInputs().get(0).getPath(), "input"));
        this.params.put("outputs", new PipeConfValueVO(tesTask.getOutputs().get(0).getPath(), "output"));
        this.params.put("envs", new PipeConfValueVO(
                String.join(" ", tesTask.getExecutors().get(0).getEnv().values()), "string"));
        setParamsToPipelineStart();
        return this.pipelineStart;
    }

    private void setParamsToPipelineStart() {
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
        pipelineStart.setParams(params);
    }
}

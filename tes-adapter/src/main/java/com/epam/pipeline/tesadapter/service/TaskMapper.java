package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesInput;
import com.epam.pipeline.tesadapter.entity.TesOutput;
import com.epam.pipeline.tesadapter.entity.TesResources;
import com.epam.pipeline.tesadapter.entity.TesState;
import com.epam.pipeline.tesadapter.entity.TesTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskMapper {
    private final String defaultInstanceType;

    private final Integer defaultHddSize;

    @Autowired
    private MessageHelper messageHelper;

    private static final String SEPARATOR = " ";
    private static final String INPUT_TYPE = "input";
    private static final String OUTPUT_TYPE = "output";
    private static final String DEFAULT_TYPE = "string";
    private static final Integer FIRST = 0;

    public TaskMapper(@Value("${cloud.pipeline.instanceType}") String instanceType,
                      @Value("${cloud.pipeline.hddSize}") Integer hddSize) {
        this.defaultInstanceType = instanceType;
        this.defaultHddSize = hddSize;
    }

    public PipelineStart mapToPipelineStart(TesTask tesTask) {
        PipelineStart pipelineStart = new PipelineStart();
        Map<String, PipeConfValueVO> params = new HashMap<>();
        TesExecutor tesExecutor = getExecutorFromTesExecutorsList(tesTask.getExecutors());

        pipelineStart.setCmdTemplate(String.join(SEPARATOR, tesExecutor.getCommand()));
        pipelineStart.setDockerImage(tesExecutor.getImage());
        pipelineStart.setExecutionEnvironment(ExecutionEnvironment.CLOUD_PLATFORM);
        pipelineStart.setHddSize(defaultHddSize);
        pipelineStart.setInstanceType(defaultInstanceType);
        pipelineStart.setIsSpot(tesTask.getResources().getPreemptible());
        pipelineStart.setForce(false);
        pipelineStart.setNonPause(true);
        ListUtils.emptyIfNull(tesTask.getInputs()).forEach(tesInput ->
                params.put(tesInput.getName(), new PipeConfValueVO(tesInput.getUrl(), INPUT_TYPE)));
        ListUtils.emptyIfNull(tesTask.getOutputs()).forEach(tesOutput ->
                params.put(tesOutput.getName(), new PipeConfValueVO(tesOutput.getUrl(), OUTPUT_TYPE)));
        MapUtils.emptyIfNull(tesExecutor.getEnv()).forEach((name, value) ->
                params.put(name, new PipeConfValueVO(value, DEFAULT_TYPE)));
        pipelineStart.setParams(params);
        return pipelineStart;
    }

    private TesExecutor getExecutorFromTesExecutorsList(List<TesExecutor> tesExecutors) {
        Assert.notEmpty(tesExecutors, messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY,
                "executors"));
        return tesExecutors.get(FIRST);
    }

    public TesTask mapToTesTask(PipelineRun run) {
        final TesTask tesTask = new TesTask();
        tesTask.setId(String.valueOf(run.getId()));
        tesTask.setState(createTesState(run.getStatus()));
        tesTask.setName(run.getPodId());
        tesTask.setResources(createTesResources(run));
        tesTask.setExecutors(createListExecutor(run));
        tesTask.setInputs(createTesInput(ListUtils.emptyIfNull(run.getPipelineRunParameters())));
        tesTask.setOutputs(createTesOutput(ListUtils.emptyIfNull(run.getPipelineRunParameters())));
        tesTask.setCreationTime(run.getStartDate().toString());
        return tesTask;
    }

    private TesState createTesState(TaskStatus status){
        TesState tesStatus = TesState.UNKNOWN;
        switch (status){
            case RUNNING:
                tesStatus = TesState.RUNNING;
                break;
            case PAUSED:
                tesStatus = TesState.PAUSED;
                break;
            case SUCCESS:
                tesStatus = TesState.COMPLETE;
                break;
            case FAILURE:
                tesStatus =  TesState.EXECUTOR_ERROR;
                break;
            case STOPPED:
                tesStatus =  TesState.CANCELED;
                break;
        }
        return tesStatus;
    }

    private List<TesInput> createTesInput(List<PipelineRunParameter> parameters){
        final TesInput tesInput = new TesInput();
        parameters.stream()
                .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(INPUT_TYPE))
                .peek(pipelineRunParameter -> {
                    tesInput.setName(pipelineRunParameter.getName());
                    tesInput.setUrl(pipelineRunParameter.getValue());
                });
        return ListUtils.emptyIfNull(Arrays.asList(tesInput));
    }

    private List<TesOutput> createTesOutput(List<PipelineRunParameter> parameters){
        final TesOutput tesOutput = new TesOutput();
        parameters.stream()
                .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(OUTPUT_TYPE))
                .peek(pipelineRunParameter -> {
                    tesOutput.setName(pipelineRunParameter.getName());
                    tesOutput.setUrl(pipelineRunParameter.getValue());
                });
        return ListUtils.emptyIfNull(Arrays.asList(tesOutput));
    }

    private TesResources createTesResources(PipelineRun run){
        final TesResources tesResources = new TesResources();
        tesResources.setPreemptible(run.getInstance().getSpot());
        tesResources.setDiskGb(new Double(run.getInstance().getNodeDisk()));
        return tesResources;
    }

    private List<TesExecutor> createListExecutor(PipelineRun run){
        final TesExecutor tesExecutor = new TesExecutor();
        tesExecutor.setCommand(ListUtils.emptyIfNull(Arrays.asList(run.getActualCmd().split(SEPARATOR))));
        tesExecutor.setEnv(run.getEnvVars());
        return ListUtils.emptyIfNull(Arrays.asList(tesExecutor));
    }
}

package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
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
    @Autowired
    private CloudPipelineAPIClient cloudPipelineAPIClient;
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
        return TesTask.builder()
                .id(String.valueOf(run.getId()))
                .name(run.getPodId())
                .resources(createTesResources(run))
                .executors(createListExecutor(run))
                .inputs(createTesInput(ListUtils.emptyIfNull(run.getPipelineRunParameters())))
                .outputs(createTesOutput(ListUtils.emptyIfNull(run.getPipelineRunParameters())))
                .creationTime(run.getStartDate().toString())
                .state(createTesState(run))
                .build();
    }

    private TesState createTesState(PipelineRun run) {
        List<PipelineTask> pipelineTaskList = cloudPipelineAPIClient.loadPipelineTasks(run.getId());
        for(PipelineTask p: pipelineTaskList){
            if((p.getName().equalsIgnoreCase("Console")) && (pipelineTaskList.size() == 1)){
                return TesState.QUEUED;
            } else if( (p.getName().equalsIgnoreCase("Console")) && (pipelineTaskList.size() > 1) &&
                    (!p.getName().equalsIgnoreCase("InitializeEnvironment"))){
                return TesState.INITIALIZING;
            }
        }
        switch (run.getStatus()) {
            case RUNNING:
                return TesState.RUNNING;
            case PAUSED:
                return TesState.PAUSED;
            case SUCCESS:
                return TesState.COMPLETE;
            case FAILURE:
                return TesState.EXECUTOR_ERROR;
            case STOPPED:
                return TesState.CANCELED;
            default:
                return TesState.UNKNOWN;
        }
    }

    private List<TesInput> createTesInput(List<PipelineRunParameter> parameters) {
        final TesInput tesInput = new TesInput();
        parameters.stream()
                .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(INPUT_TYPE))
                .forEach(pipelineRunParameter -> {
                    tesInput.setName(pipelineRunParameter.getName());
                    tesInput.setUrl(pipelineRunParameter.getValue());
                });
        return ListUtils.emptyIfNull(Arrays.asList(tesInput));
    }

    private List<TesOutput> createTesOutput(List<PipelineRunParameter> parameters) {
        final TesOutput tesOutput = new TesOutput();
        parameters.stream()
                .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(OUTPUT_TYPE))
                .forEach(pipelineRunParameter -> {
                    tesOutput.setName(pipelineRunParameter.getName());
                    tesOutput.setUrl(pipelineRunParameter.getValue());
                });
        return ListUtils.emptyIfNull(Arrays.asList(tesOutput));
    }

    private TesResources createTesResources(PipelineRun run){
        return TesResources.builder()
                .preemptible(run.getInstance().getSpot())
                .diskGb(new Double(run.getInstance().getNodeDisk()))
                .build();
    }

    private List<TesExecutor> createListExecutor(PipelineRun run){
        return ListUtils.emptyIfNull(Arrays.asList(TesExecutor.builder()
                .command(ListUtils.emptyIfNull(Arrays.asList(run.getActualCmd().split(SEPARATOR))))
                .env(run.getEnvVars())
                .build()));
    }
}

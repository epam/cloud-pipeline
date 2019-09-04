package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesInput;
import com.epam.pipeline.tesadapter.entity.TesOutput;
import com.epam.pipeline.tesadapter.entity.TesResources;
import com.epam.pipeline.tesadapter.entity.TesState;
import com.epam.pipeline.tesadapter.entity.TesTask;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class TaskMapper {
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";

    public TesTask mapToTesTask(PipelineRun run) {
        TesTask tesTask = new TesTask();
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
                .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(INPUT))
                .peek(pipelineRunParameter -> {
                    tesInput.setName(pipelineRunParameter.getName());
                    tesInput.setUrl(pipelineRunParameter.getValue());
                });
        return ListUtils.emptyIfNull(Arrays.asList(tesInput));
    }

    private List<TesOutput> createTesOutput(List<PipelineRunParameter> parameters){
        final TesOutput tesOutput = new TesOutput();
        parameters.stream()
                .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(OUTPUT))
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
        tesExecutor.setCommand(ListUtils.emptyIfNull(Arrays.asList(run.getActualCmd().split(" "))));
        tesExecutor.setEnv(run.getEnvVars());
        return ListUtils.emptyIfNull(Arrays.asList(tesExecutor));
    }
}

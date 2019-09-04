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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TaskMapper {
    public TesTask mapToTesTask(PipelineRun run) {
        TesTask tesTask = new TesTask();
        tesTask.setId(String.valueOf(run.getId()));
        tesTask.setState(createTesState(run.getStatus()));
        tesTask.setName(run.getPodId());
        tesTask.setDescription("");
        tesTask.setResources(createTesResources(run));
        tesTask.setExecutors(createListExecutor(run));
//        tesTask.setVolumes();  skip
//        tesTask.setTags();   skip
//        tesTask.setLogs();  skip
        tesTask.setInputs(createTesInput(ListUtils.emptyIfNull(run.getPipelineRunParameters())));
        tesTask.setOutputs(createTesOutput(ListUtils.emptyIfNull(run.getPipelineRunParameters())));
        tesTask.setCreationTime(run.getStartDate().toString());
        return tesTask;
    }

    private TesState createTesState(TaskStatus status){
        TesState tesStatus = null;
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
        TesInput tesInput = new TesInput();
        Stream<TesInput> stream = Stream.of(tesInput)
                .peek(i -> {
                    Predicate<PipelineRunParameter> predicate = (s -> s.getType().contains("input"));
                    Stream<PipelineRunParameter> pipelineRunParameterStream =  parameters.stream()
                            .filter(predicate)
                            .peek(pipelineRunParameter -> {
                                tesInput.setName(pipelineRunParameter.getName());
                                tesInput.setUrl(pipelineRunParameter.getValue());
                            });
                });
        return stream.collect(Collectors.toList());
    }

    private List<TesOutput> createTesOutput(List<PipelineRunParameter> parameters){
        TesOutput tesOutput = new TesOutput();
        Stream<TesOutput> stream = Stream.of(tesOutput)
                .peek(i -> {
                    Predicate<PipelineRunParameter> predicate = (s -> s.getType().contains("output"));
                    Stream<PipelineRunParameter> pipelineRunParameterStream =  parameters.stream()
                            .filter(predicate)
                            .peek(pipelineRunParameter -> {
                                tesOutput.setName(pipelineRunParameter.getName());
                                tesOutput.setUrl(pipelineRunParameter.getValue());
                            });
                });
        return stream.collect(Collectors.toList());
    }

    private TesResources createTesResources(PipelineRun run){
        TesResources tesResources = new TesResources();
//            tesResources.setCpuCores();   skip, later get from instance.nodeType
        tesResources.setPreemptible(run.getInstance().getSpot());
//            tesResources.setRamGb(); skip, later get from instance.nodeType
        tesResources.setDiskGb(new Double(run.getInstance().getNodeDisk()));
//            tesResources.setZones();  skip, later get from region
        return tesResources;
    }

    private List<TesExecutor> createListExecutor(PipelineRun run){
        TesExecutor tesExecutor = new TesExecutor();
        tesExecutor.setCommand(Arrays.asList(run.getActualCmd().split(" ")));
        tesExecutor.setWorkdir("");
        tesExecutor.setStdin("");
        tesExecutor.setStdout("");
        tesExecutor.setStderr("");
        tesExecutor.setEnv(run.getEnvVars());
        return ListUtils.emptyIfNull(Arrays.asList(tesExecutor));
    }
}

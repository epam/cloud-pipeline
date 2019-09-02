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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

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
        tesTask.setInputs(createTesInput(run.getPipelineRunParameters()));
        tesTask.setOutputs(createTesOutput(run.getPipelineRunParameters()));
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
        List<TesInput> listTesInput = new ArrayList<>();
        Predicate<PipelineRunParameter> predicate = (s -> s.getType().contains("input"));
        parameters.stream().filter(predicate).forEach(pipelineRunParameter -> {
            TesInput tesInput = new TesInput();
            tesInput.setName(pipelineRunParameter.getName());
            tesInput.setUrl(pipelineRunParameter.getValue());
            listTesInput.add(tesInput);
        });
        return listTesInput;
    }

    private List<TesOutput> createTesOutput(List<PipelineRunParameter> parameters){
        List<TesOutput> listTesOutput = new ArrayList<>();
        Predicate<PipelineRunParameter> pipelineRunParameterPredicate = (s) -> s.getType().equals("output");
        parameters.stream().filter(pipelineRunParameterPredicate).forEach(pipelineRunParameter -> {
            TesOutput output = new TesOutput();
            output.setName(pipelineRunParameter.getName());
            output.setUrl(pipelineRunParameter.getValue());
            listTesOutput.add(output);
        });
        return listTesOutput;
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
        List<TesExecutor> tesExecutorList = new ArrayList<>();
        TesExecutor tesExecutor = new TesExecutor();
        tesExecutor.setCommand(new ArrayList<String>(Arrays.asList(run.getActualCmd().split(" "))));
        tesExecutor.setWorkdir("");
        tesExecutor.setStdin("");
        tesExecutor.setStdout("");
        tesExecutor.setStderr("");
        tesExecutor.setEnv(run.getEnvVars());
        tesExecutorList.add(tesExecutor);
        return tesExecutorList;
    }
}

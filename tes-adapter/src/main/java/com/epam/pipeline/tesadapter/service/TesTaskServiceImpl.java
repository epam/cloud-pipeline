package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.vo.RunStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
public class TesTaskServiceImpl implements TesTaskService {
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final TaskMapper taskMapper;

    @Autowired
    public TesTaskServiceImpl(CloudPipelineAPIClient cloudPipelineAPIClient, TaskMapper taskMapper) {
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.taskMapper = taskMapper;
    }

    @Override
    public TesCreateTaskResponse submitTesTask(TesTask body) {
        TesCreateTaskResponse tesCreateTaskResponse = new TesCreateTaskResponse();
        PipelineRun pipelineRun = cloudPipelineAPIClient.runPipeline(taskMapper.mapToPipelineStart(body));
        Assert.notNull(pipelineRun.getId(), "INVALID PIPELINE ID");
        tesCreateTaskResponse.setId("" + pipelineRun.getId());
        return tesCreateTaskResponse;
    }

    @Override
    public TesListTasksResponse listTesTask() {
        return new TesListTasksResponse();
    }

    @Override
    public void stub() {
        //stubbed method
    }

    @Override
    public TesCancelTaskResponse cancelTesTask(String id) {
        RunStatusVO updateStatus = new RunStatusVO();
        updateStatus.setStatus(TaskStatus.STOPPED);
        cloudPipelineAPIClient.updateRunStatus(parseRunId(id), updateStatus);
        return new TesCancelTaskResponse();
    }

    private Long parseRunId(String id) {
        Assert.hasText(id, "INVALID RUN ID");
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            log.error("INVALID RUN ID");
            throw new IllegalArgumentException(e);
        }
    }
}

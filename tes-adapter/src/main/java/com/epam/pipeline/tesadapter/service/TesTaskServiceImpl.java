package com.epam.pipeline.tesadapter.service;


import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
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
    private TaskMapper taskMapper;

    private final TaskMapper taskMapper;

    private final MessageHelper messageHelper;

    private final static String ID = "id";

    @Autowired
    public TesTaskServiceImpl(CloudPipelineAPIClient cloudPipelineAPIClient, TaskMapper taskMapper,
                              MessageHelper messageHelper) {
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.taskMapper = taskMapper;
        this.messageHelper = messageHelper;
    }

    @Override
    public TesCreateTaskResponse submitTesTask(TesTask body) {
        TesCreateTaskResponse tesCreateTaskResponse = new TesCreateTaskResponse();
        PipelineRun pipelineRun = cloudPipelineAPIClient.runPipeline(taskMapper.mapToPipelineStart(body));
        Assert.notNull(pipelineRun.getId(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                ID, TesCreateTaskResponse.class.getSimpleName()));
        tesCreateTaskResponse.setId(String.valueOf(pipelineRun.getId()));
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

    @Override
    public TesTask getTesTask(Long id) {
        return taskMapper.mapToTesTask(cloudPipelineAPIClient.loadPipelineRun(id));

    }

    private Long parseRunId(String id) {
        Assert.hasText(id, messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NON_SCALAR_TYPE,
                ID, id));
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            log.error(messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT,
                    ID, id));
            throw new IllegalArgumentException(e);
        }
    }
}

package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.vo.RunStatusVO;
import org.springframework.beans.factory.annotation.Autowired;
import com.epam.pipeline.tesadapter.entity.TesTask;
import org.springframework.stereotype.Service;

@Service
public class TesTaskServiceImpl implements TesTaskService {
    private final CloudPipelineAPIClient cloudPipelineAPIClient;

    @Autowired
    public TesTaskServiceImpl(CloudPipelineAPIClient cloudPipelineAPIClient) {
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
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
    public TesTask cancelTesTask(String id) {
        RunStatusVO updateStatus = new RunStatusVO();
        updateStatus.setStatus(TaskStatus.STOPPED);
        cloudPipelineAPIClient.updateRunStatus(Long.parseLong(id), updateStatus);

        return null;
    }
}

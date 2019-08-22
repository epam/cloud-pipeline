package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
}

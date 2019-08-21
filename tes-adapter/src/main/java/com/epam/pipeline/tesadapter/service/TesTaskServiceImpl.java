package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import org.springframework.stereotype.Service;

@Service
public class TesTaskServiceImpl implements TesTaskService {
    @Override
    public TesListTasksResponse listTesTask() {
        TesListTasksResponse tesListTasksResponse = new TesListTasksResponse();
        return new TesListTasksResponse();
    }

    @Override
    public void stub() {
        //stubbed method
    }
}

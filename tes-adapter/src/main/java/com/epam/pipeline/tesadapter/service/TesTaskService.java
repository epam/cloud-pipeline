package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesTask;

public interface TesTaskService {

    TesListTasksResponse listTesTask();
    void stub();

    TesTask cancelTesTask(String id);
}

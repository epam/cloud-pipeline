package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesTask;

public interface TesTaskService {

    TesListTasksResponse listTesTask();
    void stub();

    TesCancelTaskResponse cancelTesTask(String id);

    TesTask getTesTask(Long id);
}

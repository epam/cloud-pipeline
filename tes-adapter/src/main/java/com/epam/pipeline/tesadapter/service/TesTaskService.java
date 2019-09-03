package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesServiceInfo;

public interface TesTaskService {

    TesListTasksResponse listTesTask();
    void stub();

    TesCancelTaskResponse cancelTesTask(String id);

    TesServiceInfo getServiceInfo(String nameOfService, String doc);
}

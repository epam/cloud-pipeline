package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.tesadapter.entity.TaskView;
import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesServiceInfo;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;

public interface TesTaskService {

    TesCreateTaskResponse submitTesTask(TesTask body);

    TesListTasksResponse listTesTask(String namePrefix, Long pageSize, String pageToken, TaskView view);

    TesCancelTaskResponse cancelTesTask(String id);

    TesTask getTesTask(String id, TaskView view);

    TesServiceInfo getServiceInfo();
}

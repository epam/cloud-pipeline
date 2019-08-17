package com.epam.pipeline.tesadapter.controller;

import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.tesadapter.service.TesTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TesApiController implements TesApi{
    @Autowired
    private TesTaskService tesTaskService;

    @Override
    public ResponseEntity<TesCreateTaskResponse> submitTesTask(TesTask body) {
        return tesTaskService.submitTesTask(new TesTask());
    }

    @Override
    public ResponseEntity<TesTask> getTesTask(String id) {
        return tesTaskService.getTesTask("STUBBED");
    }

    @Override
    public ResponseEntity<TesCancelTaskResponse> cancelTesTask(String id) {
        return tesTaskService.cancelTesTask("STUBBED");
    }
}

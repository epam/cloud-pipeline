package com.epam.pipeline.tesadapter.controller;

import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.tesadapter.service.TesTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class TesAdapterController {

    private TesTaskService tesTaskService;

    @Autowired
    public TesAdapterController(TesTaskService tesTaskService) {
        this.tesTaskService = tesTaskService;
    }

    @GetMapping("/v1/tasks/service-info")
    public ResponseEntity<String> serviceInfo() {
        tesTaskService.stub();
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    @PostMapping("/v1/tasks")
    @ResponseBody
    ResponseEntity<TesCreateTaskResponse> submitTesTask(@RequestBody TesTask body) {
        tesTaskService.stub();
        return new ResponseEntity<TesCreateTaskResponse>(new TesCreateTaskResponse(), HttpStatus.NOT_IMPLEMENTED);
    }

    @GetMapping("/v1/tasks/{id}")
    @ResponseBody
    ResponseEntity<TesTask> getTesTask(@PathVariable("id") String id) {
        tesTaskService.stub();
        return new ResponseEntity<TesTask>(new TesTask(), HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/v1/tasks/{id}:cancel")
    @ResponseBody
    ResponseEntity<TesCancelTaskResponse> cancelTesTask(@PathVariable("id") String id) {
        tesTaskService.stub();
        return new ResponseEntity<TesCancelTaskResponse>(new TesCancelTaskResponse(), HttpStatus.NOT_IMPLEMENTED);
    }
}

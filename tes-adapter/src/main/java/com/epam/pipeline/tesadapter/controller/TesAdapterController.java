package com.epam.pipeline.tesadapter.controller;

import com.epam.pipeline.tesadapter.entity.TaskView;
import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesServiceInfo;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.tesadapter.service.TesTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class TesAdapterController {
    private TesTaskService tesTaskService;

    @Autowired
    public TesAdapterController(TesTaskService tesTaskService) {
        this.tesTaskService = tesTaskService;
    }

    @GetMapping("/v1/tasks/service-info")
    public ResponseEntity<TesServiceInfo> serviceInfo() {
        return ResponseEntity.status(HttpStatus.OK).body(tesTaskService.getServiceInfo());
    }

    @GetMapping("/v1/tasks")
    public ResponseEntity<TesListTasksResponse> listTesTasks(
            @RequestParam(name = "name_prefix", required = false) String namePrefix,
            @RequestParam(name = "page_size", required = false) Long pageSize,
            @RequestParam(name = "page_token", required = false) String pageToken,
            @RequestParam(name = "view", required = false, defaultValue = "MINIMAL") TaskView view) {
        return ResponseEntity.status(HttpStatus.OK).body(tesTaskService.listTesTask(namePrefix, pageSize, pageToken,
                view));
    }

    @PostMapping("/v1/tasks")
    public ResponseEntity<TesCreateTaskResponse> submitTesTask(@RequestBody TesTask body) {
        return ResponseEntity.status(HttpStatus.OK).body(tesTaskService.submitTesTask(body));
    }

    @GetMapping("/v1/tasks/{id}")
    public ResponseEntity<TesTask> getTesTask(@PathVariable String id, @RequestParam(required = false,
            defaultValue = "MINIMAL") TaskView view) {
        return ResponseEntity.ok().body(tesTaskService.getTesTask(id, view));
    }

    @PostMapping("/v1/tasks/{id}:cancel")
    public ResponseEntity<TesCancelTaskResponse> cancelTesTask(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.OK).body(tesTaskService.cancelTesTask(id));
    }
}

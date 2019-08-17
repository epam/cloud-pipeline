package com.epam.pipeline.tesadapter.controller;

import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

public interface TesApi {

    @PostMapping("/v1/tasks")
    @ResponseBody ResponseEntity<TesCreateTaskResponse> submitTesTask(@RequestBody TesTask body);

    @GetMapping("/v1/tasks/{id}")
    @ResponseBody ResponseEntity<TesTask> getTesTask(@PathVariable("id") String id);

    @PostMapping("/v1/tasks/{id}:cancel")
    @ResponseBody ResponseEntity<TesCancelTaskResponse> cancelTesTask(@PathVariable("id") String id);
}

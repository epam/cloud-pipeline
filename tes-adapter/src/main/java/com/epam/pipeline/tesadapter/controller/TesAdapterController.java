package com.epam.pipeline.tesadapter.controller;

import com.epam.pipeline.tesadapter.service.TesTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TesAdapterController {
    @Autowired
    private TesTaskService tesTaskService;

    @GetMapping("/v1/tasks/service-info")
    public ResponseEntity<String> serviceInfo() {
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }
}

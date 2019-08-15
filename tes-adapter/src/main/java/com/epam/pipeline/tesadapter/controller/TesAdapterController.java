package com.epam.pipeline.tesadapter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TesAdapterController {
    @RequestMapping(method = RequestMethod.GET, value = "/v1/tasks/service-info")
    public ResponseEntity<String> serviceInfo() {
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }
}

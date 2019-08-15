package com.epam.pipeline.tesadapter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TesAdapterController {

    @GetMapping("/v1/tasks/service-info")
    public ResponseEntity<String> serviceInfo(
            @RequestParam(name = "status", required = false, defaultValue = "OK") String status){
        if (status.equalsIgnoreCase("ok")){
            return new ResponseEntity<>("The system is fully operational!", HttpStatus.OK);
        }
        return new ResponseEntity<>("The system is not operable!", HttpStatus.NOT_FOUND);
    }
}

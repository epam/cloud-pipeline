package com.epam.pipeline.adaptor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TesAdapterController {

    @GetMapping("/v1/tasks/service-info")
    public String getSimpleResponse(){
        return "OK";
    }
}

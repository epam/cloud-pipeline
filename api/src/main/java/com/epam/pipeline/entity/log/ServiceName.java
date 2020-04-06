package com.epam.pipeline.entity.log;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum ServiceName {
    API("api-srv"), EDGE("edge");

    private final String service;
    private static Map<String, ServiceName>  byService = new HashMap<>();

    static {
        byService.put(API.service, API);
        byService.put(EDGE.service, EDGE);
    }

    public static ServiceName getByService(String service) {
        ServiceName result = byService.get(service);
        if (result == null) {
            throw new IllegalArgumentException("Wrong service name: " + service);
        }
        return result;
    }

    ServiceName(String service) {
        this.service = service;
    }


}

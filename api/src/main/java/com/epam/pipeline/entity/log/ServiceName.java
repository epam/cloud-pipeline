package com.epam.pipeline.entity.log;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum ServiceName {
    API("api-srv"), EDGE("edge");

    private final String service;
    private static final Map<String, ServiceName> BY_SERVICE = new HashMap<>();

    static {
        BY_SERVICE.put(API.service, API);
        BY_SERVICE.put(EDGE.service, EDGE);
    }

    public static ServiceName getBY_SERVICE(String service) {
        ServiceName result = BY_SERVICE.get(service);
        if (result == null) {
            throw new IllegalArgumentException("Wrong service name: " + service);
        }
        return result;
    }

    ServiceName(String service) {
        this.service = service;
    }


}

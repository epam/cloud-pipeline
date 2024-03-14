package com.epam.pipeline.entity.region;

import lombok.Data;

import java.util.Map;

@Data
public class AWSOmicsServiceProperties {
    private final Map<String, String> properties;
}

package com.epam.pipeline.entity.region;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class StorageLifecycleServiceProperties {
    private final Map<String, String> properties = new HashMap<>();
}

package com.epam.pipeline.entity.region;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor(force = true)
public class StorageLifecycleServiceProperties {
    private final Map<String, String> properties;
}

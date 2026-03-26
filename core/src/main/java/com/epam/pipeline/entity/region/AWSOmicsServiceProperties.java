package com.epam.pipeline.entity.region;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor(force = true)
public class AWSOmicsServiceProperties {
    private final Map<String, String> properties;
}

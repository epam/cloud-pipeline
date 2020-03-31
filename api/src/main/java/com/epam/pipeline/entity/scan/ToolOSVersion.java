package com.epam.pipeline.entity.scan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class ToolOSVersion {
    private final String distribution;
    private final String version;
    private Boolean isAllowed;
}

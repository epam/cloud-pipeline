package com.epam.pipeline.entity.pipeline;

import lombok.Value;

@Value
public class DiskAttachRequest {
    private final Long size;
    private final String device;
}

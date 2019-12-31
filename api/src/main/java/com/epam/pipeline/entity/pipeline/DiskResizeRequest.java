package com.epam.pipeline.entity.pipeline;

import lombok.Value;

@Value
public class DiskResizeRequest {
    private final Long size;
}

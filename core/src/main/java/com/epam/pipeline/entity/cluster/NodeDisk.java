package com.epam.pipeline.entity.cluster;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class NodeDisk {
    private final Long size;
    private final String nodeId;
    private final LocalDateTime createdDate;
}

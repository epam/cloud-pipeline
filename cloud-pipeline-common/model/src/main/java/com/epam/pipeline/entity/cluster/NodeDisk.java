package com.epam.pipeline.entity.cluster;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class NodeDisk {
    Long size;
    String nodeId;
    LocalDateTime createdDate;
}

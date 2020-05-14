package com.epam.pipeline.entity.cluster;

import lombok.Value;
import lombok.experimental.Wither;

import java.time.LocalDateTime;

@Value
@Wither
public class NodeDisk {
    private final Long size;
    private final String nodeId;
    private final LocalDateTime createdDate;
}

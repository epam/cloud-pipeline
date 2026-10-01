package com.epam.pipeline.entity.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class NodeDisk {
    Long size;
    String nodeId;
    LocalDateTime createdDate;

    @JsonCreator
    public NodeDisk(
            @JsonProperty("size") final Long size,
            @JsonProperty("nodeId") final String nodeId,
            @JsonProperty("createdDate") final LocalDateTime createdDate) {
        this.size = size;
        this.nodeId = nodeId;
        this.createdDate = createdDate;
    }
}

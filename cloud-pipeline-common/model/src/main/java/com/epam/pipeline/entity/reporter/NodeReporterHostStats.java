package com.epam.pipeline.entity.reporter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
public class NodeReporterHostStats {
    String name;
    LocalDateTime timestamp;
    List<NodeReporterProcessStats> processes;

    @JsonCreator
    public NodeReporterHostStats(
            @JsonProperty("name") final String name,
            @JsonProperty("timestamp") final LocalDateTime timestamp,
            @JsonProperty("processes") final List<NodeReporterProcessStats> processes) {
        this.name = name;
        this.timestamp = timestamp;
        this.processes = processes;
    }
}

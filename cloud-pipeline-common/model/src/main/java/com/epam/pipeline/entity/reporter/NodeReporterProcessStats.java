package com.epam.pipeline.entity.reporter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Map;

@Value
public class NodeReporterProcessStats {
    int pid;
    String name;
    Map<NodeReporterStatsType, NodeReporterStatsLimit> limits;
    Map<NodeReporterStatsType, NodeReporterStatsValue> stats;

    @JsonCreator
    public NodeReporterProcessStats(
            @JsonProperty("pid") final int pid,
            @JsonProperty("name") final String name,
            @JsonProperty("limits") final Map<NodeReporterStatsType, NodeReporterStatsLimit> limits,
            @JsonProperty("stats") final Map<NodeReporterStatsType, NodeReporterStatsValue> stats) {
        this.pid = pid;
        this.name = name;
        this.limits = limits;
        this.stats = stats;
    }
}

package com.epam.pipeline.entity.reporter;

import lombok.Value;

import java.util.Map;

@Value
public class NodeReporterProcessStats {
    int pid;
    String name;
    Map<NodeReporterStatsType, NodeReporterStatsLimit> limits;
    Map<NodeReporterStatsType, NodeReporterStatsValue> stats;
}

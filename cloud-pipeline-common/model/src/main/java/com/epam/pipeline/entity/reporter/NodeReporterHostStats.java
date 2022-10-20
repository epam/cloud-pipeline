package com.epam.pipeline.entity.reporter;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
public class NodeReporterHostStats {
    String name;
    LocalDateTime timestamp;
    List<NodeReporterProcessStats> processes;
}

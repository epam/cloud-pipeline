package com.epam.pipeline.entity.reporter;

import lombok.Value;

@Value
public class NodeReporterStatsLimit {
    int softLimit;
    int hardLimit;
}

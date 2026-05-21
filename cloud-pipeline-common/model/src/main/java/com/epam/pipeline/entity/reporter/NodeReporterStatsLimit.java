package com.epam.pipeline.entity.reporter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class NodeReporterStatsLimit {
    int softLimit;
    int hardLimit;

    @JsonCreator
    public NodeReporterStatsLimit(
            @JsonProperty("softLimit") final int softLimit,
            @JsonProperty("hardLimit") final int hardLimit) {
        this.softLimit = softLimit;
        this.hardLimit = hardLimit;
    }
}

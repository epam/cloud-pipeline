package com.epam.pipeline.entity.reporter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class NodeReporterStatsValue {
    int value;

    @JsonCreator
    public NodeReporterStatsValue(@JsonProperty("value") final int value) {
        this.value = value;
    }
}

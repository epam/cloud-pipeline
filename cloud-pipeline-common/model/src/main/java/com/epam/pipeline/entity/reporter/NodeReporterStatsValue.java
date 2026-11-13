package com.epam.pipeline.entity.reporter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * The current value of a process stat, e.g. its number of open files. It is {@code long}, as the limits it is
 * compared with.
 */
@Value
public class NodeReporterStatsValue {
    long value;

    @JsonCreator
    public NodeReporterStatsValue(@JsonProperty("value") final long value) {
        this.value = value;
    }
}

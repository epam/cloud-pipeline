package com.epam.pipeline.entity.reporter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * The soft and hard limits of a process, as the node reporter sends them. They are {@code long}: for the host, the
 * node reporter sends {@code fs.file-max}, which is 9223372036854775807 (LONG_MAX) when systemd 240 or later sets it,
 * e.g. on Amazon Linux 2023 and Rocky Linux 9.
 */
@Value
public class NodeReporterStatsLimit {
    long softLimit;
    long hardLimit;

    @JsonCreator
    public NodeReporterStatsLimit(
            @JsonProperty("soft_limit") final long softLimit,
            @JsonProperty("hard_limit") final long hardLimit) {
        this.softLimit = softLimit;
        this.hardLimit = hardLimit;
    }
}

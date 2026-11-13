package com.epam.pipeline.entity.reporter;

import lombok.Value;

/**
 * The current value of a process stat, e.g. its number of open files. It is {@code long}, as the limits it is
 * compared with.
 */
@Value
public class NodeReporterStatsValue {
    long value;
}

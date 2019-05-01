package com.epam.pipeline.entity.cluster.monitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ELKUsageMetric {

    CPU("cpu", "CpuMetricsTimestamp", false),
    MEM("memory", "MemoryMetricsTimestamp", true),
    FS("filesystem", "FilesystemMetricsTimestamp", true);

    private final String name;
    private final String timestamp;
    private boolean nodeMetric;

}

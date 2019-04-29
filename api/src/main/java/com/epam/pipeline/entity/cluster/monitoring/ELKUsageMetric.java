package com.epam.pipeline.entity.cluster.monitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ELKUsageMetric {

    CPU("cpu", "CpuMetricsTimestamp"),
    MEM("memory", "MemoryMetricsTimestamp"),
    FS("filesystem", "FilesystemMetricsTimestamp");

    private final String name;
    private final String timestamp;

}

package com.epam.pipeline.entity.cluster.monitoring;

import lombok.Getter;

@Getter
public enum ELKUsageMetric {

    CPU("cpu", "CpuMetricsTimestamp"),
    MEM("memory", "MemoryMetricsTimestamp"),
    FS("filesystem", "FilesystemMetricsTimestamp");

    ELKUsageMetric(String name, String timestamp) {
        this.name = name;
        this.timestamp = timestamp;
    }

    private final String name;
    private final String timestamp;

}

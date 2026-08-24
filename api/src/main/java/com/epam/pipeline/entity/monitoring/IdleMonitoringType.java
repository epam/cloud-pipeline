package com.epam.pipeline.entity.monitoring;

import lombok.Getter;

public enum IdleMonitoringType {
    CPU("IDLE_CPU", 23),
    GPU("IDLE_GPU", 24),
    ABSOLUTE("IDLE", 6);

    @Getter
    private final String tag;

    @Getter
    private final int notificationTypeId;

    IdleMonitoringType(final String tag, final int notificationTypeId) {
        this.tag = tag;
        this.notificationTypeId = notificationTypeId;
    }
}

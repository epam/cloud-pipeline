package com.epam.pipeline.entity.monitoring;

import com.epam.pipeline.entity.notification.NotificationType;
import lombok.Getter;

public enum IdleMonitoringType {
    CPU("IDLE_CPU", NotificationType.IDLE_CPU_RUN),
    GPU("IDLE_GPU", NotificationType.IDLE_GPU_RUN),
    ABSOLUTE("IDLE", NotificationType.IDLE_RUN);

    @Getter
    private final String tag;

    @Getter
    private final NotificationType notificationType;

    IdleMonitoringType(final String tag, final NotificationType notificationType) {
        this.tag = tag;
        this.notificationType = notificationType;
    }
}

package com.epam.pipeline.entity.pipeline.run;

import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.TaskStatus;

import java.util.List;

public record PipelineStartNotificationRequest(
    NotificationType type,
    List<TaskStatus> triggerStatuses,
    List<String> recipients,
    String subject,
    String body) {
}

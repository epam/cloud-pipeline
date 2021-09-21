package com.epam.pipeline.entity.pipeline.run;

import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import lombok.Value;

import java.util.List;

@Value
public class PipelineStartNotificationRequest {
    NotificationType type;
    List<TaskStatus> triggerStatuses;
    List<String> recipients;
    String subject;
    String body;
}

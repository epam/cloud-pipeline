package com.epam.pipeline.dto.notification;

import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@RequiredArgsConstructor
public class ContextualNotification {
    Long id;
    NotificationType type;
    Long triggerId;
    List<TaskStatus> triggerStatuses;
    List<Long> recipients;
    String subject;
    String body;
    LocalDateTime created;

    public ContextualNotification(final NotificationType type,
                                  final Long triggerId,
                                  final List<TaskStatus> triggerStatuses,
                                  final List<Long> recipients,
                                  final String subject,
                                  final String body) {
        this(null, type, triggerId, triggerStatuses, recipients, subject, body, null);
    }
}

package com.epam.pipeline.dto.notification;

import com.epam.pipeline.entity.notification.NotificationEntityClass;

public record UserNotificationResource(
    Long id,
    NotificationEntityClass entityClass,
    Long entityId,
    String storagePath,
    Long storageRuleId) {
}

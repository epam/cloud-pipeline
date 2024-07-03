package com.epam.pipeline.dto.notification;

import com.epam.pipeline.entity.notification.NotificationEntityClass;
import lombok.Value;

@Value
public class UserNotificationResource {

    Long id;
    NotificationEntityClass entityClass;
    Long entityId;
    String storagePath;
    Long storageRuleId;
}

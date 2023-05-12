package com.epam.pipeline.entity.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationParameter {

    TYPE("notificationType"),
    ENTITIES("notificationEntities");

    private final String key;
}

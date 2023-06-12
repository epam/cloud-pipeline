package com.epam.pipeline.entity.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationParameter {

    TYPE("notificationType"),
    RESOURCES("notificationResources");

    private final String key;
}

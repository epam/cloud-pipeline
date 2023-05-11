package com.epam.pipeline.entity.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum NotificationParameter {

    NOTIFICATION_TYPE("notificationType"),
    LINKED_ENTITY_CLASS("linkedEntityClass"),
    LINKED_ENTITY_ID("linkedEntityId"),
    LINKED_STORAGE_PATH("linkedStoragePath"),
    LINKED_STORAGE_RULE_ID("linkedStorageRuleId");

    private final String name;
}

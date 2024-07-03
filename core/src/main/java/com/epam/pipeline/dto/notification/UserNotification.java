package com.epam.pipeline.dto.notification;

import com.epam.pipeline.entity.notification.NotificationType;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@RequiredArgsConstructor
@Builder(toBuilder = true)
public class UserNotification {

    Long id;
    NotificationType type;
    Long userId;
    String subject;
    String text;
    LocalDateTime createdDate;
    Boolean isRead;
    LocalDateTime readDate;
    List<UserNotificationResource> resources;
}

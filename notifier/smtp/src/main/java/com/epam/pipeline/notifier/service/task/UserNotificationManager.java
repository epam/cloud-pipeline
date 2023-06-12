/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.notifier.service.task;

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationParameter;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.notification.UserNotificationEntity;
import com.epam.pipeline.entity.notification.UserNotificationResourceEntity;
import com.epam.pipeline.notifier.entity.message.MessageText;
import com.epam.pipeline.notifier.repository.UserNotificationRepository;
import com.epam.pipeline.notifier.service.TemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enable.ui", havingValue = "true")
public class UserNotificationManager implements NotificationManager {

    private final UserNotificationRepository notificationRepository;
    private final TemplateService templateService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    @Transactional
    public void notifySubscribers(final NotificationMessage message) {
        notificationRepository.save(toNotifications(message));
    }

    private List<UserNotificationEntity> toNotifications(final NotificationMessage message) {
        final MessageText messageText = templateService.buildMessageText(message);
        final Set<Long> userIds = new HashSet<>();
        if (message.getToUserId() != null) {
            userIds.add(message.getToUserId());
        }
        userIds.addAll(ListUtils.emptyIfNull(message.getCopyUserIds()));
        return SetUtils.emptyIfNull(userIds).stream()
                .map(userId -> toNotification(userId, messageText, message.getTemplateParameters()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private UserNotificationEntity toNotification(final Long userId,
                                                  final MessageText text,
                                                  final Map<String, Object> parameters) {
        final UserNotificationEntity notification = new UserNotificationEntity();
        notification.setUserId(userId);
        notification.setSubject(text.getSubject());
        notification.setText(text.getBody());
        notification.setCreatedDate(LocalDateTime.now());
        notification.setIsRead(false);
        notification.setType(toNotificationType(parameters));
        notification.setResources(toNotificationResources(parameters).stream()
                .peek(resource -> resource.setNotification(notification))
                .collect(Collectors.toList()));
        return notification;
    }

    private NotificationType toNotificationType(final Map<String, Object> parameters) {
        return Optional.ofNullable(parameters.get(NotificationParameter.TYPE.getKey()))
                .map(this::toNotificationType)
                .orElse(null);
    }

    private NotificationType toNotificationType(final Object object) {
        return mapper.convertValue(object, NotificationType.class);
    }

    private List<UserNotificationResourceEntity> toNotificationResources(final Map<String, Object> parameters) {
        return Optional.ofNullable(parameters.get(NotificationParameter.RESOURCES.getKey()))
                .map(this::toNotificationResources)
                .orElseGet(Collections::emptyList);
    }

    private List<UserNotificationResourceEntity> toNotificationResources(final Object object) {
        return mapper.convertValue(object, new TypeReference<List<UserNotificationResourceEntity>>() {});
    }
}

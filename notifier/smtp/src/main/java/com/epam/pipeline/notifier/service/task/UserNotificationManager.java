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
import com.epam.pipeline.entity.notification.UserNotification;
import com.epam.pipeline.notifier.entity.message.MessageText;
import com.epam.pipeline.notifier.repository.UserNotificationRepository;
import com.epam.pipeline.notifier.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserNotificationManager implements NotificationManager {

    @Value(value = "${notification.enable.ui}")
    private boolean isEnabled;

    private final UserNotificationRepository notificationRepository;
    private final TemplateService templateService;

    @Override
    @Transactional
    public void notifySubscribers(final NotificationMessage message) {
        if (!isEnabled) {
            return;
        }
        notificationRepository.save(toUserNotifications(message));
    }

    private List<UserNotification> toUserNotifications(final NotificationMessage message) {
        final MessageText messageText = templateService.buildMessageText(message);
        final Set<Long> userIds = new HashSet<>();
        if (message.getToUserId() != null) {
            userIds.add(message.getToUserId());
        }
        userIds.addAll(ListUtils.emptyIfNull(message.getCopyUserIds()));
        final List<UserNotification> results = new ArrayList<>();
        SetUtils.emptyIfNull(userIds).forEach(userId -> results.add(buildNotification(userId, messageText)));
        return results;
    }

    private static UserNotification buildNotification(final Long userId,
                                                      final MessageText messageText) {
        final UserNotification userNotification = new UserNotification();
        userNotification.setUserId(userId);
        userNotification.setSubject(messageText.getSubject());
        userNotification.setText(messageText.getBody());
        userNotification.setCreatedDate(LocalDateTime.now());
        userNotification.setIsRead(false);
        return userNotification;
    }
}

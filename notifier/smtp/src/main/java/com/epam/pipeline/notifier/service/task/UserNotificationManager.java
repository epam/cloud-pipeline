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
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNotificationManager implements NotificationManager {

    private final UserNotificationRepository notificationRepository;
    private final TemplateService templateService;

    @Override
    @Transactional
    public void notifySubscribers(final NotificationMessage message) {
        notificationRepository.save(toUserNotifications(message));
    }

    private List<UserNotification> toUserNotifications(final NotificationMessage message) {
        final MessageText messageText = templateService.buildMessageText(message);
        final List<UserNotification> results = new ArrayList<>();
        final UserNotification userNotification = buildNotification(message.getToUserId(), messageText);
        results.add(userNotification);
        ListUtils.emptyIfNull(message.getCopyUserIds())
                .forEach(userId -> results.add(buildNotification(userId, messageText)));
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

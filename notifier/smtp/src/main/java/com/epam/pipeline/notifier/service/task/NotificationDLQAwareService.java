/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.notifier.service.task;

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationMessageDlq;
import com.epam.pipeline.notifier.repository.NotificationDlqRepository;
import com.epam.pipeline.notifier.util.MessageToDlqConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public class NotificationDLQAwareService implements NotificationManager {
    private final NotificationManager notificationManager;

    @Autowired
    private NotificationDlqRepository notificationDlqRepository;

    @Autowired
    private MessageToDlqConverter messageToDlqConverter;

    public NotificationDLQAwareService(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
        log.info("NotificationAware wrapping: {}", notificationManager.getClass().getSimpleName());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifySubscribers(NotificationMessage message) {
        final Long messageId = message.getId();
        try {
            notificationManager.notifySubscribers(message);
            //no code should be right here which can cause any exceptions
        } catch (Exception e) {
            log.error("Failed to send notification {} via {}. Moving to DLQ. Error: {}",
                    messageId, notificationManager.getClass().getSimpleName(), e.getMessage(), e);
            NotificationMessageDlq dlqMessage = messageToDlqConverter.convertToDlq(message, e);
            NotificationMessageDlq saved = notificationDlqRepository.save(dlqMessage);
            log.warn("Notification {} moved to DLQ with id {}", messageId, saved.getId());
        }
    }
}

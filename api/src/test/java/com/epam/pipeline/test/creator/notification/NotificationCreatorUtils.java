/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.creator.notification;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.notification.NotificationMessageVO;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class NotificationCreatorUtils {

    public static final TypeReference<Result<NotificationMessage>> NOTIFICATION_MESSAGE_TYPE =
            new TypeReference<Result<NotificationMessage>>() { };
    public static final TypeReference<Result<NotificationSettings>> NOTIFICATION_SETTINGS_TYPE =
            new TypeReference<Result<NotificationSettings>>() { };
    public static final TypeReference<Result<List<NotificationSettings>>> NOTIFICATION_SETTINGS_LIST_TYPE =
            new TypeReference<Result<List<NotificationSettings>>>() { };
    public static final TypeReference<Result<NotificationTemplate>> NOTIFICATION_TEMPLATE_TYPE =
            new TypeReference<Result<NotificationTemplate>>() { };
    public static final TypeReference<Result<List<NotificationTemplate>>> NOTIFICATION_TEMPLATE_LIST_TYPE =
            new TypeReference<Result<List<NotificationTemplate>>>() { };
    private static final List<Long> LONG_LIST = Collections.singletonList(ID);

    private NotificationCreatorUtils() {

    }

    public static NotificationMessage getNotificationMessage() {
        final NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setBody(TEST_STRING);
        notificationMessage.setId(ID);
        notificationMessage.setSubject(TEST_STRING);
        notificationMessage.setCopyUserIds(LONG_LIST);
        return notificationMessage;
    }

    public static NotificationMessageVO getNotificationMessageVO() {
        return new NotificationMessageVO();
    }

    public static NotificationSettings getNotificationSettings() {
        final NotificationSettings notificationSettings = new NotificationSettings();
        notificationSettings.setId(ID);
        notificationSettings.setEnabled(true);
        notificationSettings.setInformedUserIds(LONG_LIST);
        notificationSettings.setResendDelay(ID);
        notificationSettings.setType(NotificationSettings.NotificationType.HIGH_CONSUMED_RESOURCES);
        notificationSettings.setStatusesToInform(Collections.singletonList(TaskStatus.RUNNING));
        return notificationSettings;
    }

    public static NotificationTemplate getNotificationTemplate() {
        final NotificationTemplate notificationTemplate = new NotificationTemplate();
        notificationTemplate.setBody(TEST_STRING);
        notificationTemplate.setId(ID);
        notificationTemplate.setName(TEST_STRING);
        notificationTemplate.setSubject(TEST_STRING);
        return notificationTemplate;
    }
}

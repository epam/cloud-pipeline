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
import com.epam.pipeline.controller.vo.SystemNotificationFilterVO;
import com.epam.pipeline.controller.vo.notification.NotificationMessageVO;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.notification.SystemNotification;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmation;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmationRequest;
import com.epam.pipeline.entity.notification.SystemNotificationSeverity;
import com.epam.pipeline.entity.notification.SystemNotificationState;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
    public static final TypeReference<Result<SystemNotification>> SYSTEM_NOTIFICATION_TYPE =
            new TypeReference<Result<SystemNotification>>() { };
    public static final TypeReference<Result<List<SystemNotification>>> SYSTEM_NOTIFICATION_LIST_TYPE =
            new TypeReference<Result<List<SystemNotification>>>() { };
    public static final TypeReference<Result<SystemNotificationConfirmation>> CONFIRMATION_TYPE =
            new TypeReference<Result<SystemNotificationConfirmation>>() { };
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

    public static NotificationSettings getNotificationSettings(final Long id) {
        final NotificationSettings settings = new NotificationSettings();
        settings.setId(id);
        settings.setType(NotificationSettings.NotificationType.PIPELINE_RUN_STATUS);
        settings.setKeepInformedAdmins(true);
        settings.setInformedUserIds(Collections.emptyList());
        settings.setTemplateId(ID);
        settings.setThreshold(null);
        settings.setEnabled(true);
        settings.setResendDelay(null);
        settings.setKeepInformedOwner(true);
        settings.setStatusesToInform(Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE));
        return settings;
    }
    public static NotificationTemplate getNotificationTemplate() {
        final NotificationTemplate notificationTemplate = new NotificationTemplate();
        notificationTemplate.setBody(TEST_STRING);
        notificationTemplate.setId(ID);
        notificationTemplate.setName(TEST_STRING);
        notificationTemplate.setSubject(TEST_STRING);
        return notificationTemplate;
    }

    public static SystemNotification getSystemNotification() {
        final SystemNotification systemNotification = new SystemNotification();
        systemNotification.setBlocking(true);
        systemNotification.setBody(TEST_STRING);
        systemNotification.setCreatedDate(new Date());
        systemNotification.setNotificationId(ID);
        systemNotification.setSeverity(SystemNotificationSeverity.INFO);
        systemNotification.setState(SystemNotificationState.ACTIVE);
        systemNotification.setTitle(TEST_STRING);
        return systemNotification;
    }

    public static SystemNotificationFilterVO getSystemNotificationFilterVO() {
        final SystemNotificationFilterVO systemNotificationFilterVO = new SystemNotificationFilterVO();
        systemNotificationFilterVO.setCreatedDateAfter(new Date());
        systemNotificationFilterVO.setSeverityList(Collections.singletonList(SystemNotificationSeverity.CRITICAL));
        systemNotificationFilterVO.setStateList(Collections.singletonList(SystemNotificationState.ACTIVE));
        return systemNotificationFilterVO;
    }

    public static SystemNotificationConfirmation getSystemNotificationConfirmation() {
        return new SystemNotificationConfirmation(ID, TEST_STRING, TEST_STRING, TEST_STRING, new Date());
    }

    public static SystemNotificationConfirmationRequest getSystemNotificationConfirmationRequest() {
        return new SystemNotificationConfirmationRequest();
    }
}

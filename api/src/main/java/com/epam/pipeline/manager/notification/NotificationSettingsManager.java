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

package com.epam.pipeline.manager.notification;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.notification.NotificationSettingsDao;
import com.epam.pipeline.entity.notification.NotificationSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;

@Service
public class NotificationSettingsManager {

    @Autowired
    private NotificationSettingsDao notificationSettingsDao;

    @Autowired
    private MessageHelper messageHelper;

    public List<NotificationSettings> loadAll() {
        List<NotificationSettings> settings = notificationSettingsDao.loadAllNotificationsSettings();
        Set<NotificationType> existingTemplateTypes = settings.stream().map(NotificationSettings::getType)
                .collect(Collectors.toSet());

        for (NotificationType type : NotificationType.values()) {
            if (!existingTemplateTypes.contains(type)) {
                NotificationSettings defaultSetting = NotificationSettings.getDefault(type);
                settings.add(defaultSetting);
            }
        }
        return settings;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public NotificationSettings createOrUpdate(NotificationSettings settings) {
        Assert.notNull(settings.getType(),
                messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "type"));
        Assert.isTrue(settings.getTemplateId()  == settings.getType().getId(),
                messageHelper.getMessage(MessageConstants.ERROR_TEMPLATE_ID_SHOULD_BE_EQUAL_TO_TYPE,
                        settings.getType().getId(), settings.getTemplateId()));

        if (notificationSettingsDao.loadNotificationSettings(settings.getType().getId()) != null) {
            Assert.isTrue(settings.getId() == settings.getType().getId(),
                    messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_SETTINGS_NOT_FOUND, settings.getId()));
            return notificationSettingsDao.updateNotificationSettings(settings);
        } else {
            return notificationSettingsDao.createNotificationSettings(settings);
        }
    }

    /**
     * Loads NotificationSettings by type.
     * @param type {@link NotificationType}
     * @return NotificationSettings from the database by type. May be null, if no settings configured
     */
    public NotificationSettings load(NotificationType type) {
        return notificationSettingsDao.loadNotificationSettings(type.getId());
    }

    public NotificationSettings load(long id) {
        NotificationSettings settings = notificationSettingsDao.loadNotificationSettings(id);
        if (settings == null) {
            settings = NotificationSettings.getDefault(NotificationType.getById(id));
        }
        return settings;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(long id) {
        notificationSettingsDao.deleteNotificationSettingsById(id);
    }
}

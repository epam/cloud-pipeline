/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.dao.notification.NotificationTemplateDao;
import com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationTemplateManager {
    @Autowired
    private NotificationTemplateDao notificationTemplateDao;

    @Autowired
    private MessageHelper messageHelper;

    public List<NotificationTemplate> loadAll() {
        List<NotificationTemplate> templates = notificationTemplateDao.loadAllNotificationTemplates();
        Set<Long> existingTemplateTypes = templates.stream()
                .map(NotificationTemplate::getId)
                .collect(Collectors.toSet());

        for (NotificationType type : NotificationType.values()) {
            if (!existingTemplateTypes.contains(type.getId())) {
                NotificationTemplate defaultTemplate = NotificationTemplate.getDefault(type);
                templates.add(defaultTemplate);
            }
        }
        return templates;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public NotificationTemplate create(NotificationTemplate template){
        Assert.assertNotNull(messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "id"),
                template.getId());
        Assert.assertFalse(messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "name"),
                           StringUtils.isBlank(template.getName()));
        Assert.assertFalse(messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "body"),
                           StringUtils.isBlank(template.getBody()));
        Assert.assertFalse(messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "subject"),
                           StringUtils.isBlank(template.getSubject()));

        if (notificationTemplateDao.loadNotificationTemplate(template.getId()) == null) {
            return notificationTemplateDao.createNotificationTemplate(template);
        } else {
            return notificationTemplateDao.updateNotificationTemplate(template);
        }
    }

    public NotificationTemplate load(long id) {
        NotificationTemplate loaded = notificationTemplateDao.loadNotificationTemplate(id);
        if (loaded == null) {
            loaded = NotificationTemplate.getDefault(NotificationType.getById(id));
        }
        return loaded;
    }

    public NotificationTemplate load(NotificationType type) {
        return notificationTemplateDao.loadNotificationTemplate(type.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(long templateId) {
        notificationTemplateDao.deleteNotificationTemplate(templateId);
    }
}

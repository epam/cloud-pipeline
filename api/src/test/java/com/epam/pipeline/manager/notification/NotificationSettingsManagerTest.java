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

import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;

import java.util.Collections;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.app.TestApplication;
import com.epam.pipeline.dao.notification.NotificationTemplateDao;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ContextConfiguration(classes = TestApplication.class)
public class NotificationSettingsManagerTest extends AbstractSpringTest {

    @Autowired
    private NotificationSettingsManager notificationSettingsManager;

    @Autowired
    private NotificationTemplateDao notificationTemplateDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCreateAndUpdate() {
        NotificationTemplate template = createTemplate(1L, "template");
        NotificationSettings settings = createSettings(NotificationType.LONG_RUNNING, template.getId(), 1L, 1L);
        notificationSettingsManager.createOrUpdate(settings);

        NotificationSettings loaded = notificationSettingsManager.load(NotificationType.LONG_RUNNING);
        Assert.assertNotNull(loaded);
        Assert.assertEquals(NotificationType.LONG_RUNNING, loaded.getType());
        Assert.assertEquals(1L, loaded.getThreshold().longValue());
        Assert.assertEquals(settings.isKeepInformedOwner(), loaded.isKeepInformedOwner());

        settings.setThreshold(2L);
        notificationSettingsManager.createOrUpdate(settings);
        loaded = notificationSettingsManager.load(NotificationType.LONG_RUNNING);
        Assert.assertNotNull(loaded);
        Assert.assertEquals(2L, loaded.getThreshold().longValue());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCreateWithoutThresholdAndResendDelay() {
        NotificationTemplate template = createTemplate(1L, "template");
        NotificationSettings settings = createSettings(NotificationType.LONG_RUNNING, template.getId(), null, null);
        notificationSettingsManager.createOrUpdate(settings);

        NotificationSettings loaded = notificationSettingsManager.load(NotificationType.LONG_RUNNING);
        Assert.assertEquals(-1, loaded.getThreshold().longValue());
        Assert.assertEquals(-1, loaded.getResendDelay().longValue());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testDelete() {
        NotificationTemplate template = createTemplate(1L, "template");
        NotificationSettings settings = createSettings(NotificationType.LONG_RUNNING, template.getId(), 1L, 1L);
        notificationSettingsManager.createOrUpdate(settings);

        NotificationSettings loaded = notificationSettingsManager.load(NotificationType.LONG_RUNNING);
        Assert.assertNotNull(loaded);

        notificationSettingsManager.delete(loaded.getId());

        loaded = notificationSettingsManager.load(NotificationType.LONG_RUNNING);
        Assert.assertNull(loaded);
    }

    private NotificationTemplate createTemplate(Long id, String name) {
        NotificationTemplate template = new NotificationTemplate(id);
        template.setName(name);
        template.setBody("//");
        template.setSubject("//");
        notificationTemplateDao.createNotificationTemplate(template);
        return template;
    }

    private NotificationSettings createSettings(NotificationType type, long templateId,
                                                Long threshold, Long delay) {
        NotificationSettings settings = new NotificationSettings();
        settings.setType(type);
        settings.setKeepInformedAdmins(true);
        settings.setInformedUserIds(Collections.emptyList());
        settings.setTemplateId(templateId);
        settings.setThreshold(threshold);
        settings.setResendDelay(delay);
        settings.setKeepInformedOwner(true);
        return settings;
    }
}
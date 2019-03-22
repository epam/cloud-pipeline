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

package com.epam.pipeline.dao.notification;

import java.util.List;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class NotificationTemplateDaoTest extends AbstractSpringTest {

    private static final String SUBJECT_STRING = "Subject";
    private static final String TEST_NAME = "TestTemplate";
    private static final String BODY_STRING = "Body";

    @Autowired
    private NotificationTemplateDao notificationTemplateDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotificationTemplate() {
        NotificationTemplate template = createTemplate();
        NotificationTemplate notificationTemplate = notificationTemplateDao.createNotificationTemplate(template);
        Assert.assertNotNull(notificationTemplateDao.loadNotificationTemplate(notificationTemplate.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loadNotificationTemplate() {
        NotificationTemplate template = createTemplate();
        NotificationTemplate notificationTemplate = notificationTemplateDao.createNotificationTemplate(template);
        NotificationTemplate loaded = notificationTemplateDao.loadNotificationTemplate(notificationTemplate.getId());
        Assert.assertEquals(loaded.getSubject(), SUBJECT_STRING);
        Assert.assertEquals(loaded.getBody(), BODY_STRING);
        Assert.assertEquals(loaded.getName(), TEST_NAME);

        List<NotificationTemplate> templates = notificationTemplateDao.loadAllNotificationTemplates();
        Assert.assertFalse(templates.isEmpty());
    }

    private NotificationTemplate createTemplate() {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(1L);
        template.setSubject(SUBJECT_STRING);
        template.setName(TEST_NAME);
        template.setBody(BODY_STRING);
        return template;
    }
}
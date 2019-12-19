/*
 *
 *  * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.epam.pipeline.manager.user;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.user.PipelineUser;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

public class UserManagerTest extends AbstractSpringTest {

    private static final String TEST_USER = "TestUser";
    private static final String SUBJECT = "Subject";
    private static final String BODY = "Body";

    @Autowired
    private UserManager userManager;

    @Autowired
    private MonitoringNotificationDao notificationDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteUser() {
        final PipelineUser user = userManager.createUser(TEST_USER,
                Collections.singletonList(2L),
                Collections.emptyList(), null, null);

        final NotificationMessage message = new NotificationMessage();
        final NotificationTemplate template = new NotificationTemplate();
        template.setSubject(SUBJECT);
        template.setBody(BODY);
        message.setTemplate(template);
        message.setTemplateParameters(Collections.emptyMap());
        message.setToUserId(user.getId());
        message.setCopyUserIds(Collections.singletonList(user.getId()));

        notificationDao.createMonitoringNotification(message);
        Assert.assertFalse(notificationDao.loadAllNotifications().isEmpty());
        userManager.deleteUser(user.getId());
        Assert.assertTrue(notificationDao.loadAllNotifications().isEmpty());
    }
}
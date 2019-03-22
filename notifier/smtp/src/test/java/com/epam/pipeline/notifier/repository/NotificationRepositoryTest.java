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

package com.epam.pipeline.notifier.repository;

import java.util.Collections;
import java.util.List;

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.notifier.AbstractSpringTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class NotificationRepositoryTest extends AbstractSpringTest {

    private static final String SUBJECT = "Hi";
    private static final String BODY_WITH_PARAM = "Hi, from test {testHash}";
    private static final String BODY_WITHOUT_PARAM = "Hi, from test";
    private static final String PARAM = "testHash";

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;


    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loadTest() {
        NotificationMessage message = new NotificationMessage();
        NotificationTemplate template = new NotificationTemplate();
        template.setSubject(SUBJECT);
        template.setBody(BODY_WITH_PARAM);
        templateRepository.save(template);
        message.setTemplate(template);
        message.setTemplateParameters(Collections.singletonMap(PARAM, Integer.toHexString(this.hashCode())));
        message.setToUserId(0L);
        message.setCopyUserIds(Collections.singletonList(0L));
        notificationRepository.save(message);
        List<NotificationMessage> messages = notificationRepository.loadNotification(new PageRequest(0, 5));
        Assert.assertTrue(messages.size() == 1);
        NotificationMessage loaded = messages.get(0);
        Assert.assertEquals(Integer.toHexString(this.hashCode()), loaded.getTemplateParameters().get(PARAM));
        Assert.assertEquals(BODY_WITH_PARAM, loaded.getTemplate().getBody());
        Assert.assertEquals(SUBJECT, loaded.getTemplate().getSubject());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteByIdTest() {
        NotificationMessage message = new NotificationMessage();
        NotificationTemplate template = new NotificationTemplate();
        template.setSubject(SUBJECT);
        template.setBody(BODY_WITHOUT_PARAM);
        templateRepository.save(template);
        message.setTemplate(template);
        message.setToUserId(0L);
        message.setCopyUserIds(Collections.singletonList(0L));
        notificationRepository.save(message);
        Long idToDelete = message.getId();
        notificationRepository.deleteById(idToDelete);
        Assert.assertNull(notificationRepository.findOne(idToDelete));
    }

}
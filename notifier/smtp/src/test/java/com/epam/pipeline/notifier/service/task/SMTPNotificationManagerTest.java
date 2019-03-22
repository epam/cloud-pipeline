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

package com.epam.pipeline.notifier.service.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.notifier.AbstractSpringTest;
import com.epam.pipeline.notifier.repository.UserRepository;
import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

public class SMTPNotificationManagerTest extends AbstractSpringTest {

    private static final String MESSAGE_SUBJECT = "Hi";
    private static final String MESSAGE_BODY = "Hi, I just want to tell that your music the best!";
    private static final String MESSAGE_BODY_WITH_PARAM = "Hi $templateParameters.get(\"name\"), "
                                                          + "I just want to tell that your music the best! "
                                                          + "$numberTool.format(\"#0.00\", 56.4431)";
    private static final String PARSED_MESSAGE_BODY_WITH_PARAM = "Hi $templateParameters.get(\"name\"), "
                                                                 + "I just want to tell that your music "
                                                                 + "the best! 56.44";
    private static final String USER_NAME = "James Alan Hetfield";
    private static final String EMAIL = "HetfieldJ@metallica.com";
    private static final String EMAIL_KEY = "email";

    @Rule
    public final GreenMailRule greenMail = new GreenMailRule(ServerSetupTest.SMTP);

    @Autowired
    private SMTPNotificationManager smtpNotificationManager;

    @Autowired
    private UserRepository userRepository;


    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testEmailSending() {
        PipelineUser user = new PipelineUser();
        user.setUserName(USER_NAME);
        user.setAdmin(true);
        user.setAttributes(Collections.singletonMap(EMAIL_KEY, EMAIL));
        userRepository.save(user);

        NotificationMessage message = new NotificationMessage();
        NotificationTemplate template = new NotificationTemplate();
        template.setSubject(MESSAGE_SUBJECT);
        template.setBody(MESSAGE_BODY);
        message.setTemplate(template);
        message.setTemplateParameters(Collections.emptyMap());
        message.setToUserId(user.getId());
        message.setCopyUserIds(Collections.singletonList(user.getId()));

        smtpNotificationManager.notifySubscribers(message);
        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertTrue(receivedMessages.length == 2);
        assertTrue(GreenMailUtil.getBody(receivedMessages[0]).contains(MESSAGE_BODY));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testEmailSendingWithParams() {
        PipelineUser user = new PipelineUser();
        user.setUserName(USER_NAME);
        user.setAdmin(true);
        user.setAttributes(Collections.singletonMap(EMAIL_KEY, EMAIL));
        userRepository.save(user);

        NotificationMessage message = new NotificationMessage();
        NotificationTemplate template = new NotificationTemplate();
        template.setSubject(MESSAGE_SUBJECT);
        template.setBody(MESSAGE_BODY_WITH_PARAM);
        message.setTemplate(template);
        message.setTemplateParameters(Collections.singletonMap("name", USER_NAME));
        message.setToUserId(user.getId());
        message.setCopyUserIds(Collections.singletonList(user.getId()));

        smtpNotificationManager.notifySubscribers(message);
        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertTrue(receivedMessages.length == 2);
        String filledMessage = PARSED_MESSAGE_BODY_WITH_PARAM
                .replace("$templateParameters.get(\"name\")", USER_NAME);
        assertTrue(GreenMailUtil.getBody(receivedMessages[0]).contains(filledMessage));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testEmailSendingWithoutTemplate() {
        PipelineUser user = new PipelineUser();
        user.setUserName(USER_NAME);
        user.setAttributes(Collections.singletonMap(EMAIL_KEY, EMAIL));
        userRepository.save(user);

        NotificationMessage message = new NotificationMessage();
        message.setSubject(MESSAGE_SUBJECT);
        message.setBody(MESSAGE_BODY);
        message.setToUserId(user.getId());
        message.setCopyUserIds(Collections.singletonList(user.getId()));

        smtpNotificationManager.notifySubscribers(message);
        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertTrue(receivedMessages.length == 2);
        assertTrue(GreenMailUtil.getBody(receivedMessages[0]).contains(MESSAGE_BODY));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testEmailSendingWithParamsWithoutTemplate() {
        PipelineUser user = new PipelineUser();
        user.setUserName(USER_NAME);
        user.setAttributes(Collections.singletonMap(EMAIL_KEY, EMAIL));
        userRepository.save(user);

        NotificationMessage message = new NotificationMessage();
        message.setSubject(MESSAGE_SUBJECT);
        message.setBody(MESSAGE_BODY_WITH_PARAM);
        message.setTemplateParameters(Collections.singletonMap("name", USER_NAME));
        message.setToUserId(user.getId());
        message.setCopyUserIds(Collections.singletonList(user.getId()));

        smtpNotificationManager.notifySubscribers(message);
        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertTrue(receivedMessages.length == 2);
        String filledMessage = PARSED_MESSAGE_BODY_WITH_PARAM
                .replace("$templateParameters.get(\"name\")", USER_NAME);
        System.out.println(GreenMailUtil.getBody(receivedMessages[0]));
        System.out.println(filledMessage);
        assertTrue(GreenMailUtil.getBody(receivedMessages[0]).contains(filledMessage));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testWontSendToOwnerIfNotConfigured() throws MessagingException {
        PipelineUser user = new PipelineUser();
        user.setUserName(USER_NAME);
        user.setAdmin(false);
        user.setAttributes(Collections.singletonMap(EMAIL_KEY, EMAIL));
        userRepository.save(user);

        NotificationMessage message = new NotificationMessage();
        NotificationTemplate template = new NotificationTemplate();
        template.setSubject(MESSAGE_SUBJECT);
        template.setBody(MESSAGE_BODY_WITH_PARAM);
        message.setTemplate(template);
        message.setTemplateParameters(Collections.singletonMap("name", USER_NAME));
        message.setToUserId(null);
        message.setCopyUserIds(Collections.singletonList(user.getId()));

        smtpNotificationManager.notifySubscribers(message);
        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertEquals(1, receivedMessages.length);
        assertNull(receivedMessages[0].getRecipients(Message.RecipientType.TO));
        assertEquals(1, receivedMessages[0].getRecipients(Message.RecipientType.CC).length);
    }
}
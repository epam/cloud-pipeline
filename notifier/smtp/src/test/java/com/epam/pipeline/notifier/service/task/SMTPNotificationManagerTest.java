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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
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
    private static final String COPY_USER_NAME = "Lars Ulrich";
    private static final String COPY_EMAIL = "UlrichL@metallica.com";
    private static final String RUN_SUBJECT = "Job #$templateParameters.get(\"id\") is now STOPPED";
    private static final String PARSED_RUN_SUBJECT = "Job #14638 is now STOPPED";
    private static final int RUN_ID = 14638;
    private static final long MESSAGE_ID = 1L;

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
        assertEquals(2, receivedMessages.length);
        assertTrue(GreenMailUtil.getBody(receivedMessages[0]).contains(MESSAGE_BODY));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testEmailSendingWithoutMainUser() {
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
        message.setCopyUserIds(Collections.singletonList(user.getId()));

        smtpNotificationManager.notifySubscribers(message);
        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertEquals(1, receivedMessages.length);
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
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testEmailLogNamesSubjectAndAllRecipients() {
        PipelineUser owner = new PipelineUser();
        owner.setUserName(USER_NAME);
        owner.setAttributes(Collections.singletonMap(EMAIL_KEY, EMAIL));
        userRepository.save(owner);

        PipelineUser copyUser = new PipelineUser();
        copyUser.setUserName(COPY_USER_NAME);
        copyUser.setAttributes(Collections.singletonMap(EMAIL_KEY, COPY_EMAIL));
        userRepository.save(copyUser);

        NotificationMessage message = new NotificationMessage();
        message.setId(MESSAGE_ID);
        NotificationTemplate template = new NotificationTemplate();
        template.setSubject(RUN_SUBJECT);
        template.setBody(MESSAGE_BODY);
        message.setTemplate(template);
        message.setTemplateParameters(Collections.singletonMap("id", RUN_ID));
        message.setToUserId(owner.getId());
        message.setCopyUserIds(Collections.singletonList(copyUser.getId()));

        List<String> logged = logOf(() -> smtpNotificationManager.notifySubscribers(message));

        assertEquals(2, greenMail.getReceivedMessages().length);
        String expected = String.format("Email from message %s with subject '%s' formed and will be send to: [%s], "
                + "cc: [], bcc: [%s]", MESSAGE_ID, PARSED_RUN_SUBJECT, EMAIL, COPY_EMAIL);
        assertTrue(String.format("No '%s' in %s", expected, logged), logged.contains(expected));
    }

    /**
     * Runs an action and returns the messages that {@link SMTPNotificationManager} logged meanwhile, at INFO and
     * above. The test context does not load the notifier's log4j2 configuration, so INFO is off unless set here.
     */
    private static List<String> logOf(final Runnable action) {
        final Logger logger = ((LoggerContext) LogManager.getContext(false))
                .getLogger(SMTPNotificationManager.class.getName());
        final Level level = logger.getLevel();
        final MessagesAppender appender = new MessagesAppender();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            action.run();
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(level);
            appender.stop();
        }
        return appender.getMessages();
    }

    private static final class MessagesAppender extends AbstractAppender {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        MessagesAppender() {
            super("messages", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        List<String> getMessages() {
            return messages;
        }
    }
}
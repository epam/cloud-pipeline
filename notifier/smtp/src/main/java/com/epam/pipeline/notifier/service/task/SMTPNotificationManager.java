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

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.notifier.repository.UserRepository;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.tools.generic.NumberTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.mail.internet.InternetAddress;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * SMTP realization of {@link NotificationManager}.
 * {@link SMTPNotificationManager} sends message to all target users from {@link NotificationMessage#getToUserId()} and
 * {@link NotificationMessage#getCopyUserIds()}
 */
@Component
public class SMTPNotificationManager implements NotificationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SMTPNotificationManager.class);
    public static final String MESSAGE_TAG = "message";

    @Value(value = "${notification.enable.smtp}")
    private boolean isEnabled;

    @Value(value = "${email.notification.retry.count:3}")
    private int notifyRetryCount;

    @Value(value = "${email.smtp.server.host.name}")
    private String smtpServerHostName;

    @Value(value = "${email.smtp.port}")
    private int smtpPort;

    @Value(value = "${email.ssl.on.connect}")
    private boolean sslOnConnect;

    @Value(value = "${email.start.tls.enabled}")
    private boolean startTlsEnabled;

    @Value(value = "${email.from}")
    private String emailFrom;

    @Value(value = "${email.user}")
    private String username;

    @Value(value = "${email.password}")
    private String password;

    @Value(value = "${email.notification.letter.delay:-1}")
    private long emailDelay;

    @Value(value = "${email.notification.retry.delay:-1}")
    private long retryDelay;

    @Autowired
    private UserRepository userRepository;

    /**
     * Sends a notification to all specified recipients.
     *
     * If {@link NotificationMessage#template} is specified then it will be used. Otherwise
     * {@link NotificationMessage#subject} and {@link NotificationMessage#body} will be used instead.
     *
     * Both subject and body are filled with {@link NotificationMessage#templateParameters} regardless the way they
     * were retrieved (from template or directly from fields).
     */
    @Override
    public void notifySubscribers(NotificationMessage message) {
        if (!isEnabled) {
            return;
        }
        for (int i = 0; i < notifyRetryCount; i++) {
            try {
                Optional<Email> email = buildEmail(message);
                if (email.isPresent()) {
                    email.get().send();
                }

                LOGGER.info("Message with id: {} was successfully send", message.getId());
                sleepIfRequired(emailDelay);
                return;
            } catch (EmailException e) {
                LOGGER.warn(String.format("Fail to send message with id %d. Attempt %d/%d. %n Cause: %n ",
                        message.getId(), i + 1, notifyRetryCount), e);
                sleepIfRequired(retryDelay);
            }
        }
        LOGGER.error(String.format("All attempts are failed. Message with id: %d will not be sent.",
                message.getId()));

    }

    private Optional<Email> buildEmail(NotificationMessage message) throws EmailException {
        HtmlEmail email = new HtmlEmail();
        email.setHostName(smtpServerHostName);
        email.setSmtpPort(smtpPort);
        email.setSSLOnConnect(sslOnConnect);
        email.setStartTLSEnabled(startTlsEnabled);

        // check that credentials are provided, otherwise try to proceed without authentication
        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            email.setAuthenticator(new DefaultAuthenticator(username, password));
        }

        email.setFrom(emailFrom);

        Optional<NotificationTemplate> template = Optional.ofNullable(message.getTemplate());
        String subject = template.map(NotificationTemplate::getSubject).orElse(message.getSubject());
        String body = template.map(NotificationTemplate::getBody).orElse(message.getBody());

        VelocityContext velocityContext = getVelocityContext(message);
        velocityContext.put("numberTool", new NumberTool());

        StringWriter subjectOut = new StringWriter();
        StringWriter bodyOut = new StringWriter();

        Velocity.evaluate(velocityContext, subjectOut, MESSAGE_TAG + message.hashCode(), subject);
        Velocity.evaluate(velocityContext, bodyOut, MESSAGE_TAG + message.hashCode(), body);

        email.setSubject(subjectOut.toString());
        email.setHtmlMsg(bodyOut.toString());

        if (message.getToUserId() != null) {
            PipelineUser targetUser = userRepository.findOne(message.getToUserId());
            email.addTo(targetUser.getEmail());
        } else {
            if (CollectionUtils.isEmpty(message.getCopyUserIds())) {
                LOGGER.info("Email with message {} won't be sent: no recipients found", message.getId());
                return Optional.empty();
            }
        }

        List<PipelineUser> keepInformedUsers = userRepository.findByIdIn(message.getCopyUserIds());

        for (PipelineUser user : keepInformedUsers) {
            String address = user.getEmail();
            if (address != null) {
                email.addCc(address);
            }
        }

        LOGGER.info("Email from message {} formed and will be send to: {}",
                message.getId(),
                email.getToAddresses()
                        .stream()
                        .map(InternetAddress::getAddress)
                        .collect(Collectors.toList())
        );

        return Optional.of(email);
    }

    private VelocityContext getVelocityContext(NotificationMessage message) {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("templateParameters", message.getTemplateParameters());

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        velocityContext.put("calendar", calendar);

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        velocityContext.put("dateFormat", dateFormat);

        return velocityContext;
    }

    private void sleepIfRequired(final long delay) {
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error(e.getMessage(), e);
        }
    }

}

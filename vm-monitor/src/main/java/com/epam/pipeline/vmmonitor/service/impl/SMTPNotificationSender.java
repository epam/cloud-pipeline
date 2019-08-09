/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.vmmonitor.service.impl;

import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.vmmonitor.config.SMTPConfiguration;
import com.epam.pipeline.vmmonitor.service.CloudPipelineAPIClient;
import com.epam.pipeline.vmmonitor.service.NotificationSender;
import com.epam.pipeline.vo.notification.NotificationMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.tools.generic.NumberTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.mail.internet.InternetAddress;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(value = "notification.send.method", havingValue = "smtp")
public class SMTPNotificationSender implements NotificationSender {

    private static final String MESSAGE_TAG = "message";

    private final CloudPipelineAPIClient apiClient;
    private final SMTPConfiguration configuration;

    @Override
    public void sendMessage(final NotificationMessageVO message) {
        log.debug("Submitting notification using SMTP");
        for (int i = 0; i < configuration.getNotificationRetryCount(); i++) {
            try {
                Optional<Email> email = buildEmail(message);
                if (email.isPresent()) {
                    email.get().send();
                    log.info("SMTP: Message with subject: '{}' was successfully send", message.getSubject());
                }

                sleepIfRequired(configuration.getNotificationLetterDelay());
                return;
            } catch (EmailException e) {
                log.warn(String.format("SMTP: Fail to send message with subject '%s'. Attempt %d/%d. %n Cause: %n ",
                        message.getSubject(), i + 1, configuration.getNotificationRetryCount()), e);
                sleepIfRequired(configuration.getNotificationRetryDelay());
            }
        }
        log.error(String.format("SMTP: All attempts are failed. Message with subject: '%s' will not be sent.",
                message.getSubject()));
    }

    private Optional<Email> buildEmail(NotificationMessageVO message) throws EmailException {
        final HtmlEmail email = new HtmlEmail();
        email.setHostName(configuration.getSmtpServerHostName());
        email.setSmtpPort(configuration.getSmtpPort());
        email.setSSLOnConnect(configuration.isSslOnConnect());
        email.setStartTLSEnabled(configuration.isStartTlsEnabled());

        // check that credentials are provided, otherwise try to proceed without authentication
        if (StringUtils.isNotBlank(configuration.getUser()) &&
                StringUtils.isNotBlank(configuration.getPassword())) {
            email.setAuthenticator(new DefaultAuthenticator(configuration.getUser(), configuration.getPassword()));
        }

        email.setFrom(configuration.getFrom());

        final String subject = message.getSubject();
        final String body = message.getBody();

        final VelocityContext velocityContext = getVelocityContext(message);
        velocityContext.put("numberTool", new NumberTool());

        final StringWriter subjectOut = new StringWriter();
        final StringWriter bodyOut = new StringWriter();

        Velocity.evaluate(velocityContext, subjectOut, MESSAGE_TAG + message.hashCode(), subject);
        Velocity.evaluate(velocityContext, bodyOut, MESSAGE_TAG + message.hashCode(), body);

        email.setSubject(subjectOut.toString());
        email.setHtmlMsg(bodyOut.toString());

        if (message.getToUser() == null && CollectionUtils.isEmpty(message.getCopyUsers())) {
            log.info("Email with message {} won't be sent: no recipients found", message.getSubject());
            return Optional.empty();
        }

        final String userEmail = getTargetUserEmail(message);
        if (userEmail != null) {
            email.addTo(userEmail);
        }

        ListUtils.emptyIfNull(message.getCopyUsers())
                .forEach(username -> {
                    final String ccEmail = getUserEmail(username);
                    if (ccEmail != null) {
                        try {
                            email.addCc(ccEmail);
                        } catch (EmailException e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                });

        log.info("Email from message {} formed and will be send to: {}",
                message.getSubject(),
                email.getToAddresses()
                        .stream()
                        .map(InternetAddress::getAddress)
                        .collect(Collectors.toList())
        );

        return Optional.of(email);
    }

    private String getTargetUserEmail(NotificationMessageVO message) {
        return getUserEmail(message.getToUser());
    }

    private VelocityContext getVelocityContext(NotificationMessageVO message) {
        final VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("templateParameters", message.getParameters());

        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        velocityContext.put("calendar", calendar);

        final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
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
            log.error(e.getMessage(), e);
        }
    }

    private String getUserEmail(final String userName) {
        try {
            return Optional.ofNullable(apiClient.loadUserByName(userName))
                    .map(PipelineUser::getEmail)
                    .orElse(null);
        } catch (PipelineResponseException e) {
            log.error("An error during loading user by name " + userName, e);
            return null;
        }
    }
}

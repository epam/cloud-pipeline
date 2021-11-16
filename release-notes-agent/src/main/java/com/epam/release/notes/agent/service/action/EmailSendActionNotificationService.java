/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.release.notes.agent.service.action;

import com.epam.release.notes.agent.entity.action.Action;
import com.epam.release.notes.agent.entity.mail.EmailContent;
import com.epam.release.notes.agent.entity.version.VersionStatusInfo;
import com.epam.release.notes.agent.exception.EmailException;
import com.epam.release.notes.agent.service.action.mail.TemplateNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Optional;

import static java.lang.String.format;

@Slf4j
@Service
public class EmailSendActionNotificationService implements ActionNotificationService {

    private final JavaMailSender javaMailSender;
    private final TemplateNotificationService templateNotificationService;
    private final String serviceEmail;

    public EmailSendActionNotificationService(final JavaMailSender javaMailSender,
                                              final TemplateNotificationService templateNotificationService,
                                              @Value("${spring.mail.username}") final String serviceEmail) {
        this.javaMailSender = javaMailSender;
        this.templateNotificationService = templateNotificationService;
        this.serviceEmail = serviceEmail;
    }

    @Override
    public void process(final VersionStatusInfo versionStatusInfo) {
        final EmailContent emailContent = templateNotificationService.populate(versionStatusInfo);
        if (emailContent == null) {
            log.error("Email content must be populated. Email could not be sent.");
            return;
        }
        final MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        final MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
        try {
            helper.setFrom(serviceEmail);
            helper.setSubject(getEmailContent(emailContent.getTitle(), "title"));
            helper.setText(getEmailContent(emailContent.getBody(), "body"), true);
            helper.setTo(Optional.ofNullable(emailContent.getRecipients())
                    .map(recipients -> recipients.toArray(new String[0]))
                    .orElseThrow(() -> new IllegalArgumentException("Recipients are not specified. " +
                            "Email could not be sent.")));
        } catch (MessagingException e) {
            throw new EmailException("Exception was thrown while trying to populate a MimeMessage.", e);
        }
        javaMailSender.send(mimeMessage);
    }

    @Override
    public Action getServiceAction() {
        return Action.POST;
    }

    private String getEmailContent(final String content, final String contentName) {
        return Optional.ofNullable(content)
                .orElseThrow(() -> new IllegalArgumentException(
                        format("Email content %s is not specified. Email could not be sent.", contentName)));
    }
}

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

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.entity.jira.JiraIssue;
import com.epam.release.notes.agent.entity.mail.EmailContent;
import com.epam.release.notes.agent.exception.EmailException;
import com.epam.release.notes.agent.service.action.mail.TemplateNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.List;

@Service
public class EmailSendActionNotificationService implements ActionNotificationService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateNotificationService templateNotificationService;

    @Override
    public void process(final String oldVersion, final String newVersion, final List<JiraIssue> jiraIssues,
                        final List<GitHubIssue> gitHubIssues, final String[] recipients) {
        final EmailContent emailContent = templateNotificationService.populate(oldVersion, newVersion, jiraIssues,
                gitHubIssues);
        final MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        final MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
        try {
            helper.setSubject(emailContent.getTitle());
            helper.setText(emailContent.getBody(), true);
            helper.setTo(recipients);
        } catch (MessagingException e) {
            throw new EmailException("Exception was thrown while trying to populate a MimeMessage.", e);
        }
        javaMailSender.send(mimeMessage);
    }
}

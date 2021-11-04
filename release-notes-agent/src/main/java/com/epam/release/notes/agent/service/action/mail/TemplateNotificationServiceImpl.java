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

package com.epam.release.notes.agent.service.action.mail;

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.entity.jira.JiraIssue;
import com.epam.release.notes.agent.entity.mail.EmailContent;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import java.util.List;
import java.util.Locale;

@Service
public class TemplateNotificationServiceImpl implements TemplateNotificationService{

    private final SpringTemplateEngine templateEngine;
    private final String emailToAdminTemplateName;
    private final String emailToSubscribersTemplateName;
    private final String emailToAdminTitle;
    private final String emailToSubscribersTitle;

    public TemplateNotificationServiceImpl(final SpringTemplateEngine templateEngine,
                                           @Value("${release.notes.agent.path.to.admin.email}") final String emailToAdminTemplateName,
                                           @Value("${release.notes.agent.path.to.subscribers.email}") final String emailToSubscribersTemplateName,
                                           @Value("${release.notes.agent.email.to.admin.subject}") final String emailToAdminTitle,
                                           @Value("${release.notes.agent.email.to.subscribers.subject}") final String emailToSubscribersTitle) {
        this.templateEngine = templateEngine;
        this.emailToAdminTemplateName = emailToAdminTemplateName;
        this.emailToSubscribersTemplateName = emailToSubscribersTemplateName;
        this.emailToAdminTitle = emailToAdminTitle;
        this.emailToSubscribersTitle = emailToSubscribersTitle;
    }

    @Override
    public EmailContent populate(final String oldVersion,
                                 final String newVersion,
                                 final List<JiraIssue> jiraIssues,
                                 final List<GitHubIssue> gitHubIssues) {
        final Context context = new Context(Locale.US);
        context.setVariable("oldVersion", oldVersion);
        context.setVariable("newVersion", newVersion);
        if (validateIssuesParameters(jiraIssues, gitHubIssues)) {
            context.setVariable("jiraIssues", jiraIssues);
            context.setVariable("gitHubIssues", gitHubIssues);
            final String htmlEmailContent = templateEngine.process(emailToSubscribersTemplateName, context);
            return EmailContent.builder()
                    .title(emailToSubscribersTitle)
                    .body(htmlEmailContent)
                    .build();
        }
        final String htmlEmailContent = templateEngine.process(emailToAdminTemplateName, context);
        return EmailContent.builder()
                .title(emailToAdminTitle)
                .body(htmlEmailContent)
                .build();
    }

    private boolean validateIssuesParameters(final List<JiraIssue> jiraIssues, final List<GitHubIssue> gitHubIssues) {
        return CollectionUtils.isNotEmpty(jiraIssues) || CollectionUtils.isNotEmpty(gitHubIssues);
    }

}

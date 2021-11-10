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
import com.epam.release.notes.agent.entity.version.Version;
import com.epam.release.notes.agent.service.version.ApplicationVersionService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

@Service
public class EmailTemplateNotificationService implements TemplateNotificationService {

    private static final String OLD_VERSION = "oldVersion";
    private static final String NEW_VERSION = "newVersion";
    private static final String JIRA_ISSUES = "jiraIssues";
    private static final String GITHUB_ISSUES = "gitHubIssues";
    private static final String EXCEPTION_MESSAGE = "This kind of version status is not handled in current method";

    private final TemplateEngine templateEngine;
    private final ApplicationVersionService applicationVersionService;
    private final String emailToAdminTemplateName;
    private final String emailToSubscribersTemplateName;
    private final String emailToSubscribersWithoutIssuesTemplateName;
    private final String emailToAdminTitle;
    private final String emailToSubscribersTitle;

    public EmailTemplateNotificationService(
            final TemplateEngine templateEngine,
            final ApplicationVersionService applicationVersionService,
            @Value("${release.notes.agent.name.of.admin.email.template}") final String emailToAdminTemplateName,
            @Value("${release.notes.agent.name.of.subscribers.email.template}") final String emailToSubscribersTemplateName,
            @Value("${release.notes.agent.name.of.subscribers.email.without.issues.template}")
            final String emailToSubscribersWithoutIssuesTitle,
            @Value("${release.notes.agent.email.to.admin.subject}") final String emailToAdminTitle,
            @Value("${release.notes.agent.email.to.subscribers.subject}") final String emailToSubscribersTitle
    ) {
        this.templateEngine = templateEngine;
        this.applicationVersionService = applicationVersionService;
        this.emailToAdminTemplateName = emailToAdminTemplateName;
        this.emailToSubscribersTemplateName = emailToSubscribersTemplateName;
        this.emailToAdminTitle = emailToAdminTitle;
        this.emailToSubscribersTitle = emailToSubscribersTitle;
        this.emailToSubscribersWithoutIssuesTemplateName = emailToSubscribersWithoutIssuesTitle;
    }

    @Override
    public EmailContent populate(final String oldVersion,
                                 final String newVersion,
                                 final List<JiraIssue> jiraIssues,
                                 final List<GitHubIssue> gitHubIssues) {
        final Context context = new Context(Locale.US);
        switch (applicationVersionService.getVersionStatus(Version.buildVersion(oldVersion),
                Version.buildVersion(newVersion))) {
            case MAJOR_CHANGED:
                addVersionsToContext(oldVersion, newVersion, context);
                return createEmailContent(context, emailToAdminTitle, emailToAdminTemplateName);
            case MINOR_CHANGED:
                addVersionsToContext(oldVersion, newVersion, context);
                if(validateIssuesParameters(jiraIssues, gitHubIssues)){
                    return createEmailContent(context, emailToSubscribersTitle,
                            emailToSubscribersWithoutIssuesTemplateName);
                }
                addIssuesToContext(jiraIssues, gitHubIssues, context);
                return createEmailContent(context, emailToSubscribersTitle, emailToSubscribersTemplateName);
            default:
                throw new IllegalStateException(EXCEPTION_MESSAGE);
        }
    }

    private EmailContent createEmailContent(Context context, String emailTitle, String templateName) {
        return EmailContent.builder()
                .title(emailTitle)
                .body(templateEngine.process(templateName, context))
                .build();
    }

    private void addVersionsToContext(final String oldVersion, final String newVersion, final Context context) {
        context.setVariable(OLD_VERSION, oldVersion);
        context.setVariable(NEW_VERSION, newVersion);
    }

    private void addIssuesToContext(final List<JiraIssue> jiraIssues, final List<GitHubIssue> gitHubIssues,
                               final Context context) {
        context.setVariable(JIRA_ISSUES, jiraIssues);
        context.setVariable(GITHUB_ISSUES, gitHubIssues);
    }

    private boolean validateIssuesParameters(final List<JiraIssue> jiraIssues, final List<GitHubIssue> gitHubIssues) {
        return CollectionUtils.isEmpty(jiraIssues) && CollectionUtils.isEmpty(gitHubIssues);
    }

}

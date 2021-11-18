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
import com.epam.release.notes.agent.entity.version.VersionStatusInfo;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailTemplateNotificationService implements TemplateNotificationService {

    private static final String OLD_VERSION = "oldVersion";
    private static final String NEW_VERSION = "newVersion";
    private static final String JIRA_ISSUES = "jiraIssues";
    private static final String GITHUB_ISSUES = "gitHubIssues";
    private static final String EXCEPTION_MESSAGE = "This kind of version status is not handled in current method";
    private static final String ADMIN_EMAILS_NOT_CONFIGURED = "Admin emails is not configured!";
    private static final String ADMIN_EMAILS_NOT_CORRECT = "Admin emails is configured incorrectly!";
    private static final String SUBSCRIBERS_EMAILS_NOT_CONFIGURED = "Subscriber emails is not configured!";
    private static final String SUBSCRIBERS_EMAILS_NOT_CORRECT = "Subscribers emails is configured incorrectly!";
    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@"
            + "[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    private static final Pattern PATTERN = Pattern.compile(EMAIL_PATTERN);

    private final TemplateEngine templateEngine;
    private final String emailToAdminTemplateName;
    private final String emailToSubscribersTemplateName;
    private final String emailToAdminTitle;
    private final String emailToSubscribersTitle;
    private final List<String> adminsEmailAddresses;
    private final List<String> subscribersEmailAddresses;

    public EmailTemplateNotificationService(
            final TemplateEngine templateEngine,
            @Value("${release.notes.agent.name.of.admin.email.template}") final String emailToAdminTemplateName,
            @Value("${release.notes.agent.name.of.subscribers.email.template}")
            final String emailToSubscribersTemplateName,
            @Value("#{'${release.notes.agent.email.to.admin.subject}'?:" +
                    "'Cloud-pipeline major version change notification'}")
            final String emailToAdminTitle,
            @Value("#{'${release.notes.agent.email.to.subscribers.subject}'?:" +
                    "'Cloud-pipeline minor version change notification'}")
            final String emailToSubscribersTitle,
            @Value("#{'${release.notes.agent.admin.emails}'.split(',')?:}") final List<String> adminsEmailAddresses,
            @Value("#{'${release.notes.agent.subscribers.emails}'.split(',')?:}")
            final List<String> subscribersEmailAddresses
    ) {
        this.templateEngine = templateEngine;
        this.emailToAdminTemplateName = emailToAdminTemplateName;
        this.emailToSubscribersTemplateName = emailToSubscribersTemplateName;
        this.emailToAdminTitle = emailToAdminTitle;
        this.emailToSubscribersTitle = emailToSubscribersTitle;
        this.adminsEmailAddresses = ListUtils.emptyIfNull(adminsEmailAddresses);
        this.subscribersEmailAddresses = ListUtils.emptyIfNull(subscribersEmailAddresses);
        validateProperties();
    }


    @Override
    public EmailContent populate(final VersionStatusInfo versionStatusInfo) {
        final Context context = new Context(Locale.US);
        switch (versionStatusInfo.getVersionStatus()) {
            case MAJOR_CHANGED:
                addVersionsToContext(versionStatusInfo.getOldVersion(), versionStatusInfo.getNewVersion(), context);
                return createEmailContent(context, emailToAdminTitle, emailToAdminTemplateName,
                        ListUtils.emptyIfNull(adminsEmailAddresses));
            case MINOR_CHANGED:
                addVersionsToContext(versionStatusInfo.getOldVersion(), versionStatusInfo.getNewVersion(), context);
                addIssuesToContext(versionStatusInfo.getJiraIssues(), versionStatusInfo.getGitHubIssues(), context);
                return createEmailContent(context, emailToSubscribersTitle, emailToSubscribersTemplateName,
                        ListUtils.emptyIfNull(subscribersEmailAddresses));
            default:
                throw new IllegalStateException(EXCEPTION_MESSAGE);
        }
    }

    private EmailContent createEmailContent(final Context context, final String emailTitle,
                                            final String templateName, final List<String> recipients) {
        return EmailContent.builder()
                .title(emailTitle)
                .body(templateEngine.process(templateName, context))
                .recipients(recipients)
                .build();
    }

    private void addVersionsToContext(final String oldVersion, final String newVersion,  final Context context) {
        context.setVariable(OLD_VERSION, oldVersion);
        context.setVariable(NEW_VERSION, newVersion);
    }

    private void addIssuesToContext(final List<JiraIssue> jiraIssues, final List<GitHubIssue> gitHubIssues,
                               final Context context) {
        context.setVariable(JIRA_ISSUES, jiraIssues);
        context.setVariable(GITHUB_ISSUES, gitHubIssues);
    }

    private void validateProperties() {
        adminsEmailAddresses.forEach(e -> {
            Assert.hasText(e, ADMIN_EMAILS_NOT_CONFIGURED);
            Assert.isTrue(validateEmails(e), ADMIN_EMAILS_NOT_CORRECT);
        });
        subscribersEmailAddresses.forEach(e -> {
            Assert.hasText(e, SUBSCRIBERS_EMAILS_NOT_CONFIGURED);
            Assert.isTrue(validateEmails(e), SUBSCRIBERS_EMAILS_NOT_CORRECT);
        });
    }

    private boolean validateEmails(final String emailAdress) {
        final Matcher matcher = PATTERN.matcher(emailAdress);
        return matcher.matches();
    }

}

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
import com.epam.release.notes.agent.entity.version.IssueSourcePriority;
import com.epam.release.notes.agent.entity.version.VersionStatusInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Service
public class EmailTemplateNotificationService implements TemplateNotificationService {

    private static final String OLD_VERSION = "oldVersion";
    private static final String NEW_VERSION = "newVersion";
    private static final String JIRA_ISSUES = "jiraIssues";
    private static final String GITHUB_ISSUES = "gitHubIssues";
    private static final String EXCEPTION_MESSAGE = "This kind of version status is not handled in current method";
    private static final String MAJOR_CHANGES_SUBSCRIBER_EMAILS_NOT_CONFIGURED =
            "Major changes subscriber's emails is not configured!";
    private static final String MAJOR_CHANGES_SUBSCRIBER_EMAILS_NOT_CORRECT =
            "Major changes subscriber's emails is configured incorrectly!";
    private static final String MINOR_CHANGES_SUBSCRIBER_EMAILS_NOT_CONFIGURED = "Subscriber emails is not configured!";
    private static final String MINOR_CHANGES_SUBSCRIBER_EMAILS_NOT_CORRECT =
            "Subscribers emails is configured incorrectly!";
    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@"
            + "[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    private static final Pattern PATTERN = Pattern.compile(EMAIL_PATTERN);

    private final TemplateEngine templateEngine;
    private final String majorChangeEmailTemplateName;
    private final String emailToSubscribersTemplateName;
    private final String majorChangeEmailTitle;
    private final String minorChangeEmailTitle;
    private final List<String> majorVersionSubscriberEmailAddresses;
    private final List<String> minorVersionSubscriberEmailAddresses;

    public EmailTemplateNotificationService(
            final TemplateEngine templateEngine,
            @Value("${release.notes.agent.major.version.changed.email.template}")
            final String majorChangeEmailTemplateName,
            @Value("${release.notes.agent.minor.version.changed.email.template}")
            final String minorChangeEmailTemplateName,
            @Value("#{'${release.notes.agent.major.version.changed.subject}'?:" +
                    "'Cloud-pipeline major version change notification'}")
            final String majorChangeEmailTitle,
            @Value("#{'${release.notes.agent.minor.version.changed.subject}'?:" +
                    "'Cloud-pipeline minor version change notification'}")
            final String minorChangeEmailTitle,
            @Value("#{'${release.notes.agent.major.version.changed.subscriber.emails}'.split(',')?:}")
            final List<String> majorVersionChangeSubscriberEmail,
            @Value("#{'${release.notes.agent.minor.version.changed.subscriber.emails}'.split(',')?:}")
            final List<String> minorVersionChangeSubscriberEmail) {
        this.templateEngine = templateEngine;
        this.majorChangeEmailTemplateName = majorChangeEmailTemplateName;
        this.emailToSubscribersTemplateName = minorChangeEmailTemplateName;
        this.majorChangeEmailTitle = majorChangeEmailTitle;
        this.minorChangeEmailTitle = minorChangeEmailTitle;
        this.majorVersionSubscriberEmailAddresses = ListUtils.emptyIfNull(majorVersionChangeSubscriberEmail);
        this.minorVersionSubscriberEmailAddresses = ListUtils.emptyIfNull(minorVersionChangeSubscriberEmail);
    }


    @Override
    public EmailContent populate(final VersionStatusInfo versionStatusInfo) {
        validateProperties();
        final Context context = new Context(Locale.US);
        addVersionsToContext(versionStatusInfo.getOldVersion(), versionStatusInfo.getNewVersion(), context);
        addIssuesToContext(versionStatusInfo.getJiraIssues(), versionStatusInfo.getGitHubIssues(),
                versionStatusInfo.getSourcePriority(), context);

        switch (versionStatusInfo.getVersionStatus()) {
            case MAJOR_CHANGED:
                return createEmailContent(context, majorChangeEmailTitle, majorChangeEmailTemplateName,
                        ListUtils.emptyIfNull(majorVersionSubscriberEmailAddresses));
            case MINOR_CHANGED:
                return createEmailContent(context, minorChangeEmailTitle, emailToSubscribersTemplateName,
                        ListUtils.emptyIfNull(minorVersionSubscriberEmailAddresses));
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
                                    final IssueSourcePriority sourcePriority, final Context context) {
        final Set<Long> githubIssueNumbers;
        final List<JiraIssue> jiraIssuesFiltered;
        final List<GitHubIssue> gitHubIssuesFiltered;
        switch (sourcePriority) {
            case GITHUB:
                githubIssueNumbers = gitHubIssues.stream()
                        .map(GitHubIssue::getNumber)
                        .collect(Collectors.toCollection(HashSet::new));
                jiraIssuesFiltered = ListUtils.emptyIfNull(jiraIssues).stream()
                        .filter(j -> !StringUtils.isNumeric(j.getGithubId()) ||
                                isNotIssueDuplicate(githubIssueNumbers, Long.parseLong(j.getGithubId())))
                        .collect(Collectors.toList());
                gitHubIssuesFiltered = ListUtils.emptyIfNull(gitHubIssues);
                break;
            case JIRA:
                githubIssueNumbers = ListUtils.emptyIfNull(jiraIssues).stream()
                        .map(JiraIssue::getGithubId)
                        .filter(StringUtils::isNumeric)
                        .map(Long::parseLong)
                        .collect(Collectors.toCollection(HashSet::new));
                gitHubIssuesFiltered = ListUtils.emptyIfNull(gitHubIssues).stream()
                        .filter(g -> isNotIssueDuplicate(githubIssueNumbers, g.getNumber()))
                        .collect(Collectors.toList());
                jiraIssuesFiltered = ListUtils.emptyIfNull(jiraIssues);
                break;
            default:
                jiraIssuesFiltered = ListUtils.emptyIfNull(jiraIssues);
                gitHubIssuesFiltered = ListUtils.emptyIfNull(gitHubIssues);
                break;
        }
        log.debug(format("Issue source priority: %s. After filter there are: %s github and %s jira issues.",
                sourcePriority, gitHubIssuesFiltered.size(), jiraIssuesFiltered.size()));
        context.setVariable(JIRA_ISSUES, jiraIssuesFiltered);
        context.setVariable(GITHUB_ISSUES, gitHubIssuesFiltered);
    }

    private boolean isNotIssueDuplicate(final Set<Long> primaryIssueNumbers, final Long issueNumber) {
        return !primaryIssueNumbers.contains(issueNumber);
    }

    private void validateProperties() {
        majorVersionSubscriberEmailAddresses.forEach(e -> {
            Assert.hasText(e, MAJOR_CHANGES_SUBSCRIBER_EMAILS_NOT_CONFIGURED);
            Assert.isTrue(validateEmails(e), MAJOR_CHANGES_SUBSCRIBER_EMAILS_NOT_CORRECT);
        });
        minorVersionSubscriberEmailAddresses.forEach(e -> {
            Assert.hasText(e, MINOR_CHANGES_SUBSCRIBER_EMAILS_NOT_CONFIGURED);
            Assert.isTrue(validateEmails(e), MINOR_CHANGES_SUBSCRIBER_EMAILS_NOT_CORRECT);
        });
    }

    private boolean validateEmails(final String emailAddress) {
        final Matcher matcher = PATTERN.matcher(emailAddress);
        return matcher.matches();
    }

}

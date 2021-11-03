package com.epam.release.notes.agent.service.action.mail;

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.entity.jira.JiraIssue;
import com.epam.release.notes.agent.entity.mail.EmailContent;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
public class TemplateNotificationServiceImpl implements TemplateNotificationService{

    private static final String EMAIL_TO_ADMIN_TEMPLATE_NAME = "emailReleaseNotificationToAdmin";
    private static final String EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME = "emailReleaseNotificationToSubscribers";
    private static final String EMAIL_TO_ADMIN_TITLE = "Email to admin";
    private static final String EMAIL_TO_SUBSCRIBERS_TITLE = "Email to subscribers";

    private final SpringTemplateEngine templateEngine;

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
            final String htmlEmailContent = templateEngine.process(EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME, context);
            return EmailContent.builder()
                    .title(EMAIL_TO_SUBSCRIBERS_TITLE)
                    .body(htmlEmailContent)
                    .build();
        }
        final String htmlEmailContent = templateEngine.process(EMAIL_TO_ADMIN_TEMPLATE_NAME, context);
        return EmailContent.builder()
                .title(EMAIL_TO_ADMIN_TITLE)
                .body(htmlEmailContent)
                .build();
    }

    private boolean validateIssuesParameters(final List<JiraIssue> jiraIssues, final List<GitHubIssue> gitHubIssues) {
        return CollectionUtils.isNotEmpty(jiraIssues) || CollectionUtils.isNotEmpty(gitHubIssues);
    }

}

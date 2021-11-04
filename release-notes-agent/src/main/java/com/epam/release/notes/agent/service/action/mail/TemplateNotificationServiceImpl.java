package com.epam.release.notes.agent.service.action.mail;

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.entity.jira.JiraIssue;
import com.epam.release.notes.agent.entity.mail.EmailContent;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
public class TemplateNotificationServiceImpl implements TemplateNotificationService{


    private static final String EMAIL_TO_ADMIN_TITLE = "Email to admin";
    private static final String EMAIL_TO_SUBSCRIBERS_TITLE = "Email to subscribers";

    private final SpringTemplateEngine templateEngine;
    @Value("${release.notes.agent.path.to.admin.email}")
    private final String emailToAdminTemplateName;
    @Value("${release.notes.agent.path.to.subscribers.email}")
    private final String emailToSubscribersTemplateName;

//    public TemplateNotificationServiceImpl(SpringTemplateEngine templateEngine,
//                                           @Value("${release.notes.agent.path.to.admin.email}") String emailToAdminTemplateName,
//                                           @Value("${release.notes.agent.path.to.subscribers.email}") String emailToSubscribersTemplateName) {
//        this.templateEngine = templateEngine;
//        this.emailToAdminTemplateName = emailToAdminTemplateName;
//        this.emailToSubscribersTemplateName = emailToSubscribersTemplateName;
//    }

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
                    .title(EMAIL_TO_SUBSCRIBERS_TITLE)
                    .body(htmlEmailContent)
                    .build();
        }
        final String htmlEmailContent = templateEngine.process(emailToAdminTemplateName, context);
        return EmailContent.builder()
                .title(EMAIL_TO_ADMIN_TITLE)
                .body(htmlEmailContent)
                .build();
    }

    private boolean validateIssuesParameters(final List<JiraIssue> jiraIssues, final List<GitHubIssue> gitHubIssues) {
        return CollectionUtils.isNotEmpty(jiraIssues) || CollectionUtils.isNotEmpty(gitHubIssues);
    }

}

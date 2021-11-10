package com.epam.release.notes.agent.service.action.mail;

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.entity.jira.JiraIssue;
import com.epam.release.notes.agent.entity.mail.EmailContent;
import com.epam.release.notes.agent.entity.version.Version;
import com.epam.release.notes.agent.entity.version.VersionStatus;
import com.epam.release.notes.agent.service.version.ApplicationVersionService;
import com.epam.release.notes.agent.service.version.ApplicationVersionServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.util.List;
import java.util.stream.Stream;

public class EmailTemplateNotificationServiceTest {

    private static final String EMAIL_TEMPLATE_ENCODING = "UTF-8";
    private static final String OLD_VERSION = "55.55.55.55.a";
    private static final String NEW_MINOR_VERSION = "55.55.55.56.a";
    private static final String NEW_MAJOR_VERSION = "56.55.55.55.a";
    private static final String RESOLVER_PREFIX = "src/test/resources/mail/";
    private static final String RESOLVER_SUFFIX = ".html";
    private static final String EMAIL_TO_ADMIN_TEMPLATE_NAME = "email-release-notification-to-admin";
    private static final String EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME = "email-release-notification-to-subscribers";
    private static final String EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_TEMPLATE_NAME = "email-release-notification-to"
            + "-subscribers-without-issues";
    private static final String EMAIL_TO_ADMIN_TITLE = "email to admin";
    private static final String EMAIL_TO_SUBSCRIBERS_TITLE = "email to subscribers";
    private static final String EXPECTED_EMAIL_TO_SUBSCREBERS_BODY = "<!DOCTYPE html>\r\n" +
            "<html>\r\n" +
            "<head>\r\n" +
            "  \r\n" +
            "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\r\n" +
            "</head>\r\n" +
            "<body>\r\n" +
            "<p> Good day! </p>\r\n" +
            "<p> A new Cloud-pipeline version has been released. <br>\r\n" +
            "  Version changed from <span>55.55.55.55.a</span> to <span>55.55.55.56.a</span>\r\n" +
            "</p>\r\n" +
            "<p> Regards, <br /> &emsp; <em>The Cloud-Pipeline Team</em>\r\n" +
            "</p>\r\n" +
            "</body>\r\n" +
            "</html>";

    private EmailTemplateNotificationService emailTemplateNotificationService;

    @BeforeEach
    public void populateApplicationVersionService() {
        ApplicationVersionService applicationVersionService = Mockito.mock(ApplicationVersionServiceImpl.class);

        Mockito.when(applicationVersionService.getVersionStatus(Version.buildVersion(OLD_VERSION),
                Version.buildVersion(NEW_MINOR_VERSION))).thenAnswer(invocation -> VersionStatus.MINOR_CHANGED);
        Mockito.when(applicationVersionService.getVersionStatus(Version.buildVersion(OLD_VERSION),
                Version.buildVersion(NEW_MAJOR_VERSION))).thenAnswer(invocation -> VersionStatus.MAJOR_CHANGED);
        Mockito.when(applicationVersionService.getVersionStatus(Version.buildVersion(OLD_VERSION),
                Version.buildVersion(OLD_VERSION))).thenAnswer(invocation -> VersionStatus.NOT_CHANGED);

        final AbstractConfigurableTemplateResolver templateResolver = new FileTemplateResolver();
        templateResolver.setPrefix(RESOLVER_PREFIX);
        templateResolver.setSuffix(RESOLVER_SUFFIX);
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding(EMAIL_TEMPLATE_ENCODING);
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(templateResolver);

        emailTemplateNotificationService = new EmailTemplateNotificationService(templateEngine,
                applicationVersionService, EMAIL_TO_ADMIN_TEMPLATE_NAME, EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME,
                EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_TEMPLATE_NAME, EMAIL_TO_ADMIN_TITLE, EMAIL_TO_SUBSCRIBERS_TITLE
                );
    }

    @ParameterizedTest
    @MethodSource("provideParameters")
    public void shouldCreateCorrectEmail(final String oldVersion, final String newVersion,
                                         final List<JiraIssue> jiraIssues, final List<GitHubIssue> gitHubIssues,
                                         final String expectedEmailBody, final String expectedEmailTitle) {
        final EmailContent emailContent = emailTemplateNotificationService.populate(oldVersion, newVersion,
                jiraIssues, gitHubIssues);
        Assertions.assertEquals(expectedEmailBody, emailContent.getBody());
        Assertions.assertEquals(expectedEmailTitle, emailContent.getTitle());
    }

    static Stream<Arguments> provideParameters() {
        return Stream.of(
                Arguments.of(OLD_VERSION, NEW_MINOR_VERSION, null, null, EXPECTED_EMAIL_TO_SUBSCREBERS_BODY,
                  EMAIL_TO_SUBSCRIBERS_TITLE),
                Arguments.of()
        );
    }

}

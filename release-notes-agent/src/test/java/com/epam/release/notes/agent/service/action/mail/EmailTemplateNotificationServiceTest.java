package com.epam.release.notes.agent.service.action.mail;

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.entity.jira.JiraIssue;
import com.epam.release.notes.agent.entity.mail.EmailContent;
import com.epam.release.notes.agent.entity.version.VersionStatus;
import com.epam.release.notes.agent.entity.version.VersionStatusInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class EmailTemplateNotificationServiceTest {

    private static final String EMAIL_TEMPLATE_ENCODING = "UTF-8";
    private static final String OLD_VERSION = "55.55.55.55.a";
    private static final String NEW_MINOR_VERSION = "55.55.55.56.a";
    private static final String NEW_MAJOR_VERSION = "56.55.55.55.a";
    private static final String RESOLVER_PREFIX = "mail/";
    private static final String RESOLVER_SUFFIX = ".html";
    private static final String EMAIL_TO_ADMIN_TEMPLATE_NAME = "email-release-notification-to-admin";
    private static final String EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME = "email-release-notification-to-subscribers";
    private static final String EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_TEMPLATE_NAME = "email-release-notification-to"
            + "-subscribers-without-issues";
    private static final String EMAIL_TO_ADMIN_TITLE = "email to admin";
    private static final String EMAIL_TO_SUBSCRIBERS_TITLE = "email to subscribers";
    private static final String ONE = "1";
    private static final String TWO = "2";
    private static final String SMTH = "smth";
    private static final String SMTH_ELSE = "smth2";
    private static final String URL = "https://github.com";
    private static final String EXPECTED_EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_BODY = "<!DOCTYPE html>\r\n" +
            "<html>\r\n<head>\r\n  \r\n" +
            "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\r\n" +
            "</head>\r\n<body>\r\n<p> Good day! </p>\r\n<p> A new Cloud-pipeline version has been released. <br>\r\n" +
            "  Version changed from <span>55.55.55.55.a</span> to <span>55.55.55.56.a</span>\r\n" +
            "</p>\r\n<p> Regards, <br /> &emsp; <em>The Cloud-Pipeline Team</em>\r\n" +
            "</p>\r\n</body>\r\n</html>\r\n";
    private static final String EXPECTED_EMAIL_TO_SUBSCRIBERS_BODY = "<!DOCTYPE html>\r\n" +
            "<html>\r\n    <head>\r\n        \r\n" +
            "        <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\r\n" +
            "    </head>\r\n    <body>\r\n    <p> Good day! </p>\r\n" +
            "    <p> A new Cloud-pipeline version has been released. <br>\r\n" +
            "    Version changed from <span>55.55.55.55.a</span> to <span>55.55.55.56.a</span>\r\n" +
            "    </p>\r\n    <p> The release includes the following changes: <br>\r\n" +
            "    </p>\r\n    <p> &nbsp;&nbsp;&nbsp;&nbsp;Jira issues: </p>\r\n" +
            "    <ul>\r\n        <li >\r\n            <a href=\"https://github.com\">1 - smth</a>\r\n" +
            "        </li>\r\n        <li >\r\n            <a href=\"https://github.com\">2 - smth2</a>\r\n" +
            "        </li>\r\n    </ul>\r\n    <p> &nbsp;&nbsp;&nbsp;&nbsp;Github issues: </p>\r\n" +
            "    <ul>\r\n        <li>\r\n            <a href=\"https://github.com\">1 - smth</a>\r\n" +
            "        </li>\r\n        <li>\r\n            <a href=\"https://github.com\">2 - smth2</a>\r\n" +
            "        </li>\r\n    </ul>\r\n    <p> Regards, <br /> &emsp; <em>The Cloud-Pipeline Team</em>\r\n" +
            "    </p>\r\n    </body>\r\n</html>\r\n";
    private static final String EXPECTED_EMAIL_TO_ADMIN_BODY = "<!DOCTYPE html>\r\n" +
            "<html>\r\n    <head>\r\n        \r\n" +
            "        <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\r\n" +
            "    </head>\r\n    <body>\r\n    <p> Good day! </p>\r\n" +
            "    <p> Major version of Cloud-pipeline has been changed from <span>55.55.55.55.a</span> to " +
            "<span>56.55.55.55.a</span>. <br>\r\n    </p>\r\n    <p> Please, take an action. </p>\r\n" +
            "    <p> Regards, <br /> &emsp; <em>The Cloud-Pipeline Team</em>\r\n" +
            "    </p>\r\n    </body>\r\n</html>\r\n";

    private static EmailTemplateNotificationService emailTemplateNotificationService;

    @BeforeAll
    public static void populateApplicationVersionService() {
        final AbstractConfigurableTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix(RESOLVER_PREFIX);
        templateResolver.setSuffix(RESOLVER_SUFFIX);
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding(EMAIL_TEMPLATE_ENCODING);
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(templateResolver);

        emailTemplateNotificationService = new EmailTemplateNotificationService(templateEngine,
                EMAIL_TO_ADMIN_TEMPLATE_NAME, EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME,
                EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_TEMPLATE_NAME, EMAIL_TO_ADMIN_TITLE, EMAIL_TO_SUBSCRIBERS_TITLE
        );
    }

    @ParameterizedTest
    @MethodSource("provideParameters")
    public void shouldCreateCorrectEmail(
            final String oldVersion, final String newVersion, final List<JiraIssue> jiraIssues,
            final List<GitHubIssue> gitHubIssues, final VersionStatus versionStatus,
            final String expectedEmailBody, final String expectedEmailTitle
    ) {
        final VersionStatusInfo versionStatusInfo = VersionStatusInfo.builder()
                .oldVersion(oldVersion)
                .newVersion(newVersion)
                .jiraIssues(jiraIssues)
                .gitHubIssues(gitHubIssues)
                .versionStatus(versionStatus)
                .build();
        final EmailContent emailContent = emailTemplateNotificationService.populate(versionStatusInfo);
        Assertions.assertEquals(expectedEmailBody, emailContent.getBody());
        Assertions.assertEquals(expectedEmailTitle, emailContent.getTitle());
    }

    @Test
    public void shouldThrowIllegalStateExceptionDuringCreatingEmailBody() {
        final VersionStatusInfo versionStatusInfo = VersionStatusInfo.builder()
                .oldVersion(OLD_VERSION)
                .newVersion(OLD_VERSION)
                .jiraIssues(Collections.emptyList())
                .gitHubIssues(Collections.emptyList())
                .versionStatus(VersionStatus.NOT_CHANGED)
                .build();
        final Throwable thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> emailTemplateNotificationService.populate(versionStatusInfo));
        Assertions.assertNotNull(thrown.getMessage());
    }

    static Stream<Arguments> provideParameters() {
        return Stream.of(
                Arguments.of(OLD_VERSION, NEW_MINOR_VERSION, null, null, VersionStatus.MINOR_CHANGED,
                        EXPECTED_EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_BODY, EMAIL_TO_SUBSCRIBERS_TITLE),
                Arguments.of(OLD_VERSION, NEW_MINOR_VERSION, Collections.emptyList(), Collections.emptyList(),
                        VersionStatus.MINOR_CHANGED, EXPECTED_EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_BODY,
                        EMAIL_TO_SUBSCRIBERS_TITLE),
                Arguments.of(OLD_VERSION, NEW_MAJOR_VERSION, null, null, VersionStatus.MAJOR_CHANGED,
                        EXPECTED_EMAIL_TO_ADMIN_BODY, EMAIL_TO_ADMIN_TITLE),
                Arguments.of(OLD_VERSION, NEW_MINOR_VERSION,
                        Arrays.asList(JiraIssue.builder().id(ONE).title(SMTH).url(URL).build(),
                                JiraIssue.builder().id(TWO).title(SMTH_ELSE).url(URL).build()),
                        Arrays.asList(GitHubIssue.builder().id(1L).title(SMTH).htmlUrl(URL).build(),
                                GitHubIssue.builder().id(2L).title(SMTH_ELSE).htmlUrl(URL).build()),
                        VersionStatus.MINOR_CHANGED, EXPECTED_EMAIL_TO_SUBSCRIBERS_BODY, EMAIL_TO_SUBSCRIBERS_TITLE)
        );
    }

}

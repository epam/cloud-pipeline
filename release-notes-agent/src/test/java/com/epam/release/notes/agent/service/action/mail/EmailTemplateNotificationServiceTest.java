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
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.ArrayList;
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
    private static final String EMAIL_TO_ADMIN_TEMPLATE_NAME = "email-release-notification-to-admin.html";
    private static final String EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME = "email-release-notification-to-subscribers.html";
    private static final String EMAIL_TO_ADMIN_TITLE = "email to admin";
    private static final String EMAIL_TO_SUBSCRIBERS_TITLE = "email to subscribers";
    private static final String ONE = "1";
    private static final String TWO = "2";
    private static final String SMTH = "smth";
    private static final String SMTH_ELSE = "smth2";
    private static final String URL = "https://github.com";
    private static final List<String> ADMIN_MAILS = Arrays.asList("admin1@mail.com", "admin2@mail.com");
    private static final List<String> SUBSCRIBERS_MAILS = Arrays.asList("subscriber1@mail.com", "subscriber2@mail.com");
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String P = "    </p>";
    private static final String LI_CLOSE = "        </li>";
    private static final String LI_OPEN = "        <li>";
    private static final String SPACE = "    ";
    private static final String EXPECTED_EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_BODY = "<!DOCTYPE html>" + LINE_SEPARATOR +
            "<html>" + LINE_SEPARATOR + "    <head>" + LINE_SEPARATOR + "        " + LINE_SEPARATOR +
            "        <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">" + LINE_SEPARATOR +
            "    </head>" + LINE_SEPARATOR + "    <body>" + LINE_SEPARATOR + "    <p> Good day! </p>" + LINE_SEPARATOR +
            "    <p> A new Cloud-pipeline version has been released. <br>" + LINE_SEPARATOR +
            "    Version changed from <span>55.55.55.55.a</span> to <span>55.55.55.56.a</span>" + LINE_SEPARATOR +
            P + LINE_SEPARATOR + SPACE + LINE_SEPARATOR + SPACE + LINE_SEPARATOR + SPACE + LINE_SEPARATOR +
            SPACE + LINE_SEPARATOR + SPACE + LINE_SEPARATOR +
            "    <p> Regards, <br /> &emsp; <em>The Cloud-Pipeline Team</em>" + LINE_SEPARATOR +
            P + LINE_SEPARATOR + "    </body>" + LINE_SEPARATOR + "</html>" + LINE_SEPARATOR;
    private static final String EXPECTED_EMAIL_TO_SUBSCRIBERS_BODY = "<!DOCTYPE html>" + LINE_SEPARATOR +
            "<html>" + LINE_SEPARATOR + "    <head>" + LINE_SEPARATOR + "        " + LINE_SEPARATOR +
            "        <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">" + LINE_SEPARATOR +
            "    </head>" + LINE_SEPARATOR + "    <body>" + LINE_SEPARATOR + "    <p> Good day! </p>" + LINE_SEPARATOR +
            "    <p> A new Cloud-pipeline version has been released. <br>" + LINE_SEPARATOR +
            "    Version changed from <span>55.55.55.55.a</span> to <span>55.55.55.56.a</span>" + LINE_SEPARATOR +
            P + LINE_SEPARATOR + "    <p>" + LINE_SEPARATOR +
            "    The release includes the following changes: <br>" + LINE_SEPARATOR +
            "    </p>" + LINE_SEPARATOR + "    <p> &nbsp;&nbsp;&nbsp;&nbsp;Jira issues: </p>" + LINE_SEPARATOR +
            "    <ul>" + LINE_SEPARATOR + LI_OPEN + LINE_SEPARATOR +
            "            <a href=\"https://github.com\">1 - smth</a>" + LINE_SEPARATOR +
            LI_CLOSE + LINE_SEPARATOR + LI_OPEN + LINE_SEPARATOR +
            "            <a href=\"https://github.com\">2 - smth2</a>" + LINE_SEPARATOR +
            LI_CLOSE + LINE_SEPARATOR + "    </ul>" + LINE_SEPARATOR +
            "    <p> &nbsp;&nbsp;&nbsp;&nbsp;Github issues: </p>" + LINE_SEPARATOR +
            "    <ul>" + LINE_SEPARATOR + LI_OPEN + LINE_SEPARATOR +
            "            <a href=\"https://github.com\">1 - smth</a>" + LINE_SEPARATOR +
            LI_CLOSE + LINE_SEPARATOR + LI_OPEN + LINE_SEPARATOR +
            "            <a href=\"https://github.com\">2 - smth2</a>" + LINE_SEPARATOR +
            LI_CLOSE + LINE_SEPARATOR + "    </ul>" + LINE_SEPARATOR +
            "    <p> Regards, <br /> &emsp; <em>The Cloud-Pipeline Team</em>" + LINE_SEPARATOR +
            P + LINE_SEPARATOR + "    </body>" + LINE_SEPARATOR + "</html>" + LINE_SEPARATOR;
    private static final String EXPECTED_EMAIL_TO_ADMIN_BODY = "<!DOCTYPE html>" + LINE_SEPARATOR +
            "<html>" + LINE_SEPARATOR + "    <head>" + LINE_SEPARATOR + "        " + LINE_SEPARATOR +
            "        <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />" + LINE_SEPARATOR +
            "    </head>" + LINE_SEPARATOR + "    <body>" + LINE_SEPARATOR + "    <p> Good day! </p>" + LINE_SEPARATOR +
            "    <p> Major version of Cloud-pipeline has been changed from <span>55.55.55.55.a</span> to " +
            "<span>56.55.55.55.a</span>. <br>" + LINE_SEPARATOR + P + LINE_SEPARATOR +
            "    <p> Please, take an action. </p>" + LINE_SEPARATOR +
            "    <p> Regards, <br /> &emsp; <em>The Cloud-Pipeline Team</em>" + LINE_SEPARATOR +
            P + LINE_SEPARATOR + "    </body>" + LINE_SEPARATOR + "</html>" + LINE_SEPARATOR;

    private static EmailTemplateNotificationService emailTemplateNotificationService;

    @BeforeAll
    public static void populateApplicationVersionService() {
        final AbstractConfigurableTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix(RESOLVER_PREFIX);
        templateResolver.setCharacterEncoding(EMAIL_TEMPLATE_ENCODING);
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(templateResolver);

        emailTemplateNotificationService = new EmailTemplateNotificationService(templateEngine,
                EMAIL_TO_ADMIN_TEMPLATE_NAME, EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME,
                EMAIL_TO_ADMIN_TITLE, EMAIL_TO_SUBSCRIBERS_TITLE, ADMIN_MAILS, SUBSCRIBERS_MAILS
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
        Assertions.assertNotNull(emailContent.getRecipients());
        Assertions.assertNotEquals(Collections.emptyList(), emailContent.getRecipients());
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
                Arguments.of(OLD_VERSION, NEW_MINOR_VERSION, Collections.emptyList(), Collections.emptyList(),
                        VersionStatus.MINOR_CHANGED,
                        EXPECTED_EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_BODY, EMAIL_TO_SUBSCRIBERS_TITLE),
                Arguments.of(OLD_VERSION, NEW_MINOR_VERSION, null, null,
                        VersionStatus.MINOR_CHANGED,
                        EXPECTED_EMAIL_TO_SUBSCRIBERS_WITHOUT_ISSUES_BODY, EMAIL_TO_SUBSCRIBERS_TITLE),
                Arguments.of(OLD_VERSION, NEW_MAJOR_VERSION, Collections.emptyList(), Collections.emptyList(),
                        VersionStatus.MAJOR_CHANGED, EXPECTED_EMAIL_TO_ADMIN_BODY, EMAIL_TO_ADMIN_TITLE),
                Arguments.of(OLD_VERSION, NEW_MINOR_VERSION,
                        new ArrayList<>(Arrays.asList(JiraIssue.builder().id(ONE).title(SMTH).url(URL).build(),
                                JiraIssue.builder().id(TWO).title(SMTH_ELSE).url(URL).build())),
                        new ArrayList<>(Arrays.asList(GitHubIssue.builder().number(1L).title(SMTH).htmlUrl(URL).build(),
                                GitHubIssue.builder().number(2L).title(SMTH_ELSE).htmlUrl(URL).build())),
                        VersionStatus.MINOR_CHANGED, EXPECTED_EMAIL_TO_SUBSCRIBERS_BODY, EMAIL_TO_SUBSCRIBERS_TITLE)
        );
    }

}

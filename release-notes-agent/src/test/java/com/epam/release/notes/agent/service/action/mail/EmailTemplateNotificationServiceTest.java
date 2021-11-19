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
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EmailTemplateNotificationServiceTest {

    private static final String EMAIL_TEMPLATE_ENCODING = "UTF-8";
    private static final String OLD_VERSION = "55.55.55.55.a";
    private static final String NEW_MINOR_VERSION = "55.55.55.56.a";
    private static final String NEW_MAJOR_VERSION = "56.55.55.55.a";
    private static final String RESOLVER_PREFIX = "mail/";
    private static final String EMAIL_TO_ADMIN_TEMPLATE_NAME = "/email-release-notification-major-change.html";
    private static final String EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME = "/email-release-notification-minor-change.html";
    private static final String EMAIL_TO_ADMIN_TITLE = "email to admin";
    private static final String EMAIL_TO_SUBSCRIBERS_TITLE = "email to subscribers";
    private static final String ONE = "1";
    private static final String TWO = "2";
    private static final String SMTH = "smth";
    private static final String SMTH_ELSE = "smth2";
    private static final String KEY_1 = "TEST-1";
    private static final String KEY_2 = "TEST-2";
    private static final String URL = "https://github.com";
    private static final List<String> ADMIN_MAILS = Arrays.asList("admin1@mail.com", "admin2@mail.com");
    private static final List<String> SUBSCRIBERS_MAILS = Arrays.asList("subscriber1@mail.com", "subscriber2@mail.com");
    private static TemplateEngine expectedTemplateEngine;
    private static EmailTemplateNotificationService actualEmailTemplateNotificationService;

    @BeforeAll
    static void populateApplicationVersionService() {
        final String srcPath = Paths.get("src", "main", "resources", "mail").toFile().getAbsolutePath();
        final TemplateEngine actualTemplateEngine = constructTemplateEngine(srcPath, new FileTemplateResolver());
        expectedTemplateEngine = constructTemplateEngine(RESOLVER_PREFIX, new ClassLoaderTemplateResolver());
        actualEmailTemplateNotificationService = new EmailTemplateNotificationService(actualTemplateEngine,
                EMAIL_TO_ADMIN_TEMPLATE_NAME, EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME,
                EMAIL_TO_ADMIN_TITLE, EMAIL_TO_SUBSCRIBERS_TITLE, ADMIN_MAILS, SUBSCRIBERS_MAILS);
    }

    @ParameterizedTest
    @MethodSource("provideParameters")
    public void shouldCreateCorrectEmail(
            final String oldVersion, final String newVersion, final List<JiraIssue> jiraIssues,
            final List<GitHubIssue> gitHubIssues, final VersionStatus versionStatus,
            final String expectedEmailBody, final String expectedEmailTitle) {
        final VersionStatusInfo versionStatusInfo = VersionStatusInfo.builder()
                .oldVersion(oldVersion)
                .newVersion(newVersion)
                .jiraIssues(jiraIssues)
                .gitHubIssues(gitHubIssues)
                .versionStatus(versionStatus)
                .build();
        final EmailContent emailContent = actualEmailTemplateNotificationService.populate(versionStatusInfo);
        assertEquals(expectedEmailBody, emailContent.getBody());
        assertEquals(expectedEmailTitle, emailContent.getTitle());
        assertNotNull(emailContent.getRecipients());
        assertNotEquals(Collections.emptyList(), emailContent.getRecipients());
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
            () -> actualEmailTemplateNotificationService.populate(versionStatusInfo));
        assertNotNull(thrown.getMessage());
    }

    static Stream<Arguments> provideParameters() {
        final ArrayList<JiraIssue> jiraIssues = new ArrayList<>(Arrays.asList(
                JiraIssue.builder().id(ONE).title(SMTH).url(URL).key(KEY_1).build(),
                JiraIssue.builder().id(TWO).title(SMTH_ELSE).url(URL).key(KEY_2).build()));
        final ArrayList<GitHubIssue> gitHubIssues = new ArrayList<>(Arrays.asList(
                GitHubIssue.builder().number(1L).title(SMTH).htmlUrl(URL).build(),
                GitHubIssue.builder().number(2L).title(SMTH_ELSE).htmlUrl(URL).build()));
        return Stream.of(
                Arguments.of(
                        OLD_VERSION,
                        NEW_MINOR_VERSION,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        VersionStatus.MINOR_CHANGED,
                        expectedTemplateEngine.process(EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME, getContext(
                                NEW_MINOR_VERSION, Collections.emptyList(), Collections.emptyList())),
                        EMAIL_TO_SUBSCRIBERS_TITLE),
                Arguments.of(
                        OLD_VERSION,
                        NEW_MINOR_VERSION,
                        null, null,
                        VersionStatus.MINOR_CHANGED,
                        expectedTemplateEngine.process(EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME, getContext(
                                NEW_MINOR_VERSION, null, null)),
                        EMAIL_TO_SUBSCRIBERS_TITLE),
                Arguments.of(OLD_VERSION,
                        NEW_MAJOR_VERSION,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        VersionStatus.MAJOR_CHANGED,
                        expectedTemplateEngine.process(EMAIL_TO_ADMIN_TEMPLATE_NAME, getContext(
                                NEW_MAJOR_VERSION, Collections.emptyList(), Collections.emptyList())),
                        EMAIL_TO_ADMIN_TITLE),
                Arguments.of(OLD_VERSION,
                        NEW_MINOR_VERSION,
                        jiraIssues,
                        gitHubIssues,
                        VersionStatus.MINOR_CHANGED,
                        expectedTemplateEngine.process(EMAIL_TO_SUBSCRIBERS_TEMPLATE_NAME, getContext(
                                NEW_MINOR_VERSION, jiraIssues, gitHubIssues)),
                        EMAIL_TO_SUBSCRIBERS_TITLE)
        );
    }

    private static Context getContext(final String newVersion, final List<JiraIssue> jiraIssues,
                                      final List<GitHubIssue> gitHubIssues) {
        final Context context = new Context();
        context.setVariable("oldVersion", OLD_VERSION);
        context.setVariable("newVersion", newVersion);
        context.setVariable("jiraIssues", jiraIssues);
        context.setVariable("gitHubIssues", gitHubIssues);
        return context;
    }

    private static TemplateEngine constructTemplateEngine(final String prefix,
                                                          final AbstractConfigurableTemplateResolver templateResolver) {
        templateResolver.setPrefix(prefix);
        templateResolver.setCharacterEncoding(EMAIL_TEMPLATE_ENCODING);
        TemplateEngine testTemplateEngine = new TemplateEngine();
        testTemplateEngine.addTemplateResolver(templateResolver);
        return testTemplateEngine;
    }
}

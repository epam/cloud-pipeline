/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.NotifierLog;
import com.epam.pipeline.autotests.utils.PlatformApi;
import com.epam.pipeline.autotests.utils.TestCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * TC-NOTIFICATION-01: a run that is stopped again while it is already stopped sends no second email.
 * <p>
 * The emails are not read from a mailbox. The test reads the notifier's log, which names the subject and all the
 * recipients of each email, and shows whether the mail server accepted it. run-e2e.sh mounts the notifier's log
 * directory into the runner container, and the config names it (e2e.ui.notifier.logs.dir).
 * <p>
 * The emails of run1 are found by their subject, which ends with "Job #&lt;run1&gt; is now STOPPED": run1 is new, so only
 * this test can cause them, and no time window is needed. The emails are counted by message id, as the notifier logs an email
 * again on each attempt to send it. The API's status history of run1 is checked too: the API queues an email only
 * for a status that differs from the last one, and records that status, so a second email that is queued but not yet
 * sent shows there as a second STOPPED status.
 */
public class RunStatusNotificationTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    private static final String RUN_STATUS = "PIPELINE_RUN_STATUS";
    private static final String STOPPED = "STOPPED";
    // The default subject of the PIPELINE_RUN_STATUS template; a deployment may add a prefix
    private static final String RUN_STATUS_SUBJECT =
            "Job #$templateParameters[\"id\"] is now $templateParameters[\"status\"]";
    private static final int REPEATED_STOPS = 3;
    // The notifier checks its queue every 30 s, and sends at most 5 messages each time
    private static final Duration LOG_MAX_AGE = Duration.ofMinutes(2);
    private static final Duration EMAIL_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration LOG_POLL = Duration.ofSeconds(15);
    private static final Duration QUIET_TIME = Duration.ofMinutes(3);
    // An older notifier logs no subject: its emails of the last day show it before any run is launched
    private static final Duration FORMAT_CHECK_PERIOD = Duration.ofDays(1);

    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;

    @Test
    @TestCase(value = "TC-NOTIFICATION-01")
    public void noEmailIsSentIfRunStatusDidNotChange() {
        // Checked before any run is launched
        final NotifierLog notifierLog = new NotifierLog(C.NOTIFIER_LOGS_DIR)
                .assertCurrent(LOG_MAX_AGE)
                .assertSubjectsLogged(System.currentTimeMillis() - FORMAT_CHECK_PERIOD.toMillis());
        logoutIfNeeded();
        loginAs(admin);
        open(C.ROOT_ADDRESS);
        assertRunStatusEmailsAreSentToOwners();
        final JsonNode currentUser = PlatformApi.get("whoami");
        final String adminEmail = email(currentUser);
        final long since = System.currentTimeMillis();

        tools().perform(registry, group, tool, tab -> tab.run(this));
        final String run1 = getLastRunId();
        tools().perform(registry, group, tool, tab -> tab.run(this));
        final String run2 = getLastRunId();
        assertNotEquals(run2, run1, "run2 got the id of run1");

        final String stop = format("pipe stop -y %s", run1);
        final String stopped = format("RunID %s of \"[^\"]+\" stopped", run1);
        final String prompt = format("pipeline-%s", run2);
        final LogAO run2Log = runsMenu()
                .showLog(run2)
                .waitForSshLink();
        // "pipe stop" of a run that has already finished also says "stopped"
        assertRunStatus(run1, "RUNNING");
        run2Log.sshInNewTab(shell -> shell
                .waitUntilTextAppears(run2)
                .executeAndWaitForPrompt(stop, prompt)
                .assertOutputOfLastCommandMatches(stop, stopped));

        final String subject = format("Job #%s is now %s", run1, STOPPED);
        final NotifierLog.Email email = waitForSentEmail(notifierLog, subject, since);
        printEvidence(notifierLog.emailsWithSubjectEndingWith(subject, since));
        assertTrue(email.isSentTo(adminEmail),
                format("The email '%s' is not sent to the owner of run %s, %s: to %s", email.subject, run1,
                        adminEmail, email.to));

        // A new terminal: the first one was idle while the test waited for the email, which may take minutes
        run2Log.sshInNewTab(shell -> {
            shell.waitUntilTextAppears(run2);
            for (int i = 0; i < REPEATED_STOPS; i++) {
                shell.executeAndWaitForPrompt(stop, prompt)
                        .assertOutputOfLastCommandMatches(stop, stopped);
            }
        });

        sleep(QUIET_TIME.toMillis(), MILLISECONDS);
        final Map<Long, NotifierLog.Email> emails = notifierLog.emailsWithSubjectEndingWith(subject, since);
        printEvidence(emails);
        assertEquals(emails.size(), 1, format("The notifier log has %d emails '%s' (message ids %s)",
                emails.size(), subject, emails.keySet()));

        open(C.ROOT_ADDRESS);
        final JsonNode run = assertRunStatus(run1, STOPPED);
        assertTrue(run.path("owner").asText().equalsIgnoreCase(currentUser.path("userName").asText()),
                format("Run %s is owned by %s, not by %s", run1, run.path("owner").asText(),
                        currentUser.path("userName").asText()));
        final long stoppedStatuses = StreamSupport.stream(run.path("runStatuses").spliterator(), false)
                .filter(status -> STOPPED.equals(status.path("status").asText()))
                .count();
        assertEquals(stoppedStatuses, 1L, format("Run %s has %d STOPPED statuses: %s", run1, stoppedStatuses,
                run.path("runStatuses")));
    }

    /**
     * The case's prerequisite: the PIPELINE_RUN_STATUS email notification is enabled, informs the owner of the run,
     * covers the STOPPED status (no status selected means all), and its template has the default subject.
     */
    private void assertRunStatusEmailsAreSentToOwners() {
        final JsonNode settings = StreamSupport.stream(PlatformApi.get("notification/settings").spliterator(), false)
                .filter(entry -> RUN_STATUS.equals(entry.path("type").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(format("No %s email notification settings", RUN_STATUS)));
        assertTrue(settings.path("enabled").asBoolean(), format("The %s email notification is not enabled",
                RUN_STATUS));
        assertTrue(settings.path("keepInformedOwner").asBoolean(),
                format("The %s email notification has no 'Keep owners informed'", RUN_STATUS));
        final JsonNode statuses = settings.path("statusesToInform");
        assertTrue(statuses.size() == 0 || StreamSupport.stream(statuses.spliterator(), false)
                        .anyMatch(status -> STOPPED.equals(status.asText())),
                format("The %s email notification does not inform about %s: %s", RUN_STATUS, STOPPED, statuses));
        final String templateSubject = PlatformApi.get("notification/template/" + settings.path("templateId").asLong())
                .path("subject").asText();
        assertTrue(templateSubject.trim().endsWith(RUN_STATUS_SUBJECT), format("The subject of the %s template, "
                + "'%s', does not end with '%s'", RUN_STATUS, templateSubject, RUN_STATUS_SUBJECT));
    }

    /**
     * Fails unless the API shows the run in the status, and returns the run.
     */
    private static JsonNode assertRunStatus(final String runId, final String status) {
        final JsonNode run = PlatformApi.get("run/" + runId);
        assertEquals(run.path("status").asText(), status, format("Run %s is %s, not %s", runId,
                run.path("status").asText(), status));
        return run;
    }

    /**
     * The address the notifier sends the user's emails to: the "email" attribute, in any case.
     */
    private static String email(final JsonNode user) {
        return PlatformApi.email(user).orElseThrow(() -> new AssertionError(format("User %s has no email attribute",
                user.path("userName").asText())));
    }

    private static NotifierLog.Email waitForSentEmail(final NotifierLog notifierLog, final String subject,
                                                      final long since) {
        final long deadline = System.currentTimeMillis() + EMAIL_TIMEOUT.toMillis();
        while (true) {
            final Map<Long, NotifierLog.Email> emails = notifierLog.emailsWithSubjectEndingWith(subject, since);
            final NotifierLog.Email sent = emails.values().stream().filter(email -> email.sent).findFirst()
                    .orElse(null);
            if (sent != null) {
                return sent;
            }
            if (System.currentTimeMillis() > deadline) {
                printEvidence(emails);
                // The RUNNING emails of the two runs show an older notifier, if the last day had no email
                notifierLog.assertSubjectsLogged(since);
                fail(emails.isEmpty()
                        ? format("The notifier log has no email '%s' after %d min", subject,
                                EMAIL_TIMEOUT.toMinutes())
                        : format("The notifier did not send the email '%s' in %d min: message ids %s, dropped: %s",
                                subject, EMAIL_TIMEOUT.toMinutes(), emails.keySet(),
                                emails.values().stream().anyMatch(email -> email.dropped)));
            }
            sleep(LOG_POLL.toMillis(), MILLISECONDS);
        }
    }

    /**
     * Prints the notifier's log lines about the emails into the console log of the run, as the evidence of the case.
     */
    private static void printEvidence(final Map<Long, NotifierLog.Email> emails) {
        System.out.printf("TC-NOTIFICATION-01: %d email(s) in the notifier log%n", emails.size());
        emails.values().forEach(email -> email.lines.forEach(line -> System.out.println("    " + line)));
    }
}

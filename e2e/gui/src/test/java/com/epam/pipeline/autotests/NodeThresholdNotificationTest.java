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
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.NodeRequests;
import com.epam.pipeline.autotests.utils.NotifierLog;
import com.epam.pipeline.autotests.utils.PlatformApi;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.VmMonitorLog;
import com.epam.pipeline.autotests.utils.VmMonitorRecipients;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * 2870_2: the VM monitor emails the admin when a process on a node it checks holds more open files than the threshold,
 * in percent of the process's limit.
 * <p>
 * The VM monitor checks only the nodes labelled for it (CP_VM_MONITOR_NODE_MONITORING_NODE_LABEL=true), and the node
 * reporter measures only the processes it is set up for (CP_NODE_REPORTER_STATS_PROCS_INCLUDE). The test launches a
 * run without storage mounts, has the helper of run-e2e.sh label the run's node, and starts a process named "python"
 * in the run, as root, that holds 999 files under a limit of 1024. Root, as the node reporter can read the limits of a
 * process of its own user only.
 * <p>
 * The email is not read from a mailbox. The test reads the VM monitor's log, which shows that it checked the node,
 * found the process above the threshold and sent the notification to the API, and the notifier's log, which shows the
 * subject and the recipients of the email, and that the mail server accepted it. run-e2e.sh mounts both log
 * directories into the runner container read-only.
 * <p>
 * After a notification, the VM monitor sends no other one for a while
 * (CP_VM_MONITOR_NODE_THRESHOLD_RESEND_DELAY_MINUTES, 120 by default), whatever the node: it logs "Aborting
 * notifications for N more minutes" instead. So the test
 * launches the run only shortly before an earlier delay ends, waits as long as the VM monitor says, and keeps the
 * process running meanwhile: the VM monitor sends the notification at its first check after the delay. The test is the
 * last of the suite, so a delay that lasts when the suite starts has usually ended by then.
 * <p>
 * At the end, the test removes the label and then terminates the node on the "Cluster" page. The page shows no
 * TERMINATE button for a node with the label, as it takes any node with a cloud-pipeline/&lt;name&gt;=true label for a
 * node of the platform. The node is terminated rather than only stopped, so that it does not stay for another run. A
 * node that had the label before the test, e.g. left by an earlier run that was killed, keeps it, as the helper removes
 * only the labels it set: the test terminates it through the API call of the TERMINATE button.
 */
public class NodeThresholdNotificationTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    // The subject of the NODE-THRESHOLD email of the VM monitor: "[<platform name>] Nodes exceed thresholds"
    private static final String SUBJECT = "Nodes exceed thresholds";
    private static final String PROCESS = "python";
    private static final String STATS_TYPE = "NOFILE";
    private static final int OPEN_FILES_LIMIT = 1024;
    private static final int OPENED_FILES = 999;
    // 999 files, stdin, stdout and stderr of 1024: 97%
    private static final int MIN_USAGE_PERCENT = 90;
    private static final String WORK_DIR = "/tmp/cp-e2e-2870-2";
    private static final Duration PROCESS_LIFETIME = Duration.ofHours(4);
    private static final Duration DEFAULT_RESEND_DELAY = Duration.ofMinutes(120);
    // Both services log a check every 30-60 s
    private static final Duration LOG_MAX_AGE = Duration.ofMinutes(2);
    private static final Duration MONITORED_CHECK_PERIOD = Duration.ofMinutes(5);
    private static final Duration FORMAT_CHECK_PERIOD = Duration.ofDays(1);
    // Old enough to cover the log files that are written while the test runs
    private static final Duration BASELINE_PERIOD = Duration.ofMinutes(10);
    // A run takes a few minutes to start: launched that long before an earlier resend delay ends
    private static final Duration LAUNCH_LEAD = Duration.ofMinutes(5);
    // The VM monitor checks the nodes every minute, but its one scheduler thread may be busy with another monitor
    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration DETECTION_TIMEOUT = Duration.ofMinutes(5);
    // After a dropped notification: the VM monitor names the minutes left, rounded down, and checks every minute
    private static final Duration DELAY_MARGIN = Duration.ofMinutes(2);
    private static final Duration EMAIL_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration LOG_POLL = Duration.ofSeconds(15);
    // The notifier checks its queue every 30 s, and sends at most 5 messages each time
    private static final Duration QUIET_PERIOD = Duration.ofSeconds(75);
    private static final Duration QUIET_TIMEOUT = Duration.ofMinutes(10);
    // The email of an accepted notification follows within a minute or two; one that did not by then never comes
    private static final Duration EMAIL_PENDING_PERIOD = Duration.ofMinutes(5);
    private static final String NO_EXCEPTION = "no exception logged";

    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;

    private NodeRequests labels;
    // The node of the run, while it has the label the test set
    private String labelledNode;
    // The node of the run, if it had the label before the test, until the test has terminated it
    private String nodeLabelledBefore;

    @Test
    @TestCase(value = "2870_2")
    public void notificationIsSentWhenNodeExceedsThresholds() {
        // Checked before anything is launched
        labels = new NodeRequests(C.NODE_REQUESTS_DIR).assertWatched();
        assertReporterMeasuresPython();
        final Duration delay = resendDelay();
        final long now = System.currentTimeMillis();
        final VmMonitorLog vmMonitorLog = new VmMonitorLog(C.VM_MONITOR_LOGS_DIR)
                .assertCurrent(LOG_MAX_AGE)
                .assertNodesMonitored(now - MONITORED_CHECK_PERIOD.toMillis());
        final NotifierLog notifierLog = new NotifierLog(C.NOTIFIER_LOGS_DIR)
                .assertCurrent(LOG_MAX_AGE)
                .assertSubjectsLogged(now - FORMAT_CHECK_PERIOD.toMillis());
        logoutIfNeeded();
        loginAs(admin);
        open(C.ROOT_ADDRESS);
        final VmMonitorRecipients recipients = VmMonitorRecipients.load("2870_2");
        waitUntilResendDelayEnds(vmMonitorLog, delay);

        final long since = System.currentTimeMillis() - BASELINE_PERIOD.toMillis();
        final Set<String> cyclesBefore = cycleLines(vmMonitorLog, since);

        // Steps 1-5. No storage is mounted (CP_CAP_LIMIT_MOUNTS=None): the case needs none, and the run starts faster
        tools().perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .doNotMountStoragesSelect(true)
                .assertDoNotMountStoragesIsChecked()
                .launchTool(this, Utils.nameWithoutGroup(tool));
        final String runId = getLastRunId();
        final LogAO runLog = runsMenu()
                .showLog(runId)
                .waitForSshLink();

        // Step 6
        final NodeRequests.Response label = labels.label(runId);
        assertTrue(label.ok, format("The node of run %s is not labelled for the VM monitor: %s", runId,
                label.message));
        if (label.labelledBefore) {
            nodeLabelledBefore = label.node;
        } else {
            labelledNode = label.node;
        }
        System.out.printf("2870_2: %s%n", label.message);
        final VmMonitorLog.NodeCycle polled = waitForPoll(vmMonitorLog, label, cyclesBefore, since);
        printEvidence("VM monitor", evidence(polled, label.node));

        // Steps 7-12. The baseline is taken once the emails of earlier notifications are sent: they have the same
        // subject as the test's
        waitForQuietLogs(vmMonitorLog, notifierLog, since);
        final Set<String> cyclesBeforeProcess = cycleLines(vmMonitorLog, since);
        final Set<Long> emailsBeforeProcess = notifierLog.emailsWithSubjectEndingWith(SUBJECT, since).keySet();
        runLog.sshInNewTab(shell -> {
            final String prompt = format("pipeline-%s", runId);
            shell.waitUntilTextAppears(runId);
            // The output is followed by the prompt with no line break: ";" ends each value
            final String uid = "echo \"uid=$(id -u);\"";
            shell.executeAndWaitForPrompt(uid, prompt)
                    .assertOutputOfLastCommandMatches(uid, "uid=0;");
            // Short commands: the terminal helpers find a command on one line of the screen
            for (final String command : processCommands()) {
                shell.executeAndWaitForPrompt(command, prompt);
            }
            final String comm = "p=$(cat pid); echo \"comm=$(cat /proc/$p/comm);\"";
            shell.executeAndWaitForPrompt(comm, prompt)
                    .assertOutputOfLastCommandMatches(comm, "comm=" + PROCESS + ";");
            final String fds = "echo \"fds=$(ls /proc/$p/fd | wc -l | tr -d ' ');\"";
            shell.executeAndWaitForPrompt(fds, prompt)
                    .assertOutputOfLastCommandMatches(fds, "fds=(10[01]\\d|102[0-4]);");
            final String limits = "grep 'Max open files' /proc/$p/limits";
            shell.executeAndWaitForPrompt(limits, prompt)
                    .assertOutputOfLastCommandMatches(limits,
                            format("Max open files\\s+%1$d\\s+%1$d\\s", OPEN_FILES_LIMIT));
        });

        // Step 13
        final VmMonitorLog.NodeCycle notified = waitForNotification(vmMonitorLog, label.node, cyclesBeforeProcess,
                since, delay);
        printEvidence("VM monitor", evidence(notified, label.node));
        assertTrue(Boolean.TRUE.equals(notified.sent), format("The VM monitor did not send the notification about "
                + "node %s to the API, or the API did not accept it: %s", label.node, notified.lines));

        // Step 14
        final Map<Long, NotifierLog.Email> emails = waitForEmails(notifierLog, emailsBeforeProcess, vmMonitorLog,
                cyclesBeforeProcess, since);
        printEvidence("notifier", emails.values().stream()
                .flatMap(email -> email.lines.stream())
                .collect(Collectors.toList()));
        if (emails.size() > 1) {
            System.out.printf("2870_2: the VM monitor sent %d notifications meanwhile, which have the same subject%n",
                    emails.size());
        }
        emails.values().forEach(recipients::assertSentTo);

        // After. The "Cluster" page shows no TERMINATE button for a node with a cloud-pipeline/<name>=true label, as
        // it takes such a node for a node of the platform (node-roles.js)
        open(C.ROOT_ADDRESS);
        if (labelledNode != null) {
            // The test labelled the node: the label is removed first
            final NodeRequests.Response unlabelled = labels.unlabel(labelledNode);
            assertTrue(unlabelled.ok, format("Unable to remove the label from node %s: %s", labelledNode,
                    unlabelled.message));
            System.out.printf("2870_2: %s%n", unlabelled.message);
            labelledNode = null;
            navigationMenu()
                    .clusterNodes()
                    .removeNode(runId)
                    .validateThereIsNoNode(runId);
        } else {
            // The node had the label before the test, e.g. left by an earlier run that was killed, and the helper
            // removes only the labels it set: the node is terminated through the API call of the TERMINATE button
            System.out.printf("2870_2: node %s had the label before the test, so the page shows no TERMINATE button "
                    + "for it: it is terminated through the API%n", label.node);
            PlatformApi.call("DELETE", "cluster/node/" + label.node, null);
            nodeLabelledBefore = null;
            navigationMenu()
                    .clusterNodes()
                    .validateThereIsNoNode(runId);
        }
    }

    /**
     * Cleans up the node of the run if the test did not get to its end. If the test labelled the node, the label is
     * removed, and the base class then stops the run and terminates its node on the "Cluster" page. A node that had
     * the label before the test keeps it, so the page shows no TERMINATE button for it: it is terminated here through
     * the API call of that button, which also stops the run.
     */
    @AfterClass(alwaysRun = true)
    public void cleanUpNode() {
        if (labels != null && labelledNode != null) {
            try {
                final NodeRequests.Response response = labels.unlabel(labelledNode);
                System.out.printf("2870_2: %s%n", response.message);
            } catch (RuntimeException | AssertionError e) {
                System.out.printf("2870_2: unable to remove the label from node %s, run-e2e.sh removes it after the "
                        + "tests: %s%n", labelledNode, e.getMessage());
            }
        }
        if (nodeLabelledBefore != null) {
            try {
                open(C.ROOT_ADDRESS);
                PlatformApi.call("DELETE", "cluster/node/" + nodeLabelledBefore, null);
                System.out.printf("2870_2: node %s, which had the label before the test, is terminated through the "
                        + "API%n", nodeLabelledBefore);
            } catch (RuntimeException | AssertionError e) {
                System.out.printf("2870_2: unable to terminate node %s, which had the label before the test: %s%n",
                        nodeLabelledBefore, e.getMessage());
            }
        }
    }

    /**
     * The commands that start the process, detached from the terminal, which the test closes, in {@link #WORK_DIR}:
     * a "python" link to the platform's Python, so that the node reporter sees the process by that name, and a script
     * that opens the files and writes its pid.
     */
    private static List<String> processCommands() {
        return Arrays.asList(
                format("mkdir -p %1$s && cd %1$s", WORK_DIR),
                "ln -sf \"${CP_PYTHON_PATH:-$(command -v python3)}\" " + PROCESS,
                "echo 'import os, time' > files.py",
                format("echo 'files = [open(\"/dev/null\") for i in range(%d)]' >> files.py", OPENED_FILES),
                "echo 'open(\"pid\", \"w\").write(str(os.getpid()))' >> files.py",
                format("echo 'time.sleep(%d)' >> files.py", PROCESS_LIFETIME.getSeconds()),
                format("(ulimit -n %d && nohup ./%s files.py > out 2>&1 < /dev/null &)", OPEN_FILES_LIMIT, PROCESS),
                "sleep 3; cat out");
    }

    private static void assertReporterMeasuresPython() {
        // The node reporter splits the list at commas and does not trim the names
        final List<String> processes = Arrays.asList(C.NODE_REPORTER_PROCS.split(","));
        assertTrue(processes.contains(PROCESS), format("The node reporter does not measure %s processes (%s, "
                + "e2e.ui.node.reporter.procs): add %s to CP_NODE_REPORTER_STATS_PROCS_INCLUDE of the cp-node-reporter "
                + "DaemonSet", PROCESS, C.NODE_REPORTER_PROCS, PROCESS));
    }

    private static Duration resendDelay() {
        final String minutes = C.VM_MONITOR_NODE_RESEND_DELAY_MINUTES.trim();
        if (minutes.isEmpty()) {
            return DEFAULT_RESEND_DELAY;
        }
        try {
            return Duration.ofMinutes(Long.parseLong(minutes));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(format("e2e.ui.vm.monitor.node.resend.delay.minutes is not a number "
                    + "of minutes: %s", minutes), e);
        }
    }

    /**
     * Waits until shortly before the resend delay of an earlier node threshold notification ends, if one lasts: the
     * VM monitor would drop the test's notification until then.
     */
    private static void waitUntilResendDelayEnds(final VmMonitorLog vmMonitorLog, final Duration delay) {
        final long since = System.currentTimeMillis() - delay.plus(BASELINE_PERIOD).toMillis();
        final Duration left = vmMonitorLog.nodeNotificationsResendDelayLeft(delay, since);
        if (left.isZero()) {
            return;
        }
        System.out.printf("2870_2: the VM monitor sent a node threshold notification %d min ago, and sends no other "
                + "one for %d more min%n", delay.minus(left).toMinutes(), left.toMinutes());
        if (left.compareTo(LAUNCH_LEAD) > 0) {
            final Duration wait = left.minus(LAUNCH_LEAD);
            System.out.printf("2870_2: waiting %d min before the run is launched%n", wait.toMinutes());
            sleep(wait.toMillis(), MILLISECONDS);
        }
    }

    /**
     * The checks of the nodes that have finished: one still running is new for the baseline, as its notification may
     * follow.
     */
    private static Set<String> cycleLines(final VmMonitorLog vmMonitorLog, final long since) {
        return vmMonitorLog.nodeCycles(since).stream()
                .filter(cycle -> cycle.finished)
                .map(cycle -> cycle.line)
                .collect(Collectors.toSet());
    }

    private static List<VmMonitorLog.NodeCycle> newCycles(final VmMonitorLog vmMonitorLog, final Set<String> before,
                                                          final long since) {
        return vmMonitorLog.nodeCycles(since).stream()
                .filter(cycle -> !before.contains(cycle.line))
                .collect(Collectors.toList());
    }

    /**
     * Waits until a check of the nodes after the label read the stats of the node. A failed poll is retried at the
     * next check, e.g. while the node reporter on the node still starts.
     */
    private static VmMonitorLog.NodeCycle waitForPoll(final VmMonitorLog vmMonitorLog,
                                                      final NodeRequests.Response label,
                                                      final Set<String> before, final long since) {
        final long deadline = System.currentTimeMillis() + POLL_TIMEOUT.toMillis();
        while (true) {
            VmMonitorLog.NodePoll failed = null;
            for (final VmMonitorLog.NodeCycle cycle : newCycles(vmMonitorLog, before, since)) {
                final VmMonitorLog.NodePoll poll = cycle.finished ? cycle.poll(label.node) : null;
                if (poll != null && !poll.failed) {
                    return cycle;
                }
                failed = poll != null ? poll : failed;
            }
            if (System.currentTimeMillis() > deadline) {
                if (failed != null) {
                    printEvidence("VM monitor", failed.lines);
                    fail(format("The VM monitor did not get the stats of node %s from %s in %d min: %s", label.node,
                            failed.pod, POLL_TIMEOUT.toMinutes(), Objects.toString(failed.cause, NO_EXCEPTION)));
                }
                fail(format("The VM monitor did not check node %s in %d min after it was labelled %s=true: no "
                        + "cp-node-reporter pod runs on it, or the VM monitor selects the nodes by another label",
                        label.node, POLL_TIMEOUT.toMinutes(), label.label));
            }
            sleep(LOG_POLL.toMillis(), MILLISECONDS);
        }
    }

    /**
     * Waits until a check of the nodes found the process on the node above the threshold, and the VM monitor sent the
     * notification, or the log shows that the API did not accept it. While a resend delay lasts, the VM monitor
     * drops the notification and names the minutes left: the wait is extended by them, at most by the whole delay.
     */
    private static VmMonitorLog.NodeCycle waitForNotification(final VmMonitorLog vmMonitorLog, final String node,
                                                              final Set<String> before, final long since,
                                                              final Duration delay) {
        final long start = System.currentTimeMillis();
        final long latest = start + delay.plus(DETECTION_TIMEOUT).toMillis();
        long deadline = start + DETECTION_TIMEOUT.toMillis();
        String reportedAbort = null;
        while (true) {
            final List<VmMonitorLog.NodeCycle> cycles = newCycles(vmMonitorLog, before, since);
            for (final VmMonitorLog.NodeCycle cycle : cycles) {
                final VmMonitorLog.NodePoll poll = cycle.poll(node);
                if (poll == null || !cycle.finished || !isAbove(poll)) {
                    continue;
                }
                if (cycle.proceeded && cycle.sent != null) {
                    return cycle;
                }
                if (cycle.abortedForMinutes != null && !cycle.line.equals(reportedAbort)) {
                    reportedAbort = cycle.line;
                    deadline = Math.min(latest, System.currentTimeMillis()
                            + Duration.ofMinutes(cycle.abortedForMinutes).plus(DELAY_MARGIN).toMillis());
                    System.out.printf("2870_2: the VM monitor dropped the notification: an earlier one was sent less "
                            + "than %d min ago, %d more min to wait (until %s UTC)%n", delay.toMinutes(),
                            cycle.abortedForMinutes, cycle.abortedUntil);
                }
            }
            if (System.currentTimeMillis() > deadline) {
                final List<String> lines = new ArrayList<>();
                cycles.stream()
                        .filter(cycle -> cycle.poll(node) != null)
                        .reduce((first, second) -> second)
                        .ifPresent(cycle -> lines.addAll(evidence(cycle, node)));
                printEvidence("VM monitor", lines);
                fail(format("In %d min, no check of node %s found %s above the %s threshold with a notification "
                        + "sent: %s (the last check of the node is in the console log)",
                        (System.currentTimeMillis() - start) / 60_000, node, PROCESS, STATS_TYPE,
                        diagnosis(cycles, node)));
            }
            sleep(LOG_POLL.toMillis(), MILLISECONDS);
        }
    }

    /**
     * What the last check that polled the node lacks.
     */
    private static String diagnosis(final List<VmMonitorLog.NodeCycle> cycles, final String node) {
        final VmMonitorLog.NodeCycle last = cycles.stream()
                .filter(cycle -> cycle.finished && cycle.poll(node) != null)
                .reduce((first, second) -> second)
                .orElse(null);
        if (last == null) {
            return format("no check of the nodes polled node %s", node);
        }
        final VmMonitorLog.NodePoll poll = last.poll(node);
        if (poll.failed) {
            return format("the VM monitor did not get the stats of node %s from %s: %s", node, poll.pod,
                    Objects.toString(poll.cause, NO_EXCEPTION));
        }
        final Optional<VmMonitorLog.Usage> usage = poll.usages.stream()
                .filter(it -> it.type.equals(STATS_TYPE) && it.process.equals(PROCESS))
                .findFirst();
        if (!usage.isPresent()) {
            return format("the node reporter did not report a %1$s process on node %2$s. It reports only the "
                    + "processes of CP_NODE_REPORTER_STATS_PROCS_INCLUDE, and stops listing them at a process whose "
                    + "limits it may not read, e.g. a %1$s process of another user than root: see "
                    + "/opt/node-reporter/logs/%2$s/stats.log on the node", PROCESS, node);
        }
        if (usage.get().percent < MIN_USAGE_PERCENT) {
            return format("%s uses %d%% of its limit, not %d%% or more", PROCESS, usage.get().percent,
                    MIN_USAGE_PERCENT);
        }
        if (poll.exceeded.stream().noneMatch(it -> it.type.equals(STATS_TYPE) && it.process.equals(PROCESS))) {
            return format("%s uses %d%% of its limit, but the VM monitor did not find it above the %s threshold "
                    + "(CP_VM_MONITOR_NODE_THRESHOLDS)", PROCESS, usage.get().percent, STATS_TYPE);
        }
        if (last.abortedForMinutes != null) {
            return format("the VM monitor dropped the notification, a resend delay lasted %d more min",
                    last.abortedForMinutes);
        }
        return "the VM monitor found it above the threshold, but the log does not show the notification";
    }

    private static boolean isAbove(final VmMonitorLog.NodePoll poll) {
        return poll.usages.stream().anyMatch(usage -> usage.type.equals(STATS_TYPE) && usage.process.equals(PROCESS)
                && usage.percent >= MIN_USAGE_PERCENT)
                && poll.exceeded.stream().anyMatch(usage -> usage.type.equals(STATS_TYPE)
                && usage.process.equals(PROCESS));
    }

    /**
     * Waits until the emails of the node threshold notifications that the VM monitor sent before are sent: until the
     * last check of the nodes is not sending a notification, the notifier log has at least as many emails with the
     * subject as the VM monitor log has notifications the API accepted (both logs start a new file every day, and the
     * same files are read) if the last of these is recent ({@link #EMAIL_PENDING_PERIOD}), and no new email appears for
     * {@link #QUIET_PERIOD}. Otherwise such an email, sent after the test took its baseline, would be taken for the
     * test's. Only the last check can still be sending, and only a recent notification's email can still come: an
     * earlier check that never finished was cut off, and an email that is not in the log minutes after its
     * notification never comes (e.g. if the notifier found no recipient).
     */
    private static void waitForQuietLogs(final VmMonitorLog vmMonitorLog, final NotifierLog notifierLog,
                                         final long since) {
        final long deadline = System.currentTimeMillis() + QUIET_TIMEOUT.toMillis();
        Set<Long> emails = notifierLog.emailsWithSubjectEndingWith(SUBJECT, since).keySet();
        long quietSince = System.currentTimeMillis();
        while (true) {
            final List<VmMonitorLog.NodeCycle> cycles = vmMonitorLog.nodeCycles(since);
            final VmMonitorLog.NodeCycle last = cycles.isEmpty() ? null : cycles.get(cycles.size() - 1);
            final boolean inProgress = last != null && !last.finished && last.proceeded && last.sent == null;
            final List<VmMonitorLog.NodeCycle> acceptedCycles = cycles.stream()
                    .filter(cycle -> Boolean.TRUE.equals(cycle.sent))
                    .collect(Collectors.toList());
            final long accepted = acceptedCycles.size();
            final boolean pending = emails.size() < accepted
                    && isRecent(acceptedCycles.get(acceptedCycles.size() - 1), vmMonitorLog.lastLineTime(since));
            if (inProgress || pending) {
                quietSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - quietSince >= QUIET_PERIOD.toMillis()) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                fail(format("The emails '%s' did not settle in %d min: the VM monitor log has %d node threshold "
                        + "notification(s) the API accepted (%s), and the notifier log %d email(s)", SUBJECT,
                        QUIET_TIMEOUT.toMinutes(), accepted, inProgress ? "one in progress" : "none in progress",
                        emails.size()));
            }
            sleep(LOG_POLL.toMillis(), MILLISECONDS);
            final Set<Long> current = notifierLog.emailsWithSubjectEndingWith(SUBJECT, since).keySet();
            if (!current.equals(emails)) {
                emails = current;
                quietSince = System.currentTimeMillis();
            }
        }
    }

    /**
     * Whether the check of the nodes started less than {@link #EMAIL_PENDING_PERIOD} before the last line of the VM
     * monitor log, both in the VM monitor's clock. Not recent if a time does not parse: the counts are then not waited
     * for, and the quiet period alone settles the emails.
     */
    private static boolean isRecent(final VmMonitorLog.NodeCycle cycle, final LocalDateTime lastLine) {
        return cycle.time != null && lastLine != null
                && Duration.between(cycle.time, lastLine).compareTo(EMAIL_PENDING_PERIOD) < 0;
    }

    /**
     * Waits until the notifier's log has one new email with the subject for each node threshold notification that
     * the VM monitor sent since the baseline, all of them accepted by the mail server, and returns them. The subject
     * is the same for every such notification.
     */
    private static Map<Long, NotifierLog.Email> waitForEmails(final NotifierLog notifierLog,
                                                              final Set<Long> emailsBefore,
                                                              final VmMonitorLog vmMonitorLog,
                                                              final Set<String> cyclesBefore, final long since) {
        final long deadline = System.currentTimeMillis() + EMAIL_TIMEOUT.toMillis();
        while (true) {
            final long expected = newCycles(vmMonitorLog, cyclesBefore, since).stream()
                    .filter(cycle -> Boolean.TRUE.equals(cycle.sent))
                    .count();
            final Map<Long, NotifierLog.Email> emails = notifierLog.emailsWithSubjectEndingWith(SUBJECT, since);
            emails.keySet().removeAll(emailsBefore);
            if (emails.size() == expected && emails.values().stream().allMatch(email -> email.sent)) {
                return emails;
            }
            if (System.currentTimeMillis() > deadline) {
                printEvidence("notifier", emails.values().stream()
                        .flatMap(email -> email.lines.stream())
                        .collect(Collectors.toList()));
                notifierLog.assertSubjectsLogged(since);
                fail(format("In %d min, the notifier log has %d new email(s) '%s' (message ids %s, sent: %d, dropped: "
                        + "%d), and the VM monitor sent %d notification(s)", EMAIL_TIMEOUT.toMinutes(),
                        emails.size(), SUBJECT, emails.keySet(),
                        emails.values().stream().filter(email -> email.sent).count(),
                        emails.values().stream().filter(email -> email.dropped).count(), expected));
            }
            sleep(LOG_POLL.toMillis(), MILLISECONDS);
        }
    }

    /**
     * The lines of a check about the node: the start, the poll of the node, and the notification.
     */
    private static List<String> evidence(final VmMonitorLog.NodeCycle cycle, final String node) {
        final List<String> lines = new ArrayList<>();
        lines.add(cycle.lines.get(0));
        final VmMonitorLog.NodePoll poll = cycle.poll(node);
        if (poll != null) {
            lines.addAll(poll.lines);
        }
        lines.addAll(cycle.lines.subList(1, cycle.lines.size()));
        return lines;
    }

    /**
     * Prints a service's log lines about the node or its email into the console log of the run, as the evidence of
     * the case.
     */
    private static void printEvidence(final String service, final List<String> lines) {
        System.out.printf("2870_2: %d line(s) of the %s log%n", lines.size(), service);
        lines.forEach(line -> System.out.println("    " + line));
    }
}

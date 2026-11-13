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
package com.epam.pipeline.autotests.utils;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.testng.Assert.assertTrue;

/**
 * The log of the VM monitor (cp-vm-monitor), read from its log directory: vm_monitor.log, and the files it is rolled
 * over to every day, vm_monitor-yyyyMMdd.log. Its lines are {@code [<level>] <date> <time> [<thread>] <class> -
 * <message>}; the lines of a stack trace do not have this form, and are skipped, except the exception's own line
 * after the error of a failed poll of a node, which is kept as the poll's cause.
 * <p>
 * For a certificate file, the VM monitor logs:
 * <ul>
 * <li>{@code Checking new or changed certificate file <path>}: the check of new and changed files found it, or
 * {@code Reading certificate from file <path>}: any scan read it;</li>
 * <li>{@code Certificate for <subject> expires after <date>}: it read the certificate;</li>
 * <li>{@code Sending <subject> certificate expiration notification}: the certificate expires soon, and the
 * notification is sent to the API;</li>
 * <li>{@code Successfully sent notification using API client}, or {@code An error occurred during sending
 * notification using API client}: whether the API accepted it (the next of these lines of the same thread). If the
 * scan ends first ({@code Finished ... monitoring.} or {@code Failed ... monitoring.}), the notification was not
 * sent. The VM monitor has one scheduler thread, so the next line of this kind may be of another monitor.</li>
 * </ul>
 * <p>
 * Every minute, the node monitor checks the nodes labelled for it (a cycle), and logs:
 * <ul>
 * <li>{@code Starting nodes monitoring...} and at the end {@code Finished nodes monitoring.} or {@code Failed nodes
 * monitoring.}, on the scheduler thread; {@code Skipping disabled nodes monitoring...} if it is turned off;</li>
 * <li>for each node, on a thread of its own pool: {@code Retrieving stats from <reporter pod> for <node>...}, then
 * {@code Resolved <type> <n>% usage of <process>.} for each process the node reporter measures, and
 * {@code Detected <type> <threshold>% threshold exceeding of <process>.} for one above the threshold; or
 * {@code Failed to resolve stats from <reporter pod> for <node>.};</li>
 * <li>if a process is above the threshold, on the scheduler thread: {@code Proceeding with notifications...}, then
 * whether the API accepted the notification (as above), or {@code Aborting notifications for <m> more minutes til
 * <time>...}: after a notification, the VM monitor sends no other one for a while (the resend delay).</li>
 * </ul>
 * The VM monitor logs {@code Starting VMMonitorApplication ...} when it starts, which also ends the resend delay.
 */
public class VmMonitorLog {

    private static final Pattern LINE =
            Pattern.compile("^\\[\\s*\\w+\\s*\\] (\\S+ \\S+) \\[([^\\]]*)\\] \\S+ - (.*)$");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String CHANGES_STARTED = "Starting new and changed PKI certificates monitoring...";
    private static final String CHANGES_FINISHED = "Finished new and changed PKI certificates monitoring.";
    private static final String CHANGES_DISABLED = "Skipping disabled monitoring of new and changed PKI certificates";
    private static final String CHECKING = "Checking new or changed certificate file ";
    private static final String READING = "Reading certificate from file ";
    private static final String EXPIRES = "Certificate for %s expires after ";
    private static final Pattern SENDING = Pattern.compile("^Sending (.*) certificate expiration notification$");
    private static final String API_SENT = "Successfully sent notification using API client";
    private static final String API_FAILED = "An error occurred during sending notification using API client";
    private static final Pattern SCAN_END = Pattern.compile("^(Finished|Failed) .+ monitoring\\.$");
    private static final String NODES_STARTED = "Starting nodes monitoring...";
    private static final Pattern NODES_END = Pattern.compile("^(Finished|Failed) nodes monitoring\\.$");
    private static final String NODES_DISABLED = "Skipping disabled nodes monitoring...";
    private static final Pattern RETRIEVING = Pattern.compile("^Retrieving stats from (\\S+) for (\\S+)\\.\\.\\.$");
    private static final Pattern RETRIEVE_FAILED =
            Pattern.compile("^Failed to resolve stats from (\\S+) for (\\S+)\\.$");
    private static final Pattern RESOLVED = Pattern.compile("^Resolved (\\w+) (-?\\d+)% usage of (.+)\\.$");
    private static final Pattern DETECTED = Pattern.compile("^Detected (\\w+) (\\d+)% threshold exceeding of (.+)\\.$");
    private static final String PROCEEDING = "Proceeding with notifications...";
    private static final Pattern ABORTING =
            Pattern.compile("^Aborting notifications for (\\d+) more minutes til (\\S+)\\.\\.\\.$");
    private static final String APPLICATION_STARTING = "Starting VMMonitorApplication ";

    private final RollingLog log;

    public VmMonitorLog(final String dir) {
        assertTrue(dir != null && !dir.trim().isEmpty(), "No VM monitor log directory: set "
                + "e2e.ui.vm.monitor.logs.dir (run-e2e.sh mounts it at /vm-monitor-logs)");
        this.log = new RollingLog(Paths.get(dir), "VM monitor", "vm_monitor");
    }

    /**
     * Fails unless vm_monitor.log is readable and was written within the last {@code maxAge}: the VM monitor logs its
     * checks every minute.
     */
    public VmMonitorLog assertCurrent(final Duration maxAge) {
        log.assertCurrent(maxAge);
        return this;
    }

    /**
     * Fails unless the files written since the time (ms since the epoch) show that the VM monitor checks the new and
     * changed certificate files, and that the last check was not skipped as disabled.
     */
    public VmMonitorLog assertChangesChecked(final long since) {
        final List<Line> lines = parse(log.lines(since));
        int last = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).message.equals(CHANGES_STARTED)) {
                last = i;
            }
        }
        assertTrue(last >= 0, "The VM monitor log has no check of new and changed certificate files: the test needs "
                + "the VM monitor that checks them every minute");
        final String thread = lines.get(last).thread;
        for (final Line line : lines.subList(last + 1, lines.size())) {
            if (!line.thread.equals(thread)) {
                continue;
            }
            assertTrue(!line.message.startsWith(CHANGES_DISABLED), "The VM monitor does not check new and changed "
                    + "certificate files: it is disabled (CP_VM_MONITOR_PKI_CHANGES_ENABLE=false)");
            if (line.message.equals(CHANGES_FINISHED)) {
                break;
            }
        }
        return this;
    }

    /**
     * The certificate expiration notifications that the VM monitor sent to the API, in log order, from the files
     * written since the time (ms since the epoch).
     */
    public List<Report> reports(final long since) {
        return reports(parse(log.lines(since)));
    }

    /**
     * What the files written since the time (ms since the epoch) say about the certificate file (its name, which
     * must be unique) with the subject {@code CN=<commonName>}.
     */
    public CertificateCheck check(final String fileName, final String commonName, final long since) {
        final List<Line> lines = parse(log.lines(since));
        final String subject = "CN=" + commonName;
        final String expires = format(EXPIRES, subject);
        final CertificateCheck check = new CertificateCheck();
        for (final Line line : lines) {
            if (isAbout(line.message, CHECKING, fileName) || isAbout(line.message, READING, fileName)) {
                check.checked = true;
                check.lines.add(line.text);
            } else if (line.message.startsWith(expires)) {
                check.read = true;
                check.lines.add(line.text);
            }
        }
        for (final Report report : reports(lines)) {
            if (report.subject.equals(subject) || report.subject.startsWith(subject + ",")) {
                check.report = report;
                check.lines.addAll(report.lines);
                break;
            }
        }
        return check;
    }

    /**
     * Fails unless the files written since the time (ms since the epoch) show that the node monitor checks the nodes:
     * it does so every minute, and the last check was not skipped as disabled.
     */
    public VmMonitorLog assertNodesMonitored(final long since) {
        String last = null;
        for (final Line line : parse(log.lines(since))) {
            if (line.message.equals(NODES_STARTED) || line.message.equals(NODES_DISABLED)) {
                last = line.message;
            }
        }
        assertTrue(last != null, "The VM monitor log has no check of the nodes: the node monitor does not run");
        assertTrue(!last.equals(NODES_DISABLED), "The VM monitor does not check the nodes: the node monitor is turned "
                + "off (CP_VM_MONITOR_NODE_ENABLE is not true)");
        return this;
    }

    /**
     * The checks of the nodes (cycles) in the files written since the time (ms since the epoch), in log order. A
     * cycle that started before the oldest of these files is not among them.
     */
    public List<NodeCycle> nodeCycles(final long since) {
        final List<NodeCycle> cycles = new ArrayList<>();
        final Map<String, NodePoll> polls = new HashMap<>();
        NodeCycle open = null;
        List<NodePoll> failing = new ArrayList<>();
        for (final String text : log.lines(since)) {
            final Line line = parse(text);
            if (line == null) {
                // The exception of a failed poll is logged on the lines after it, before its stack trace
                if (!failing.isEmpty() && !text.trim().isEmpty() && !isStackFrame(text)) {
                    failing.forEach(poll -> {
                        poll.cause = text.trim();
                        poll.lines.add(text);
                    });
                    failing = new ArrayList<>();
                }
                continue;
            }
            failing = new ArrayList<>();
            if (line.message.equals(NODES_STARTED)) {
                open = new NodeCycle(line.text, line.thread, line.time());
                cycles.add(open);
                polls.clear();
                continue;
            }
            if (open == null) {
                continue;
            }
            final Matcher retrieving = RETRIEVING.matcher(line.message);
            final Matcher failed = RETRIEVE_FAILED.matcher(line.message);
            final Matcher resolved = RESOLVED.matcher(line.message);
            final Matcher detected = DETECTED.matcher(line.message);
            if (retrieving.matches()) {
                final NodePoll poll = new NodePoll(retrieving.group(2), retrieving.group(1));
                poll.lines.add(line.text);
                open.polls.add(poll);
                polls.put(line.thread, poll);
            } else if (failed.matches()) {
                for (final NodePoll poll : open.polls) {
                    if (poll.node.equals(failed.group(2)) && poll.pod.equals(failed.group(1))) {
                        poll.failed = true;
                        poll.lines.add(line.text);
                        failing.add(poll);
                    }
                }
            } else if (resolved.matches() || detected.matches()) {
                // A line of another thread than the poll's can't be told apart from the lines of other nodes
                final NodePoll poll = polls.get(line.thread);
                final Matcher usage = resolved.matches() ? resolved : detected;
                final Usage value = new Usage(usage.group(1), Integer.parseInt(usage.group(2)), usage.group(3));
                if (poll != null) {
                    (resolved.matches() ? poll.usages : poll.exceeded).add(value);
                    poll.lines.add(line.text);
                }
            } else if (line.thread.equals(open.thread)) {
                final Matcher aborting = ABORTING.matcher(line.message);
                if (line.message.equals(PROCEEDING)) {
                    open.proceeded = true;
                    open.lines.add(line.text);
                } else if (aborting.matches()) {
                    open.abortedForMinutes = Integer.valueOf(aborting.group(1));
                    open.abortedUntil = aborting.group(2);
                    open.lines.add(line.text);
                } else if (open.proceeded && open.sent == null
                        && (line.message.equals(API_SENT) || line.message.startsWith(API_FAILED))) {
                    open.sent = line.message.equals(API_SENT);
                    open.lines.add(line.text);
                } else if (NODES_END.matcher(line.message).matches()) {
                    open.finished = true;
                    if (open.proceeded && open.sent == null) {
                        // The cycle ended before the API answered: the notification was not sent
                        open.sent = false;
                    }
                    open.lines.add(line.text);
                    open = null;
                }
            }
        }
        return cycles;
    }

    /**
     * The time of the last line in the files written since the time (ms since the epoch), in the VM monitor's clock
     * and time zone; null if no line has a time.
     */
    public LocalDateTime lastLineTime(final long since) {
        LocalDateTime last = null;
        for (final Line line : parse(log.lines(since))) {
            final LocalDateTime time = line.time();
            if (time != null && (last == null || time.isAfter(last))) {
                last = time;
            }
        }
        return last;
    }

    /**
     * How long the resend delay of the node notifications still lasts, as far as the files written since the time
     * (ms since the epoch) show: the delay after the last {@code Proceeding with notifications...}, unless the VM
     * monitor started again after it, measured up to the last line of the log. Both times are of the VM monitor's
     * clock, so no time zone is involved, and the log is current (see {@link #assertCurrent}) within a minute or two.
     * Zero if no delay lasts.
     */
    public Duration nodeNotificationsResendDelayLeft(final Duration delay, final long since) {
        LocalDateTime proceeded = null;
        LocalDateTime last = null;
        for (final Line line : parse(log.lines(since))) {
            final LocalDateTime time = line.time();
            if (time == null) {
                continue;
            }
            if (line.message.equals(PROCEEDING)) {
                proceeded = time;
            } else if (line.message.startsWith(APPLICATION_STARTING)) {
                proceeded = null;
            }
            if (last == null || time.isAfter(last)) {
                last = time;
            }
        }
        if (proceeded == null) {
            return Duration.ZERO;
        }
        final Duration left = delay.minus(Duration.between(proceeded, last));
        return left.isNegative() ? Duration.ZERO : left;
    }

    private static boolean isAbout(final String message, final String prefix, final String fileName) {
        return message.startsWith(prefix) && message.endsWith("/" + fileName);
    }

    private static List<Report> reports(final List<Line> lines) {
        final List<Report> reports = new ArrayList<>();
        final Map<String, Report> pending = new HashMap<>();
        for (final Line line : lines) {
            final Matcher sending = SENDING.matcher(line.message);
            if (sending.matches()) {
                final Report report = new Report(sending.group(1), line.text);
                reports.add(report);
                pending.put(line.thread, report);
            } else if (line.message.equals(API_SENT) || line.message.startsWith(API_FAILED)
                    || SCAN_END.matcher(line.message).matches()) {
                final Report report = pending.remove(line.thread);
                if (report != null) {
                    report.sent = line.message.equals(API_SENT);
                    report.lines.add(line.text);
                }
            }
        }
        return reports;
    }

    private static List<Line> parse(final List<String> texts) {
        final List<Line> lines = new ArrayList<>();
        for (final String text : texts) {
            final Line line = parse(text);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * The line of the log, null for a line of another form, e.g. of a stack trace.
     */
    private static Line parse(final String text) {
        final Matcher matcher = LINE.matcher(text);
        return matcher.matches() ? new Line(text, matcher.group(1), matcher.group(2), matcher.group(3).trim()) : null;
    }

    private static boolean isStackFrame(final String text) {
        final String trimmed = text.trim();
        return trimmed.startsWith("at ") || trimmed.startsWith("...");
    }

    private static final class Line {
        private final String text;
        private final String time;
        private final String thread;
        private final String message;

        private Line(final String text, final String time, final String thread, final String message) {
            this.text = text;
            this.time = time;
            this.thread = thread;
            this.message = message;
        }

        /**
         * The time of the line in the VM monitor's clock and time zone, null if it does not parse.
         */
        private LocalDateTime time() {
            try {
                return LocalDateTime.parse(time, TIME);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    /**
     * A certificate expiration notification that the VM monitor sent to the API, and the log lines about it.
     * {@code sent} is null until the log shows whether the API accepted it; false if it did not, or if the scan
     * ended before the notification was sent.
     */
    public static final class Report {

        public final String subject;
        public final String line;
        public final List<String> lines = new ArrayList<>();
        public Boolean sent;

        Report(final String subject, final String line) {
            this.subject = subject;
            this.line = line;
            this.lines.add(line);
        }
    }

    /**
     * What the log says about a certificate file: a scan found it ({@code checked}), read the certificate
     * ({@code read}), and reported it ({@code report}, null if not), and the log lines about it.
     */
    public static final class CertificateCheck {

        public final List<String> lines = new ArrayList<>();
        public boolean checked;
        public boolean read;
        public Report report;
    }

    /**
     * A check of the nodes: the nodes it polled, and its notification. {@code lines} are the cycle's own lines (start,
     * notification, end); each poll has its lines. {@code time} is the time of its start, in the VM monitor's clock
     * (null if it does not parse). A cycle that never finishes was cut off, e.g. by a restart of the VM monitor.
     * {@code proceeded} is true if a process was above a threshold and no resend delay lasted; {@code sent} is then
     * whether the API accepted the notification (null until the log shows it). {@code abortedForMinutes} is set if the
     * notification was dropped, because a resend delay lasts that many more minutes (at least 1), until
     * {@code abortedUntil} (UTC).
     */
    public static final class NodeCycle {

        public final String line;
        public final LocalDateTime time;
        public final List<NodePoll> polls = new ArrayList<>();
        public final List<String> lines = new ArrayList<>();
        public boolean finished;
        public boolean proceeded;
        public Boolean sent;
        public Integer abortedForMinutes;
        public String abortedUntil;
        private final String thread;

        NodeCycle(final String line, final String thread, final LocalDateTime time) {
            this.line = line;
            this.thread = thread;
            this.time = time;
            this.lines.add(line);
        }

        /**
         * The poll of the node in this cycle, null if the node was not polled.
         */
        public NodePoll poll(final String node) {
            return polls.stream().filter(poll -> poll.node.equals(node)).findFirst().orElse(null);
        }
    }

    /**
     * The stats of a node, as the node monitor resolved them: the usage of each process the node reporter measures,
     * and those above the threshold. {@code failed} if the stats could not be retrieved, and {@code cause} is then
     * the exception logged after the error, e.g. {@code java.util.concurrent.CompletionException: ... Numeric value
     * (9223372036854775807) out of range of int} (null if the log has none).
     */
    public static final class NodePoll {

        public final String node;
        public final String pod;
        public final List<Usage> usages = new ArrayList<>();
        public final List<Usage> exceeded = new ArrayList<>();
        public final List<String> lines = new ArrayList<>();
        public boolean failed;
        public String cause;

        NodePoll(final String node, final String pod) {
            this.node = node;
            this.pod = pod;
        }
    }

    /**
     * A usage of a process, in percent of its hard limit; for a {@code Detected} line, the threshold it is above.
     */
    public static final class Usage {

        public final String type;
        public final int percent;
        public final String process;

        Usage(final String type, final int percent, final String process) {
            this.type = type;
            this.percent = percent;
            this.process = process;
        }
    }
}

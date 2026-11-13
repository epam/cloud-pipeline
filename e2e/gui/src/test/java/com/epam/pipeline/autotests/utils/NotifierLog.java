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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.testng.Assert.assertTrue;

/**
 * The log of the notifier (cp-notifier), read from its log directory: notifier.log, and the files it is rolled over
 * to every day, notifier-yyyyMMdd.log.
 * <p>
 * For each attempt to send a message, the notifier logs the email it built, with the subject and all the recipients:
 * {@code Email from message <id> with subject '<subject>' formed and will be send to: [<to>], cc: [<cc>],
 * bcc: [<bcc>]}. Then {@code Message with id: <id> was successfully send} once the mail server has accepted it, or
 * {@code All attempts are failed. Message with id: <id> will not be sent.} after the last failed attempt. An older
 * notifier logs only the To addresses: {@code Email from message <id> formed and will be send to: [<to>]}.
 */
public class NotifierLog {

    private static final Pattern FORMED = Pattern.compile("Email from message (\\d+) with subject '(.*)' formed and "
            + "will be send to: \\[([^\\]]*)\\], cc: \\[([^\\]]*)\\], bcc: \\[([^\\]]*)\\]");
    private static final Pattern FORMED_WITHOUT_SUBJECT =
            Pattern.compile("Email from message \\d+ formed and will be send to: ");
    private static final Pattern SENT = Pattern.compile("Message with id: (\\d+) was successfully send");
    private static final Pattern DROPPED = Pattern.compile("All attempts are failed\\. Message with id: (\\d+) will not");

    private final RollingLog log;

    public NotifierLog(final String dir) {
        assertTrue(dir != null && !dir.trim().isEmpty(),
                "No notifier log directory: set e2e.ui.notifier.logs.dir (run-e2e.sh mounts it at /notifier-logs)");
        this.log = new RollingLog(Paths.get(dir), "notifier", "notifier");
    }

    /**
     * Fails unless notifier.log is readable and was written within the last {@code maxAge}: the notifier logs its
     * queue check every 30 s. An empty or old directory is not the log of the running notifier.
     */
    public NotifierLog assertCurrent(final Duration maxAge) {
        log.assertCurrent(maxAge);
        return this;
    }

    /**
     * Fails if the last email logged in the files written since the time (ms since the epoch) has no subject: that
     * notifier is older, and no email can be found by its subject. A log with no email since then passes.
     */
    public NotifierLog assertSubjectsLogged(final long since) {
        String last = null;
        for (final String line : log.lines(since)) {
            if (FORMED.matcher(line).find() || FORMED_WITHOUT_SUBJECT.matcher(line).find()) {
                last = line;
            }
        }
        assertTrue(last == null || FORMED.matcher(last).find(), format("The notifier logs its emails without the "
                + "subject, e.g. '%s': the test needs the notifier that logs the subject and all recipients", last));
        return this;
    }

    /**
     * Returns the emails whose subject ends with the text, by message id in log order, from the log files written
     * since the time (ms since the epoch).
     */
    public Map<Long, Email> emailsWithSubjectEndingWith(final String subjectEnd, final long since) {
        final List<String> lines = log.lines(since);
        final Map<Long, Email> emails = new LinkedHashMap<>();
        for (final String line : lines) {
            final Matcher formed = FORMED.matcher(line);
            if (formed.find() && formed.group(2).trim().endsWith(subjectEnd)) {
                final long id = Long.parseLong(formed.group(1));
                // Each attempt logs the email again: it is still one message
                emails.computeIfAbsent(id, key -> new Email(key, formed.group(2), recipients(formed.group(3)),
                        recipients(formed.group(4)), recipients(formed.group(5)))).lines.add(line);
            }
        }
        for (final String line : lines) {
            final Long sent = id(SENT, line);
            final Long dropped = id(DROPPED, line);
            if (sent != null && emails.containsKey(sent)) {
                emails.get(sent).sent = true;
                emails.get(sent).lines.add(line);
            }
            if (dropped != null && emails.containsKey(dropped)) {
                emails.get(dropped).dropped = true;
                emails.get(dropped).lines.add(line);
            }
        }
        return emails;
    }

    private static Long id(final Pattern pattern, final String line) {
        final Matcher matcher = pattern.matcher(line);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private static List<String> recipients(final String list) {
        return list.trim().isEmpty()
                ? Collections.emptyList()
                : Arrays.stream(list.split(",")).map(String::trim).collect(Collectors.toList());
    }

    /**
     * An email of the notifier log, and the log lines about it.
     */
    public static final class Email {

        public final long id;
        public final String subject;
        public final List<String> to;
        public final List<String> cc;
        public final List<String> bcc;
        public final List<String> lines = new ArrayList<>();
        public boolean sent;
        public boolean dropped;

        Email(final long id, final String subject, final List<String> to, final List<String> cc,
              final List<String> bcc) {
            this.id = id;
            this.subject = subject;
            this.to = to;
            this.cc = cc;
            this.bcc = bcc;
        }

        public boolean isSentTo(final String address) {
            return to.stream().anyMatch(address::equalsIgnoreCase);
        }
    }
}

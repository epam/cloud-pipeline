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

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.testng.Assert.assertTrue;

/**
 * The addresses the VM monitor's emails go to: the email of the user of CP_VM_MONITOR_TO_USER (To), and copies to the
 * users of CP_VM_MONITOR_CC_USERS who have an email, which the notifier sends as BCC. The users are read through the
 * API from the GUI tab, so a page of the platform must be open.
 */
public final class VmMonitorRecipients {

    public final String toEmail;
    public final List<String> ccEmails;

    private VmMonitorRecipients(final String toEmail, final List<String> ccEmails) {
        this.toEmail = toEmail;
        this.ccEmails = Collections.unmodifiableList(ccEmails);
    }

    /**
     * Loads the addresses of e2e.ui.vm.monitor.to.user and e2e.ui.vm.monitor.cc.users. Fails if a user does not
     * exist, as the API then rejects every notification of the VM monitor, or if the To user has no email.
     *
     * @param testCase the case, for the console log, e.g. "2870_1"
     */
    public static VmMonitorRecipients load(final String testCase) {
        return new VmMonitorRecipients(userEmail(C.VM_MONITOR_TO_USER, "CP_VM_MONITOR_TO_USER"), ccEmails(testCase));
    }

    /**
     * Fails unless the email goes to the To address, with a copy to each copy user who has an email.
     */
    public void assertSentTo(final NotifierLog.Email email) {
        assertTrue(email.isSentTo(toEmail), format("The email '%s' (message %d) is not sent to %s (%s): to %s",
                email.subject, email.id, C.VM_MONITOR_TO_USER, toEmail, email.to));
        final List<String> missing = ccEmails.stream()
                .filter(cc -> email.bcc.stream().noneMatch(cc::equalsIgnoreCase))
                .collect(Collectors.toList());
        assertTrue(missing.isEmpty(), format("The email '%s' (message %d) has no copy to %s: bcc %s",
                email.subject, email.id, missing, email.bcc));
    }

    private static String userEmail(final String userName, final String variable) {
        assertTrue(!userName.trim().isEmpty(), format("The user the VM monitor emails is not known: set "
                + "e2e.ui.vm.monitor.to.user (%s)", variable));
        return PlatformApi.email(user(userName.trim(), variable)).orElseThrow(() -> new AssertionError(
                format("User %s (%s) has no email attribute", userName, variable)));
    }

    /**
     * The emails of the copy users that have one: the notifier sends copies only to them.
     */
    private static List<String> ccEmails(final String testCase) {
        final List<String> emails = new ArrayList<>();
        for (final String name : C.VM_MONITOR_CC_USERS.split(",")) {
            if (name.trim().isEmpty()) {
                continue;
            }
            final Optional<String> email = PlatformApi.email(user(name.trim(), "CP_VM_MONITOR_CC_USERS"));
            if (email.isPresent()) {
                emails.add(email.get());
            } else {
                System.out.printf("%s: copy user %s (CP_VM_MONITOR_CC_USERS) has no email attribute, so gets no "
                        + "copy%n", testCase, name.trim());
            }
        }
        return emails;
    }

    /**
     * The user, which must exist: the API rejects a notification of the VM monitor if one of its users does not.
     */
    private static JsonNode user(final String name, final String variable) {
        try {
            final JsonNode user = PlatformApi.get("user?name=" + URLEncoder.encode(name, "UTF-8"));
            assertTrue(name.equalsIgnoreCase(user.path("userName").asText()), format("User %s (%s) does not exist: "
                    + "the API rejects the notifications of the VM monitor", name, variable));
            return user;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

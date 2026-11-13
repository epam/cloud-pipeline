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
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PlatformApi;
import com.epam.pipeline.autotests.utils.TestCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * The time limits of the apt-get, yum and apk calls at the start of a run: cases 4781_1, 4781_2 and 4781_3.
 * <p>
 * 4781_1 and 4781_2 make the first "apt-get update" or yum call of a run hang, with the launch.pre.common.commands
 * entries of the cases, which do nothing in a run without the TEST_APT_HANG_ONCE / TEST_YUM_HANG_ONCE parameter.
 * The preference is read, changed and restored through the API, from the GUI tab with its session, instead of being
 * typed into the preference editor as the cases say: the editor closes quotes and brackets by itself, and the
 * entries, like the deployment's own value that the restore puts back, have escaped quotes and backslashes.
 */
public class PackageManagerTimeoutsTest extends AbstractSeveralPipelineRunningTest implements Authorization, Tools {

    private static final String PRE_COMMON_COMMANDS = "launch.pre.common.commands";
    private static final String HANG_ONCE_ENTRIES = "/packageManagerTimeoutsPreCommonCommands.json";
    private static final String CONSOLE = "Console";
    private static final String TIME_LIMIT = "300";
    private static final String APT_UPDATE = "apt-get update -qq -y --allow-insecure-repositories";
    private static final String APT_CHECK = "ls /tmp/test-apt-hung && head -1 /etc/apt/sources.list";
    private static final String YUM_CHECK =
            "ls /etc/yum.repos.d/cloud-pipeline.repo && grep -c 'did not finish' /var/log/yum.cp.log";
    private static final String[] TIME_LIMIT_WARNINGS =
            {"did not finish in", "is not a number of seconds", "No usable timeout command"};
    // launch.sh prints them before and after the package manager calls: the Console log is read until it has both
    private static final String BEFORE_PACKAGE_MANAGER = "Install runtime dependencies";
    private static final String AFTER_PACKAGE_MANAGER = "Install additional packages";
    private static final int LOG_READS = 30;
    // A command (not a path such as /etc/apt/...) that runs the package manager
    private static final String APT_COMMAND = "(^|[;&|(]\\s*|\\s)(apt-get|apt)(\\s|$)";
    private static final String YUM_COMMAND = "(^|[;&|(]\\s*|\\s)yum(\\s|$)";
    private static final String APT_ENTRY = "test_apt_hang_once";
    private static final String YUM_ENTRY = "test_yum_hang_once";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String anotherGroup = C.ANOTHER_GROUP;
    private final String rockyTool = C.TESTING_TOOL_NAME;
    private final String ubuntuTool = format("%s/ubuntu", anotherGroup);
    private final String centosTool = format("%s/%s", anotherGroup, C.TEST_DOCKER_IMAGE);
    private ObjectNode originalPreference;
    private ObjectNode originalValue;
    private String restoreValue;
    private boolean restoreByDelete;
    private boolean preferenceChanged;

    @BeforeClass
    public void addHangOnceEntries() throws IOException {
        logoutIfNeeded();
        loginAs(admin);
        originalPreference = loadPreference();
        originalValue = preferenceValue(originalPreference);
        // Entries left by a run that was stopped before its restore are not part of the deployment's value
        final boolean leftovers = removeEntry(originalValue, "ubuntu", APT_ENTRY)
                | removeEntry(originalValue, "rocky", YUM_ENTRY);
        if (leftovers) {
            System.out.printf("%s has hang-once entries of an earlier run: they are not restored%n", PRE_COMMON_COMMANDS);
            restoreByDelete = originalValue.size() == 0;
            restoreValue = restoreByDelete ? null : JSON.writeValueAsString(originalValue);
        } else {
            // A value the deployment never saved is the system default: the restore deletes the saved one
            restoreByDelete = !originalPreference.hasNonNull("createdDate");
            final JsonNode value = originalPreference.path("value");
            // The API leaves out a null value: absent and null are both restored as null
            restoreValue = value.isMissingNode() || value.isNull() ? null : value.asText();
        }
        final ObjectNode value = originalValue.deepCopy();
        merge(value, JSON.readTree(readResourceFully(HANG_ONCE_ENTRIES)));
        final String newValue = JSON.writeValueAsString(value);
        preferenceChanged = true;
        savePreference(newValue, originalPreference.path("visible").asBoolean(false));
        assertEquals(loadPreference().path("value").asText(), newValue,
                format("%s was not saved with the hang-once entries", PRE_COMMON_COMMANDS));
    }

    @AfterClass(alwaysRun = true)
    public void restorePreCommonCommands() {
        restorePreference();
    }

    @Test(priority = 1)
    @TestCase("4781_1")
    public void stalledRefreshOfThePackageListsIsStoppedAndTriedOnceMore() {
        assertNoOtherCommands("ubuntu", APT_COMMAND);
        String runId = null;
        try {
            final LogAO run = launchWithParameters(anotherGroup, ubuntuTool, "ubuntu",
                    "TEST_APT_HANG_ONCE", "CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC");
            runId = getLastRunId();
            final List<String> log = consoleLog(run.waitForSshLink());
            assertHangOnceCommandRan(log, "ubuntu:22.04", "TEST_APT_HANG_ONCE");
            // A refresh that hung again would make the repo setup fail, with "Failed to configure": the log rows
            // are read as a set, so the "once" of the case is checked by that row being absent
            assertLogContains(log, format("[WARN] '%s' did not finish in %s seconds and was stopped",
                    APT_UPDATE, TIME_LIMIT));
            assertLogContains(log, format("[WARN] Refreshing the package lists once more: %s", APT_UPDATE));
            assertLogDoesNotContain(log, "Failed to configure");
            final String sshRunId = runId;
            run.sshInNewTab(shell -> shell
                    .waitUntilTextAppears(sshRunId)
                    .executeAndWaitForPrompt(APT_CHECK, format("pipeline-%s", sshRunId))
                    .assertOutputOfLastCommandMatches(APT_CHECK,
                            "/tmp/test-apt-hung\\s*deb \\S+/ubuntu/22\\.04 stable main"));
        } finally {
            stopRun(runId);
        }
    }

    @Test(priority = 2)
    @TestCase("4781_2")
    public void stalledYumCallOfTheRepoSetupIsReportedInTheRunLogAndTriedAgain() {
        assertNoOtherCommands("rocky", YUM_COMMAND);
        String runId = null;
        try {
            final LogAO run = launchWithParameters(group, rockyTool, nameWithoutGroup(rockyTool),
                    "TEST_YUM_HANG_ONCE", "CP_PKG_MANAGER_INSTALL_TIMEOUT_SEC");
            runId = getLastRunId();
            final List<String> log = consoleLog(run.waitForSshLink());
            assertHangOnceCommandRan(log, "rocky:8.7", "TEST_YUM_HANG_ONCE");
            assertLogContains(log,
                    "[WARN] (attempt: 1) yum did not finish in time and was stopped, see /var/log/yum.cp.log");
            assertTrue(log.stream().anyMatch(row -> row.contains("[ERROR] (attempt: 1) Failed to configure ")
                            && row.contains("/rocky/8.7 for the yum, removing the repo")),
                    "No '[ERROR] (attempt: 1) Failed to configure <repo url>/rocky/8.7 ...' row in the log");
            assertLogDoesNotContain(log, "(attempt: 2)");
            assertLogDoesNotContain(log, "(attempt: 3)");
            final String sshRunId = runId;
            run.sshInNewTab(shell -> shell
                    .waitUntilTextAppears(sshRunId)
                    .executeAndWaitForPrompt(YUM_CHECK, format("pipeline-%s", sshRunId))
                    .assertOutputOfLastCommandMatches(YUM_CHECK,
                            "/etc/yum\\.repos\\.d/cloud-pipeline\\.repo\\s*1(?!\\d)"));
        } finally {
            stopRun(runId);
        }
    }

    @Test(priority = 3)
    @TestCase("4781_3")
    public void packageManagerTimeLimitsDoNotAffectAUsualRunStart() {
        // The case's prerequisite: the preference has no hang-once entries
        restorePreference();
        checkUsualRunStart(ubuntuTool, "ubuntu");
        checkUsualRunStart(centosTool, C.TEST_DOCKER_IMAGE);
    }

    private void checkUsualRunStart(final String tool, final String toolSelfName) {
        logoutIfNeeded();
        loginAs(admin);
        String runId = null;
        try {
            final LogAO run = tools()
                    .perform(registry, anotherGroup, tool, ToolTab::runWithCustomSettings)
                    .setDefaultLaunchOptions()
                    .doNotMountStoragesSelect(true)
                    .launchTool(this, toolSelfName)
                    .showLog(getLastRunId());
            runId = getLastRunId();
            final List<String> log = consoleLog(run.waitForSshLink());
            for (final String warning : TIME_LIMIT_WARNINGS) {
                assertLogDoesNotContain(log, warning);
            }
        } finally {
            stopRun(runId);
        }
    }

    private LogAO launchWithParameters(final String toolGroup, final String tool, final String toolSelfName,
                                       final String hangParameter, final String limitParameter) {
        logoutIfNeeded();
        loginAs(admin);
        return tools()
                .perform(registry, toolGroup, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .doNotMountStoragesSelect(true)
                .clickAddStringParameter().setName(hangParameter).setValue("true").close()
                .clickAddStringParameter().setName(limitParameter).setValue(TIME_LIMIT).close()
                .clickAddStringParameter().setName("CP_REPO_ENABLED").setValue("true").close()
                .launchTool(this, toolSelfName)
                .showLog(getLastRunId());
    }

    /**
     * The rows of the run's Console log, read until they hold the rows launch.sh prints before and after the
     * package manager calls: a log read before it is loaded would let the "doesn't contain" checks pass
     */
    private List<String> consoleLog(final LogAO run) {
        for (int read = 1; ; read++) {
            final List<String> log = run
                    .waitForTask(CONSOLE)
                    .click(taskWithName(CONSOLE))
                    .logMessages()
                    .collect(Collectors.toList());
            if (hasRow(log, BEFORE_PACKAGE_MANAGER) && hasRow(log, AFTER_PACKAGE_MANAGER)) {
                return log;
            }
            if (read == LOG_READS) {
                fail(format("The Console log has no '%s' and '%s' rows after %d reads",
                        BEFORE_PACKAGE_MANAGER, AFTER_PACKAGE_MANAGER, LOG_READS));
            }
            run.sleep(10, SECONDS).refresh();
        }
    }

    private static boolean hasRow(final List<String> log, final String message) {
        return log.stream().anyMatch(row -> row.contains(message));
    }

    private void stopRun(final String runId) {
        if (runId != null) {
            open(C.ROOT_ADDRESS);
            runsMenu().stopRunIfPresent(runId);
        }
    }

    /**
     * The hang-once entry was run for the OS and did not fail: launch.sh read the additional commands for the OS and
     * logged no "[WARN] '<command>' command done with exit code" row for the entry. Only the entry is checked: other
     * commands of the deployment's preference may fail on the tool, which is not what the case is about. E.g. an
     * entry "ln -s /etc/ssl/certs/ca-bundle.crt /etc/cp-certs.pem" for Rocky Linux fails on a test tool whose image
     * already has /etc/cp-certs.pem ("N-1 out of N").
     */
    private static void assertHangOnceCommandRan(final List<String> log, final String os, final String hangParameter) {
        final Pattern found = Pattern.compile("\\d+ additional commands were found for " + Pattern.quote(os));
        assertTrue(log.stream().anyMatch(message -> found.matcher(message).find()),
                format("No '<N> additional commands were found for %s' row", os));
        assertFalse(log.stream().anyMatch(message -> message.contains("[WARN] '") && message.contains(hangParameter)
                        && message.contains("command done with exit code")),
                format("The hang-once command of %s failed: it has a 'command done with exit code' row", hangParameter));
    }

    private static void assertLogContains(final List<String> log, final String message) {
        assertTrue(hasRow(log, message), format("No '%s' row in the log", message));
    }

    private static void assertLogDoesNotContain(final List<String> log, final String message) {
        assertFalse(hasRow(log, message), format("The log has a '%s' row", message));
    }

    /**
     * The case's prerequisite: no other launch.pre.common.commands command for the OS runs the package manager, as
     * it would run into the hang before the Cloud Pipeline repo setup. The value has no hang-once entries here.
     */
    private void assertNoOtherCommands(final String os, final String packageManagerCommand) {
        final Pattern pattern = Pattern.compile(packageManagerCommand);
        final List<String> others = new ArrayList<>();
        originalValue.path("linux").path(os).forEach(version -> version.forEach(entry -> entry.forEach(command -> {
            if (pattern.matcher(command.asText()).find()) {
                others.add(command.asText());
            }
        })));
        assertTrue(others.isEmpty(), format("%s has other %s commands that run the package manager: %s",
                PRE_COMMON_COMMANDS, os, others));
    }

    private void restorePreference() {
        if (!preferenceChanged) {
            return;
        }
        logoutIfNeeded();
        loginAs(admin);
        open(C.ROOT_ADDRESS);
        if (restoreByDelete) {
            PlatformApi.call("DELETE", "preferences/" + PRE_COMMON_COMMANDS, null);
            assertTrue(loadPreference().path("value").isMissingNode() || loadPreference().path("value").isNull(),
                    format("%s was not restored to its default", PRE_COMMON_COMMANDS));
        } else {
            savePreference(restoreValue, originalPreference.path("visible").asBoolean(false));
            final JsonNode value = loadPreference().path("value");
            assertEquals(value.isMissingNode() || value.isNull() ? null : value.asText(), restoreValue,
                    format("%s was not restored", PRE_COMMON_COMMANDS));
        }
        preferenceChanged = false;
    }

    /**
     * Removes the entry from every version object of the OS, then the objects left empty.
     * Returns whether the value had the entry.
     */
    private static boolean removeEntry(final ObjectNode value, final String os, final String entry) {
        final JsonNode linux = value.get("linux");
        final JsonNode versions = linux == null ? null : linux.get(os);
        if (versions == null || !versions.isObject()) {
            return false;
        }
        boolean removed = false;
        final Iterator<Map.Entry<String, JsonNode>> it = versions.fields();
        while (it.hasNext()) {
            final JsonNode version = it.next().getValue();
            if (version.isObject() && ((ObjectNode) version).remove(entry) != null) {
                removed = true;
                if (version.size() == 0) {
                    it.remove();
                }
            }
        }
        if (versions.size() == 0) {
            ((ObjectNode) linux).remove(os);
        }
        if (linux.size() == 0) {
            value.remove("linux");
        }
        return removed;
    }

    private ObjectNode loadPreference() {
        return (ObjectNode) PlatformApi.call("GET", "preferences/" + PRE_COMMON_COMMANDS, null).get("payload");
    }

    /**
     * Saves the value of the preference. For a system preference the API takes only the value and the visibility
     * from the request (the rest comes from the preference's definition), so only these are sent
     */
    private void savePreference(final String value, final boolean visible) {
        final ObjectNode preference = JSON.createObjectNode()
                .put("name", PRE_COMMON_COMMANDS)
                .put("value", value)
                .put("visible", visible);
        PlatformApi.call("POST", "preferences", JSON.createArrayNode().add(preference).toString());
    }

    private static ObjectNode preferenceValue(final ObjectNode preference) throws IOException {
        final String value = preference.path("value").asText("");
        if (value.trim().isEmpty()) {
            return JSON.createObjectNode();
        }
        final JsonNode parsed = JSON.readTree(value);
        assertTrue(parsed.isObject(), format("%s is not a JSON object: %s", PRE_COMMON_COMMANDS, value));
        return (ObjectNode) parsed;
    }

    private static void merge(final ObjectNode target, final JsonNode source) {
        source.fields().forEachRemaining(field -> {
            final JsonNode existing = target.get(field.getKey());
            if (existing != null && existing.isObject() && field.getValue().isObject()) {
                merge((ObjectNode) existing, field.getValue());
            } else {
                target.set(field.getKey(), field.getValue().deepCopy());
            }
        });
    }
}

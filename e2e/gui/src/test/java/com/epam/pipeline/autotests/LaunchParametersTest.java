/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO.PreferencesAO;
import com.epam.pipeline.autotests.ao.ShellAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Json;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.PlatformApi;
import com.epam.pipeline.autotests.utils.TestCase;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;


import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.exist;
import static com.epam.pipeline.autotests.ao.LogAO.Status.STOPPED;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.START_IDLE;
import static com.epam.pipeline.autotests.ao.Primitive.TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.ToolVersions.hasOnPage;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.lang.Double.parseDouble;
import static java.util.regex.Pattern.compile;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import com.epam.pipeline.autotests.utils.Utils;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static com.codeborne.selenide.Condition.exist;

public class LaunchParametersTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private static final String UMOUNT_COMMAND = "echo \"#!/usr/bin/env bash\" > /usr/local/sbin/umount && " +
            "echo \"sleep infinity\" >> /usr/local/sbin/umount && chmod +x /usr/local/sbin/umount";
    private static final String TERMINATE_RUN_TIMEOUT = "CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN";
    private static final int DEFAULT_TERMINATE_RUN_TIMEOUT = 1;
    private static final int TEST_TERMINATE_RUN_TIMEOUT = 2;
    private static final String UNMOUNTING_STARTED = "Unmounting all storage mounts";
    // launch.sh marks a run whose work has finished with the WORK_FINISHED tag and this line of CleanupEnvironment;
    // the API stops it when it stays so for CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN (ResourceMonitoringManager)
    private static final String WORK_FINISHED_MESSAGE = "Marking run as finished.";
    private static final String WORK_FINISHED_TAG = "WORK_FINISHED";
    private static final String EMERGENCY_TERM_ACTION = "launch.run.emergency.termination.action";
    private static final String CONSOLE = "Console";
    private static final String CLEANUP_ENVIRONMENT_TASK = "CleanupEnvironment";
    private static final String FILESYSTEM_AUTOSCALING = "FilesystemAutoscaling";
    private static final String CLUSTER_INSTANCE_HDD_SCALE_ENABLED = "cluster.instance.hdd.scale.enabled";
    private static final String CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO = "cluster.instance.hdd.scale.delta.ratio";
    private static final double SCALING_COEFF = 1 + Double.parseDouble(C.CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO);
    private static final String CHECK_SPACE_COMMAND = "df -hT";
    private static final String PARAMETERS_FILE = "/sge_profile_configuration.txt";
    private static final String TEST_CONDA_ENVIRONMENT = "test-env";
    private static final String LIST_CONDA_ENVIRONMENTS = "conda env list";
    private static final String CREATE_CONDA_ENVIRONMENT = format("conda create -y -n %s", TEST_CONDA_ENVIRONMENT);
    private static final String REMOVE_CONDA_ENVIRONMENT = format("conda env remove -y -n %s", TEST_CONDA_ENVIRONMENT);
    // a row of "conda env list" is the environment name, "*" for the active one, and its path
    private static final String BASE_CONDA_ENVIRONMENT_ROW = "base\\s+\\*?\\s*/";
    private static final String TEST_CONDA_ENVIRONMENT_ROW = TEST_CONDA_ENVIRONMENT + "\\s+/";
    private static final long CONDA_COMMAND_TIMEOUT = MINUTES.toMillis(5);
    private static final long FALLOCATE_TIMEOUT = MINUTES.toMillis(5);
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String rootHost = "root@pipeline";
    private final String customTag = "test_tag_913_" + Utils.randomSuffix();
    private final String testProfile = "testprofile.q";
    private static String testInstance = "r6i.%s";
    private static final String INSTANCE_TYPES_MASK = "Allowed instance types mask";
    private static final String TOOL_INSTANCE_TYPES_MASK = "Allowed tool instance types mask";
    private static final int USER_MASKS_ATTEMPTS = 3;
    private int[] scaling = new int[4];
    private int[] sizeDisk = new int[4];
    private String[] command = {
            "sge list",
            format("sge create %s", testProfile),
            format("sge configure %s", testProfile)
    };
    private final List<String> invalidProfileParameters = singletonList("export CP_CAP_AUTOSCALE=\"nontrue\"");
    private final List<String> profileParameters = Arrays.stream(readResourceFully(PARAMETERS_FILE).split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .collect(toList());

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        open(C.ROOT_ADDRESS);
        logoutIfNeeded();
    }
    
    @Test(priority = 1)
    @TestCase(value = {"2736"})
    public void forcibleTerminateInstancesIfJobIsStuckInUmount() {
        // The API stops a run stuck in its cleanup only with this preference set to STOP (DISABLED by default); it
        // checks the runs every system.resource.monitoring.period (60 s by default). The test sets it, and sets the
        // previous value back after
        final JsonNode termAction = PlatformApi.get("preferences/" + EMERGENCY_TERM_ACTION);
        final boolean termActionChanged = !"STOP".equals(termAction.path("value").asText(""));
        if (termActionChanged) {
            saveTermAction("STOP", termAction.path("visible").asBoolean(false));
        }
        logoutIfNeeded();
        loginAs(user);
        String stuckRunId = null;
        try {
            final RunsMenuAO runs = tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .setCommand(UMOUNT_COMMAND)
                    .launchTool(this, nameWithoutGroup(tool));
            stuckRunId = getLastRunId();
            runs
                    .showLog(stuckRunId)
                    .waitForTask(CONSOLE)
                    .click(taskWithName(CONSOLE))
                    .waitForLog(UNMOUNTING_STARTED)
                    .ensure(log(), containsMessages(UNMOUNTING_STARTED))
                    .sleep(DEFAULT_TERMINATE_RUN_TIMEOUT, MINUTES)
                    .shouldHaveRunningStatus();

            tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .setCommand(UMOUNT_COMMAND)
                    .clickAddStringParameter()
                    .setName(TERMINATE_RUN_TIMEOUT)
                    .setValue(valueOf(TEST_TERMINATE_RUN_TIMEOUT))
                    .close()
                    .launchTool(this, nameWithoutGroup(tool))
                    .showLog(getLastRunId())
                    .waitForTask(CONSOLE)
                    .click(taskWithName(CONSOLE))
                    .waitForLog(UNMOUNTING_STARTED)
                    .ensure(log(), containsMessages(UNMOUNTING_STARTED))
                    .waitForTask(CLEANUP_ENVIRONMENT_TASK)
                    .click(taskWithName(CLEANUP_ENVIRONMENT_TASK))
                    .ensure(log(), containsMessages(WORK_FINISHED_MESSAGE));
            assertTrue(PlatformApi.get("run/" + getLastRunId()).path("tags").has(WORK_FINISHED_TAG),
                    format("Run %s has no %s tag", getLastRunId(), WORK_FINISHED_TAG));
            // The timeout, then up to one monitoring period of the API
            new LogAO()
                    .sleep(TEST_TERMINATE_RUN_TIMEOUT + 1, MINUTES)
                    .refresh()
                    .shouldHaveStatus(STOPPED);
        } finally {
            stopRunAsOwner(stuckRunId);
            if (termActionChanged) {
                restoreTermAction(termAction);
            }
        }
    }

    private static void saveTermAction(final String value, final boolean visible) {
        final ObjectNode preference = new ObjectMapper().createObjectNode()
                .put("name", EMERGENCY_TERM_ACTION)
                .put("value", value)
                .put("visible", visible);
        PlatformApi.call("POST", "preferences", "[" + preference + "]");
    }

    /**
     * Sets the previous value of the preference back, as the admin: a value the deployment never saved is the system
     * default, and the saved one is deleted then.
     */
    private void restoreTermAction(final JsonNode original) {
        logoutIfNeeded();
        loginAs(admin);
        open(C.ROOT_ADDRESS);
        if (original.hasNonNull("createdDate")) {
            saveTermAction(original.path("value").asText(""), original.path("visible").asBoolean(false));
        } else {
            PlatformApi.call("DELETE", "preferences/" + EMERGENCY_TERM_ACTION, null);
        }
    }

    @Test(priority = 3)
    @TestCase(value = {"3433"})
    public void allowToUseR6iInstanceFamilyInSGEhybridAutoscaling() {
        String hybridClusterRunId = null;
        try {
            logoutIfNeeded();
            loginAs(admin);
            setUserSettings(format(testInstance, "*"));
            logoutIfNeeded();
            loginAs(user);
            final RunsMenuAO runs = tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .setTypeValue(format(testInstance, "xlarge"))
                    .enableClusterLaunch()
                    .clusterSettingsForm("Auto-scaled cluster")
                    .enableHybridClusterSelect()
                    .ok()
                    .expandTab(ADVANCED_PANEL)
                    .setPriceType(ON_DEMAND)
                    .doNotMountStoragesSelect(true)
                    .launchTool(this, nameWithoutGroup(tool));
            hybridClusterRunId = getLastRunId();
            runs
                    .showLog(hybridClusterRunId)
                    .waitForSshLink()
                    .ssh(shell -> shell
                            .waitUntilTextAppears(getLastRunId())
                            .execute("qsub -b y -pe local 32 sleep infinity")
                            .assertNextStringIsVisible("Your job 1 (\"sleep\") has been submitted",
                                    format("pipeline-%s", getLastRunId()))
                            .close());
            runsMenu()
                    .showLog(getLastRunId())
                    .waitForNestedRunsLink()
                    .clickOnNestedRunLink()
                    .instanceParameters(instance ->
                            instance.ensure(TYPE, text(format(testInstance, "8xlarge")))
                    );
        } finally {
            stopRunAsOwner(hybridClusterRunId);
            open(C.ROOT_ADDRESS);
            logoutIfNeeded();
            loginAs(admin);
            setUserSettings("");
        }
    }

    @Test(priority = 2)
    @TestCase(value = "913")
    public void addSupportForAutoscalingFilesystemForAWS() {
        try { 
            logoutIfNeeded();
            loginAs(admin);      
            checkHDDpreferences();
            tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDisk("25")
                .setPriceType(ON_DEMAND)
                .doNotMountStoragesSelect(true)
                .click(START_IDLE)
                .launchTool(this, nameWithoutGroup(tool))
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                           .sshInNewTab(shell -> createBigFile(shell))
                           .waitForTask(FILESYSTEM_AUTOSCALING)
                           .click(taskWithName(FILESYSTEM_AUTOSCALING))
                           .ensure(log(), containsMessages(autoscalingMessage(scaling[0])))
                           .sshInNewTab(shell -> checkFilesystemSpace(shell, sizeDisk[0]))
                           .commit(commit -> commit.setVersion(customTag).ok())
                           .assertCommittingFinishedSuccessfully()
                           .click(taskWithName(FILESYSTEM_AUTOSCALING))
                           .ensure(log(), containsMessages(autoscalingMessage(sizeDisk[0])))
                           .ensure(log(), containsMessages(autoscalingMessage(sizeDisk[1])))
                           .sshInNewTab(shell -> checkFilesystemSpace(shell, sizeDisk[2]))
                           .pause(nameWithoutGroup(tool))
                           .assertPausingFinishedSuccessfully()
                           .resume(nameWithoutGroup(tool))
                           .assertResumingFinishedSuccessfully()
                           .waitForSshLink()
                           .click(taskWithName(FILESYSTEM_AUTOSCALING))
                           .ensure(log(), containsMessages(autoscalingMessage(sizeDisk[2])))
                           .sshInNewTab(shell -> checkFilesystemSpace(shell, sizeDisk[3]))
                );
         } finally {
            open(C.ROOT_ADDRESS);
            logoutIfNeeded();
            loginAs(admin);
            deleteToolVersionIfPresent(customTag);
        }
    }

    @Test
    @TestCase(value = "TC-PARAMETERS-6")
    public void autoscalingClusterConfiguration() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .expandTab(ADVANCED_PANEL)
                .setPriceType(ON_DEMAND)
                .doNotMountStoragesSelect(true)
                .enableClusterLaunch()
                .clusterSettingsForm("Auto-scaled cluster")
                .setWorkingNodesCount("1")
                .ok()
                .launchTool(this, nameWithoutGroup(tool));
        final String parentRunId = getLastRunId();
        final String prompt = format("pipeline-%s", parentRunId);
        final String submitCommand = format("qsub -b y -q %s -pe local 4 -t 1:2 sleep 5m", testProfile);
        runsMenu()
                .showLog(getLastRunId())
                .waitForSshLink()
                .sshInNewTab(shell -> {
                    shell
                            .waitUntilTextAppears(parentRunId)
                            .execute(command[0])
                            .assertNextStringIsVisible("Grid engine profile has been found: main.q", prompt)
                            .assertPageAfterCommandContainsStrings(command[0],
                                    "Initiating grid engine profiles listing...")
                            .execute(command[1])
                            .assertPageContains(format("Grid engine %s profile.", testProfile))
                            .execute(":q!")
                            .waitUntilTextAppears(parentRunId)
                            .assertPageAfterCommandContainsStrings(command[1],
                                    format("Grid engine %s profile has been created.", testProfile))
                            .execute(command[0])
                            .assertNextStringIsVisible(format("Grid engine profile has been found: %s", testProfile),
                                    prompt)
                            .execute(command[2])
                            .assertPageContains(format("Grid engine %s profile.", testProfile));
                    setProfileParametersInEditor(shell, invalidProfileParameters)
                            .waitUntilTextAppears(parentRunId)
                            .assertPageContainsString("Boolean parameter CP_CAP_AUTOSCALE has invalid " +
                                    "value nontrue. Please specify true/false/yes/no/on/off.")
                            .assertPageContainsString("Grid engine profile verification has failed. " +
                                    "Reverting the changes...")
                            // the output of this configure is read later from its command line,
                            // which has to stay on the screen
                            .clearScreen(prompt)
                            .execute(command[2])
                            .assertPageContains(format("Grid engine %s profile.", testProfile));
                    // the same message follows every earlier create and configure, so only this output counts
                    setProfileParametersInEditor(shell, profileParameters)
                            .waitForPromptAfterLastCommand(command[2], prompt)
                            .assertOutputOfLastCommandContains(command[2],
                                    format("Grid engine %s autoscaling has been launched.", testProfile))
                            .assertOutputOfLastCommandDoesNotContain(command[2],
                                    "Grid engine profile verification has failed.")
                            .executeAndWaitForPrompt(submitCommand, prompt)
                            // a job submitted with -t is an array job, which Grid Engine reports as
                            // "job-array <id>.<first>-<last>:<step>"; the plain "job <id>" form is accepted too
                            .assertOutputOfLastCommandMatches(submitCommand,
                                    "Your job(-array)? 1(\\.1-2:1)? \\(\"sleep\"\\) has been submitted");
                });

        // The log list is virtualized: only the rows on screen are in the page, and "Follow log" shows the end of
        // the task log, after the export lines of the last configure. containsMessages scrolls through the list,
        // so the parameter lines of the configure diff higher up are found as well.
        runsMenu()
                .showLog(getLastRunId())
                .clickTaskWithName("SGEProfiles")
                .sleep(10, SECONDS)
                .ensure(log(), containsMessages(expectedLogMessages()));

       // Set<String> logMessages = runsMenu()
       //         .showLog(parentRunId)
       //         .sleep(10, SECONDS)
       //         .clickTaskWithName("SGEProfiles")
       //         .sleep(30, SECONDS)
       //         .screenshot("screen_TC-PARAMETERS-6")
       //         .logMessages()
       //         .collect(toSet());
       // Arrays.stream(expectedLogMessages())
       //         .forEach(t -> new LogAO().logContainsMessage(logMessages, t.trim()));
        runsMenu()
                .showLog(parentRunId)
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance
                                .ensure(TYPE, text("c6i."))
                                .ensure(DISK, text("45"))
                                .ensure(IMAGE, text("/library/centos:latest")))
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_SGE_HOSTLIST_NAME", format("@%s", testProfile)), exist)
                .ensure(configurationParameter("CP_CAP_SGE_QUEUE_NAME", testProfile), exist);
    }

    @Test
    @TestCase(value = {"TC-PARAMETERS-7"})
    public void checkCondaRunCapability() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .selectRunCapability("conda")
                .click(START_IDLE)
                .setPriceType(ON_DEMAND)
                .launch(this)
                .showLog(getLastRunId())
                .waitForSshLink()
                .sshInNewTab(shell -> {
                    final String prompt = format("pipeline-%s", getLastRunId());
                    shell
                            .waitUntilTextAppears(getLastRunId())
                            // The conda capability runs conda from a shared file system (EFS): the first conda
                            // command of a new run loads conda from there and can take more than the default 30 s
                            // wait, the next ones take ~2 s
                            .executeAndWaitForPrompt(LIST_CONDA_ENVIRONMENTS, prompt, CONDA_COMMAND_TIMEOUT)
                            .assertOutputOfLastCommandMatches(LIST_CONDA_ENVIRONMENTS, BASE_CONDA_ENVIRONMENT_ROW)
                            .executeAndWaitForPrompt(CREATE_CONDA_ENVIRONMENT, prompt, CONDA_COMMAND_TIMEOUT)
                            .executeAndWaitForPrompt(LIST_CONDA_ENVIRONMENTS, prompt)
                            .assertOutputOfLastCommandMatches(LIST_CONDA_ENVIRONMENTS,
                                    BASE_CONDA_ENVIRONMENT_ROW, TEST_CONDA_ENVIRONMENT_ROW)
                            .executeAndWaitForPrompt(REMOVE_CONDA_ENVIRONMENT, prompt, CONDA_COMMAND_TIMEOUT)
                            .executeAndWaitForPrompt(LIST_CONDA_ENVIRONMENTS, prompt)
                            .assertOutputOfLastCommandMatches(LIST_CONDA_ENVIRONMENTS, BASE_CONDA_ENVIRONMENT_ROW)
                            .assertOutputOfLastCommandDoesNotContain(LIST_CONDA_ENVIRONMENTS, TEST_CONDA_ENVIRONMENT);
                });
    }

    /**
     * Stops the run while its non-admin owner is logged in: the active runs page of the admin lists only
     * the admin's own runs, so the cleanup of the class does not stop runs of other users.
     */
    private void stopRunAsOwner(final String runId) {
        if (runId == null) {
            return;
        }
        try {
            open(C.ROOT_ADDRESS);
            runsMenu().stopRunIfPresent(runId);
        } catch (Throwable e) {
            System.out.printf("Run %s could not be stopped: %s%n", runId, e.getMessage());
        }
    }

    /**
     * Sets both instance type masks of the test user, and reads them back from a newly opened dialog. The user dialog
     * does not always save a mask edit confirmed with OK. When 3433 set both masks and only one of them was saved,
     * its cleanup cleared that one, and it was not removed either, so USER2 stayed limited to r6i.* and the later
     * cases could not launch as USER2. A mask that is not saved is set again, at most three times.
     */
    private void setUserSettings(final String mask) {
        final String[] saved = new String[2];
        for (int attempt = 1; ; attempt++) {
            editTestUser()
                    .addAllowedLaunchOptions(INSTANCE_TYPES_MASK, mask)
                    .addAllowedLaunchOptions(TOOL_INSTANCE_TYPES_MASK, mask)
                    .ok();
            editTestUser()
                    .also(popup -> {
                        saved[0] = popup.getAllowedLaunchOption(INSTANCE_TYPES_MASK);
                        saved[1] = popup.getAllowedLaunchOption(TOOL_INSTANCE_TYPES_MASK);
                    })
                    .ok();
            if (mask.equals(saved[0]) && mask.equals(saved[1])) {
                return;
            }
            final String notSaved = format("The masks of %s are not saved (attempt %d): '%s' expected, "
                    + "the instance types mask is '%s', the tool instance types mask is '%s'",
                    user.login, attempt, mask, saved[0], saved[1]);
            System.out.println(notSaved);
            if (attempt == USER_MASKS_ATTEMPTS) {
                fail(notSaved);
            }
        }
    }

    private SettingsPageAO.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup editTestUser() {
        return navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(user.login)
                .edit();
    }

    private String autoscalingMessage(double resize) {
        // as the daemon's bc, multiply the exact ratio and drop the fraction; a double can land just below an integer
        final int addedSize = new BigDecimal(C.CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO)
                .multiply(BigDecimal.valueOf((long) resize))
                .setScale(0, RoundingMode.FLOOR)
                .intValue();
        return format("Filesystem /ebs was autoscaled %sG + %sG = %sG.",
                (int) resize, addedSize, (int) resize + addedSize);
    }

    private void deleteToolVersionIfPresent(final String version) {
        try {
            tools().performWithin(registry, group, tool, toolPage ->
                    toolPage.versions()
                            .viewUnscannedVersions()
                            .performIf(hasOnPage(version), versions -> versions.deleteVersion(version)));
        } catch (Throwable e) {
            System.out.printf("Tool version %s could not be deleted: %s%n", version, e.getMessage());
        }
    }

    private String logMessage(int size) {
        return format("btrfs%5sG", size);
    }

    private int[] diskSize(String log) {
        final Matcher matcher = compile("([\\d]*[.,]*[\\d]+)")
                .matcher(log.substring(log.indexOf("btrfs"), log.indexOf("/tmpfs")));
        int[] res = new int[4];
        int i = 0;
        while(matcher.find()) {
            res[i] = (int)parseDouble(matcher.group());
            i++;
        }
        return res;
    }

    private void createBigFile (ShellAO shell) {
        String lastResult = shell
                .waitUntilTextAppears(getLastRunId())
                .execute(CHECK_SPACE_COMMAND)
                .assertNextStringIsVisible(CHECK_SPACE_COMMAND, rootHost)
                .lastCommandResult(CHECK_SPACE_COMMAND);
        scaling = diskSize(lastResult);
        sizeDisk[0] = (int) Math.floor(scaling[0] * SCALING_COEFF);
        for(int i = 0; i < 3; i++) {
            sizeDisk[i+1] = (int) Math.floor(sizeDisk[i] * SCALING_COEFF);
        }
        // The file takes about all the space df shows, so that the disk passes the autoscaling threshold. A file of
        // all of it but 1G fails with "No space left on device" when it does not fit (df -h rounds up, and btrfs
        // keeps some space of its own): the largest of a few sizes that fits is kept. The SSH tab is closed right
        // after this method, so the prompt is awaited, and the file must exist.
        final int available = scaling[2];
        final String createBigFile = format("for s in %d %d %d %d; do fallocate -l ${s}G test.big && break; "
                + "rm -f test.big; done; ls -sh test.big", available - 1, available - 2, available - 3, available - 4);
        shell.executeAndWaitForPrompt(createBigFile, rootHost, FALLOCATE_TIMEOUT)
                .assertOutputOfLastCommandMatches(createBigFile, "\\d+(\\.\\d+)?G test\\.big");
    }

    private void checkFilesystemSpace(ShellAO shell, int diskSize) {
        shell.waitUntilTextAppears(getLastRunId())
                .execute(CHECK_SPACE_COMMAND)
                .assertNextStringIsVisible(CHECK_SPACE_COMMAND, rootHost)
                .assertPageAfterCommandContainsStrings(CHECK_SPACE_COMMAND, logMessage(diskSize));
    }

    private void checkHDDpreferences() {
        PreferencesAO preferencesAO = navigationMenu()
                .settings()
                .switchToPreferences();
        boolean[] hddScaleEnabled = preferencesAO
                .getCheckboxPreferenceState(CLUSTER_INSTANCE_HDD_SCALE_ENABLED);
        assertTrue(hddScaleEnabled[0],
                format("Preference '%s' isn't enabled", CLUSTER_INSTANCE_HDD_SCALE_ENABLED));

        String[] hddScaleDeltaRatio = preferencesAO
                .getLinePreference(CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO);
        assertTrue(hddScaleDeltaRatio[0].equals(C.CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO),
                format("Preference '%s' has value '%s' instead of '%s'", CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO,
                        hddScaleDeltaRatio[0], C.CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO));
    }

    private String[] expectedLogMessages() {
        final List<String> loglist = profileParameters.stream()
                .map(parameter -> "> " + parameter)
                .collect(toCollection(ArrayList::new));
        loglist.add(format("Grid engine %s autoscaling has been launched.", testProfile));
        return loglist.toArray(new String[0]);
    }

    /**
     * Uncomments the line of each parameter in the profile opened in the text editor (vi), sets the given value
     * and saves the profile. A parameter line that already has a value is changed the same way.
     */
    private ShellAO setProfileParametersInEditor(final ShellAO shell, final List<String> exports) {
        exports.forEach(export -> {
            final String name = export.substring("export ".length(), export.indexOf('='));
            shell.execute(format(":%%s;^.*export %s=.*$;%s;", name, export));
        });
        return shell.execute(":wq");
    }
}


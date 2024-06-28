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

import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO.PreferencesAO;
import com.epam.pipeline.autotests.ao.ShellAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.ConfigurationPermission;
import com.epam.pipeline.autotests.utils.Json;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.SystemParameter;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Condition.text;
import static com.epam.pipeline.autotests.ao.LogAO.Status.STOPPED;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.REMOVE_PARAMETER;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.START_IDLE;
import static com.epam.pipeline.autotests.ao.Primitive.TYPE;
import static com.epam.pipeline.autotests.ao.Profile.advancedTab;
import static com.epam.pipeline.autotests.ao.ToolVersions.hasOnPage;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.lang.Double.parseDouble;
import static java.util.regex.Pattern.compile;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LaunchParametersTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private static final String LAUNCH_PARAMETERS_PREFERENCE = SettingsPageAO.PreferencesAO.LaunchAO.LAUNCH_PARAMETERS;
    private static final String LAUNCH_PARAMETER_RESOURCE = "launch-parameter";
    private static final String CP_FSBROWSER_ENABLED = "CP_FSBROWSER_ENABLED";
    private static final String USER_ROLE = "ROLE_PIPELINE_MANAGER";
    private static final String PARAMETER_IS_NOT_ALLOWED_FOR_USE = "This parameter is not allowed for use";
    private static final String PARAMETER_IS_RESERVED = "Parameter name is reserved";
    private static final String NAME_IS_RESERVED = "Name is reserved for system parameter";
    private static final String PROFILE_NAME = "default";
    private static final String UMOUNT_COMMAND = "echo \"#!/usr/bin/env bash\" > /usr/local/sbin/umount && " +
            "echo \"sleep infinity\" >> /usr/local/sbin/umount && chmod +x /usr/local/sbin/umount";
    private static final String TERMINATE_RUN_TIMEOUT = "CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN";
    private static final int DEFAULT_TERMINATE_RUN_TIMEOUT = 1;
    private static final int TEST_TERMINATE_RUN_TIMEOUT = DEFAULT_TERMINATE_RUN_TIMEOUT + 2;
    private static final String UNMOUNTING_STARTED = "Unmounting all storage mounts";
    private static final String CLEANUP_WORNING = "Will wait for %smin to let the run stop normally. " +
            "Otherwise it will be terminated";
    private static final String CLEANUP_FINISH_MESSAGE = "Run #%s is still running after %smin. Terminating a node.";
    private static final String CONSOLE = "Console";
    private static final String CLEANUP_ENVIRONMENT_TASK = "CleanupEnvironment";
    private static final String FILESYSTEM_AUTOSCALING = "FilesystemAutoscaling";
    private static final String CLUSTER_INSTANCE_HDD_SCALE_ENABLED = "cluster.instance.hdd.scale.enabled";
    private static final String CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO = "cluster.instance.hdd.scale.delta.ratio";
    private static final double SCALING_COEFF = 1 + Double.parseDouble(C.CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO);
    private static final String CHECK_SPACE_COMMAND = "df -hT";
    private final String pipeline = resourceName(LAUNCH_PARAMETER_RESOURCE);
    private final String configuration = resourceName(format("%s-configuration", LAUNCH_PARAMETER_RESOURCE));
    private final String configurationDescription = "test-configuration-description";
    private final String rootHost = "root@pipeline";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String customTag = "test_tag";
    private static String testInstance = "r6i.%s";
    private int[] scaling = new int[4];
    private int[] sizeDisk = new int[4];
    private String initialLaunchSystemParameters;

    @BeforeClass(alwaysRun = true)
    public void setPreferences() {
        library()
                .createPipeline(pipeline);
        Stream.of(user, userWithoutCompletedRuns).forEach(user -> {
            addAccountToPipelinePermissions(user, pipeline);
            givePermissions(user,
                    PipelinePermission.allow(READ, pipeline),
                    PipelinePermission.allow(EXECUTE, pipeline),
                    PipelinePermission.allow(WRITE, pipeline)
            );
        });
        library()
                .createConfiguration(conf ->
                        conf.setName(configuration).setDescription(configurationDescription).ok()
                );
        Stream.of(user, userWithoutCompletedRuns).forEach(user -> {
            addAccountToConfigurationPermissions(user, configuration);
            givePermissions(user,
                    ConfigurationPermission.allow(READ, configuration),
                    ConfigurationPermission.allow(EXECUTE, configuration),
                    ConfigurationPermission.allow(WRITE, configuration)
            );
        });
        initialLaunchSystemParameters = editLaunchSystemParameters();
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(userWithoutCompletedRuns.login)
                .edit()
                .deleteRoleOrGroupIfExist(USER_ROLE)
                .ok();
    }

    @AfterClass(alwaysRun = true)
    public void deleteCustomVersion() {
        open(C.ROOT_ADDRESS);
        tools()
                .perform(registry, group, tool, tool ->
                        tool.versions()
                                .viewUnscannedVersions()
                                .performIf(hasOnPage(customTag), t -> t.deleteVersion(customTag))
                );
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        open(C.ROOT_ADDRESS);
        logoutIfNeeded();
        loginAs(admin);
        library()
                .removeConfigurationIfExists(configuration)
                .removePipelineIfExists(pipeline);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(LAUNCH_PARAMETERS_PREFERENCE, initialLaunchSystemParameters, true)
                .saveIfNeeded();
    }

    @Test
    @TestCase(value = {"2342_1"})
    public void checkSystemParametersForToolAndLaunchForm() {
        logoutIfNeeded();
        loginAs(userWithoutCompletedRuns);
        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .clickSystemParameter()
                                .searchSystemParameter(CP_FSBROWSER_ENABLED)
                                .validateNotFoundParameters()
                                .cancel()
                                .clickCustomParameter()
                                .setName(CP_FSBROWSER_ENABLED)
                                .messageShouldAppear(PARAMETER_IS_NOT_ALLOWED_FOR_USE)
                );
        tools()
                .perform(registry, group, tool, tool ->
                        tool.runWithCustomSettings()
                                .clickAddSystemParameter()
                                .searchSystemParameter(CP_FSBROWSER_ENABLED)
                                .validateNotFoundParameters()
                                .cancel()
                                .clickAddStringParameter()
                                .setName(CP_FSBROWSER_ENABLED)
                                .messageShouldAppear(PARAMETER_IS_NOT_ALLOWED_FOR_USE)
                );
    }

    @Test
    @TestCase(value = {"2342_2"})
    public void checkSystemParametersForPipelineAndDetachConfiguration() {
        logoutIfNeeded();
        loginAs(userWithoutCompletedRuns);
        library()
                .clickOnPipeline(pipeline)
                .firstVersion()
                .configurationTab()
                .editConfiguration(PROFILE_NAME, profile -> {
                    profile.sleep(1, SECONDS)
                            .addSystemParameter()
                            .sleep(1, SECONDS)
                            .searchSystemParameter(CP_FSBROWSER_ENABLED)
                            .validateNotFoundParameters()
                            .cancel();
                    profile.clickAddStringParameter()
                            .setName(CP_FSBROWSER_ENABLED)
                            .messageShouldAppear(PARAMETER_IS_NOT_ALLOWED_FOR_USE)
                            .click(REMOVE_PARAMETER);
                });
        library()
                .configurationWithin(configuration, configuration -> {
                    configuration
                            .expandTabs(advancedTab)
                            .addSystemParameter()
                            .searchSystemParameter(CP_FSBROWSER_ENABLED)
                            .validateNotFoundParameters()
                            .cancel();
                    configuration
                            .addStringParameter(CP_FSBROWSER_ENABLED, "")
                            .messageShouldAppear(PARAMETER_IS_NOT_ALLOWED_FOR_USE)
                            .deleteParameter(CP_FSBROWSER_ENABLED);
                });
    }

    @Test
    @TestCase(value = {"2342_3"})
    public void checkAllowedSystemParametersForToolAndLaunchForm() {
        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .clickSystemParameter()
                                .selectSystemParameters(CP_FSBROWSER_ENABLED)
                                .cancel()
                                .clickCustomParameter()
                                .setName(CP_FSBROWSER_ENABLED)
                                .messageShouldAppear(PARAMETER_IS_RESERVED)
                );
        tools()
                .perform(registry, group, tool, tool ->
                        tool.runWithCustomSettings()
                                .clickAddSystemParameter()
                                .selectSystemParameters(CP_FSBROWSER_ENABLED)
                                .cancel()
                                .clickAddStringParameter()
                                .setName(CP_FSBROWSER_ENABLED)
                                .messageShouldAppear(NAME_IS_RESERVED)
                );
    }

    @Test
    @TestCase(value = {"2342_4"})
    public void checkAllowedSystemParametersForPipelineAndDetachConfiguration() {
        logoutIfNeeded();
        loginAs(user);
        library()
                .clickOnPipeline(pipeline)
                .firstVersion()
                .configurationTab()
                .editConfiguration(PROFILE_NAME, profile -> {
                    profile.addSystemParameter()
                            .selectSystemParameters(CP_FSBROWSER_ENABLED)
                            .cancel();
                    profile.clickAddStringParameter()
                            .setName(CP_FSBROWSER_ENABLED)
                            .messageShouldAppear(NAME_IS_RESERVED)
                            .click(REMOVE_PARAMETER);
                });
        library()
                .configurationWithin(configuration, configuration -> {
                    configuration
                            .expandTabs(advancedTab)
                            .addSystemParameter()
                            .selectSystemParameters(CP_FSBROWSER_ENABLED)
                            .cancel();
                    configuration
                            .addStringParameter(CP_FSBROWSER_ENABLED, "")
                            .messageShouldAppear(NAME_IS_RESERVED)
                            .deleteParameter(CP_FSBROWSER_ENABLED);
                });
    }

    @Test(priority = 1)
    @TestCase(value = {"2342_5"})
    public void checkChangesSystemParameters() {
        try {
            logoutIfNeeded();
            loginAs(user);
            tools()
                    .perform(registry, group, tool, tool ->
                            tool.settings()
                                    .clickSystemParameter()
                                    .selectSystemParameters(CP_FSBROWSER_ENABLED)
                                    .ok()
                                    .save()
                    );
            library()
                    .clickOnPipeline(pipeline)
                    .firstVersion()
                    .configurationTab()
                    .editConfiguration(PROFILE_NAME, profile -> {
                        profile.sleep(1, SECONDS)
                                .addSystemParameter()
                                .sleep(1, SECONDS)
                                .selectSystemParameters(CP_FSBROWSER_ENABLED)
                                .sleep(1, SECONDS)
                                .ok()
                                .doNotMountStoragesSelect(true)
                                .click(SAVE);
                        profile.waitUntilSaveEnding(PROFILE_NAME);
                    });
            library()
                    .configurationWithin(configuration, configuration ->
                            configuration
                                    .selectDockerImage(dockerImage ->
                                            dockerImage
                                                    .selectRegistry(registry)
                                                    .selectGroup(group)
                                                    .selectTool(tool, "test")
                                                    .click(OK)
                                    )
                                    .setValue(DISK, "17")
                                    .sleep(1, SECONDS)
                                    .selectValue(INSTANCE_TYPE, C.DEFAULT_INSTANCE)
                                    .expandTabs(advancedTab)
                                    .addSystemParameter()
                                    .selectSystemParameters(CP_FSBROWSER_ENABLED)
                                    .ok()
                                    .click(SAVE)
                    );
            logoutIfNeeded();
            loginAs(userWithoutCompletedRuns);
            tools()
                    .perform(registry, group, tool, tool ->
                            tool.settings()
                                    .validateDisabledParameter(CP_FSBROWSER_ENABLED)
                                    .runWithCustomSettings()
                                    .expandTab(advancedTab)
                                    .validateDisabledParameter(CP_FSBROWSER_ENABLED)
                    );
            library()
                    .clickOnPipeline(pipeline)
                    .firstVersion()
                    .configurationTab()
                    .runPipeline()
                    .validateDisabledParameter(CP_FSBROWSER_ENABLED);
            library()
                    .configurationWithin(configuration, configuration -> {
                        configuration
                                .expandTabs(advancedTab);
                        new PipelineRunFormAO()
                                .validateDisabledParameter(CP_FSBROWSER_ENABLED);
                    });
        } finally {
            logoutIfNeeded();
            loginAs(admin);
            tools()
                    .perform(registry, group, tool, tool ->
                            tool.settings()
                                    .deleteParameter(CP_FSBROWSER_ENABLED)
                                    .save()
                    );
        }
    }

    @Test(priority = 1)
    @TestCase(value = {"2342_6"})
    public void checkRestrictedSystemParametersViaCLI() {
        logoutIfNeeded();
        loginAs(userWithoutCompletedRuns);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute(format("pipe run -di %s --CP_FSBROWSER_ENABLED true", tool))
                        .assertPageContainsString("An error has occurred while starting a job: " +
                                "\"CP_FSBROWSER_ENABLED\" parameter is not permitted for overriding")
                        .close()
                );
    }

    @Test
    @TestCase(value = {"2736"})
    public void forcibleTerminateInstancesIfJobIsStuckInUmount() {
        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setCommand(UMOUNT_COMMAND)
                .launchTool(this, nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForTask(CONSOLE)
                .clickTaskWithName(CONSOLE)
                .waitForLog(UNMOUNTING_STARTED)
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
                .clickTaskWithName(CONSOLE)
                .waitForLog(UNMOUNTING_STARTED)
                .waitForTask(CLEANUP_ENVIRONMENT_TASK)
                .clickTaskWithName(CLEANUP_ENVIRONMENT_TASK)
                .ensure(log(), containsMessages(format(CLEANUP_WORNING, valueOf(TEST_TERMINATE_RUN_TIMEOUT))))
                .sleep(TEST_TERMINATE_RUN_TIMEOUT, MINUTES)
                .refresh()
                .shouldHaveStatus(STOPPED)
                .ensure(log(), containsMessages(format(CLEANUP_FINISH_MESSAGE,
                        getLastRunId(), valueOf(TEST_TERMINATE_RUN_TIMEOUT))));
    }

    @Test
    @TestCase(value = {"3433"})
    public void allowToUseR6iInstanceFamilyInSGEhybridAutoscaling() {
        try {
            logoutIfNeeded();
            loginAs(admin);
            setUserSettings(format(testInstance, "*"));
            logoutIfNeeded();
            loginAs(user);
            tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .expandTab(ADVANCED_PANEL)
                    .setTypeValue(format(testInstance, "xlarge"))
                    .setPriceType(ON_DEMAND)
                    .doNotMountStoragesSelect(true)
                    .enableClusterLaunch()
                    .clusterSettingsForm("Auto-scaled cluster")
                    .enableHybridClusterSelect()
                    .ok()
                    .launchTool(this, nameWithoutGroup(tool))
                    .showLog(getLastRunId())
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
            open(C.ROOT_ADDRESS);
            logoutIfNeeded();
            loginAs(admin);
            setUserSettings("");
        }
    }

    @Test
    @TestCase(value = "913")
    @CloudProviderOnly(values = {Cloud.AWS})
    public void addSupportForAutoscalingFilesystemForAWS() {
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
                           .inAnotherTab(logTab -> logTab
                                   .ssh(shell -> createBigFile(shell))
                           )
                           .waitForTask(FILESYSTEM_AUTOSCALING)
                           .clickTaskWithName(FILESYSTEM_AUTOSCALING)
                           .ensure(log(), containsMessages(autoscalingMessage(scaling[0])))
                           .inAnotherTab(logTab -> logTab
                                   .ssh(shell -> checkFilesystemSpace(shell, sizeDisk[0])))
                           .commit(commit -> commit.setVersion(customTag).ok())
                           .assertCommittingFinishedSuccessfully()
                           .clickTaskWithName(FILESYSTEM_AUTOSCALING)
                           .ensure(log(), containsMessages(autoscalingMessage(sizeDisk[0])))
                           .ensure(log(), containsMessages(autoscalingMessage(sizeDisk[1])))
                           .inAnotherTab(logTab -> logTab
                                   .ssh(shell -> checkFilesystemSpace(shell, sizeDisk[2])))
                           .pause(nameWithoutGroup(tool))
                           .assertPausingFinishedSuccessfully()
                           .resume(nameWithoutGroup(tool))
                           .assertResumingFinishedSuccessfully()
                           .waitForSshLink()
                           .clickTaskWithName(FILESYSTEM_AUTOSCALING)
                           .ensure(log(), containsMessages(autoscalingMessage(sizeDisk[2])))
                           .inAnotherTab(logTab -> logTab
                                   .ssh(shell -> {
                                       checkFilesystemSpace(shell, sizeDisk[3]);
                                       shell.close();
                                   })
                           )
                );
    }

    private String editLaunchSystemParameters() {
        final String launchSystemParameters = navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToLaunch()
                .getLaunchSystemParameters();
        final SystemParameter[] systemParameters = Json.stringToSystemParameters(launchSystemParameters);
        final SystemParameter[] systemParameterList = Arrays.stream(systemParameters)
                .peek(p -> {
                    if ("CP_FSBROWSER_ENABLED".equals(p.getName())) {
                        p.setRoles(new String[] { USER_ROLE });
                    }
                })
                .toArray(SystemParameter[]::new);
        final String systemParametersToString = Json.systemParametersToString(systemParameterList);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(LAUNCH_PARAMETERS_PREFERENCE, systemParametersToString, true)
                .saveIfNeeded();
        return launchSystemParameters;
    }

    private void setUserSettings(String mask) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(user.login)
                .edit()
                .addAllowedLaunchOptions("Allowed instance types mask", mask)
                .sleep(2, SECONDS)
                .ok();
    }

    private String autoscalingMessage(double resize) {
        return format("Filesystem /ebs was autoscaled %sG + %sG = %sG.",
                (int)resize, (int)Math.floor(resize/2), (int)resize + (int)Math.floor(resize/2));
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
        shell.execute(format("fallocate -l %sG test.big", scaling[2] - 1));
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
}

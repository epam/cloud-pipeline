/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.PAUSE;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.ConfigurationPermission;
import com.epam.pipeline.autotests.utils.Json;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.SystemParameter;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.Status.STOPPED;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.REMOVE_PARAMETER;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Profile.advancedTab;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LaunchParametersTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private static final String LAUNCH_PARAMETERS_PREFERENCE = SettingsPageAO.PreferencesAO.LaunchAO.LAUNCH_PARAMETERS;
    private static final String LAUNCH_PARAMETER_RESOURCE = "launch-parameter";
    private static final String LAUNCH_JOB_DISK_SIZE_THRESHOLDS = "launch.job.disk.size.thresholds";
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
    private static final String DISK_SIZE_THRESHOLDS_JSON = "/diskSizeThresholds.json";
    private final String pipeline = resourceName(LAUNCH_PARAMETER_RESOURCE);
    private final String configuration = resourceName(format("%s-configuration", LAUNCH_PARAMETER_RESOURCE));
    private final String configurationDescription = "test-configuration-description";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private String initialLaunchSystemParameters;
    private String[] prefInitialValue;


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
                .launchTool(this, Utils.nameWithoutGroup(tool))
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
                .launchTool(this, Utils.nameWithoutGroup(tool))
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
                .launchTool(this, Utils.nameWithoutGroup(tool))
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
    @TestCase(value = {"3074"})
    public void launchFormDiskSizeDisclaimers() {
        logoutIfNeeded();
        loginAs(admin);
        prefInitialValue = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(LAUNCH_JOB_DISK_SIZE_THRESHOLDS);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(LAUNCH_JOB_DISK_SIZE_THRESHOLDS,
                        readResourceFully(DISK_SIZE_THRESHOLDS_JSON), true)
                .saveIfNeeded();
        try {
            tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(ADVANCED_PANEL)
                    .setPriceType(ON_DEMAND)
                    .expandTab(EXEC_ENVIRONMENT)
                    .setDisk("40")
                    .checkLaunchMessage("info", "Threshold disclaimer 1.", true)
                    .setDisk("50")
                    .checkLaunchMessage("warning",
                            "Threshold disclaimer 2. Your job size: 50Gb, larger or equal 50Gb", true)
                    .setDisk("80")
                    .checkLaunchMessage("error",
                            "Threshold disclaimer 3. Your disk: 80Gb, larger then 70Gb", true)
                    .launch(this);
            runsMenu()
                    .activeRuns()
                    .showLog(getLastRunId())
                    .waitForSshLink()
                    .ensure(PAUSE, not(visible));
        } finally {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .updateCodeText(LAUNCH_JOB_DISK_SIZE_THRESHOLDS,
                            prefInitialValue[0], true)
                    .saveIfNeeded();
        }
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
}

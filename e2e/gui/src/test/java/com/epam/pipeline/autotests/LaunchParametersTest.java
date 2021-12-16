/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.REMOVE_PARAMETER;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Profile.advancedTab;
import static com.epam.pipeline.autotests.ao.Profile.execEnvironmentTab;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LaunchParametersTest extends AbstractAutoRemovingPipelineRunningTest implements Navigation, Authorization {

    private static final String LAUNCH_PARAMETERS_PREFERENCE = SettingsPageAO.PreferencesAO.LaunchAO.LAUNCH_PARAMETERS;
    private static final String LAUNCH_PARAMETER_RESOURCE = "launch-parameter";
    private static final String CP_FSBROWSER_ENABLED = "CP_FSBROWSER_ENABLED";
    private static final String USER_ROLE = "ROLE_PIPELINE_MANAGER";
    private static final String PARAMETER_IS_NOT_ALLOWED_FOR_USE = "This parameter is not allowed for use";
    private static final String PARAMETER_IS_RESERVED = "Parameter name is reserved";
    private static final String NAME_IS_RESERVED = "Name is reserved for system parameter";
    private static final String PROFILE_NAME = "default";
    private final String pipeline = resourceName(LAUNCH_PARAMETER_RESOURCE);
    private final String configuration = resourceName(format("%s-configuration", LAUNCH_PARAMETER_RESOURCE));
    private final String configurationDescription = "test-configuration-description";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
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
                                    .expandTabs(execEnvironmentTab)
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
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(format("pipe run -di %s --CP_FSBROWSER_ENABLED true", tool))
                        .assertPageContainsString("An error has occurred while starting a job: " +
                                "\"CP_FSBROWSER_ENABLED\" parameter is not permitted for overriding")
                        .close()
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
}

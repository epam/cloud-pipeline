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
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.ToolSettings;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Selectors.byTitle;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.RUN_CAPABILITIES;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Profile.execEnvironmentTab;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ToolsParametersTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private static final String CUSTOM_CAPABILITIES_1_JSON = "/customCapabilities1.json";
    private static final String CUSTOM_CAPABILITIES_2_JSON = "/customCapabilities2.json";
    private static final String CUSTOM_CAPABILITIES_3_JSON = "/customCapabilities3.json";
    private static final String SYSTEM_D = "SystemD";
    private static final String TOOLTIP_1 = "This capability is not allowed\nSupported OS versions:\ndebian 10\n" +
            "centos 8";
    private static final String CUSTOM_CAPABILITY_DESC_1 = "Custom test capability 1";
    private static final String CUSTOM_CAPABILITY_DESC_2 = "Custom test capability 2";
    private static final String CUSTOM_CAPABILITY_DESC_3 = "Custom test capability 3";
    private static final String CUSTOM_CAPABILITY_DESC_4 = "Custom test capability 4";
    private static final String DEFAULT_CONFIGURATION = "default";
    private static final String RUN_CAPABILITIES_TITLE = "Run capabilities";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String centosTool = format("%s/%s", C.ANOTHER_GROUP, "centos");
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String anotherGroup = C.ANOTHER_GROUP;
    private final String invalidEndpoint = "8700";
    private final String launchCapabilities = "launch.capabilities";
    private final String CUSTOM_CAPABILITY_1 = "testCapability1";
    private final String CUSTOM_CAPABILITY_2 = "testCapability2";
    private final String CUSTOM_CAPABILITY_3 = "testCapability3";
    private final String CUSTOM_CAPABILITY_4 = "testCapability4";
    private final String capabilityParam = "CP_CAP_CUSTOM_%s";
    private final String logMessage = "Running '%s' commands:";
    private final String pipeline2323 = "tool-parameters-2323-" + Utils.randomSuffix();
    private final String configuration2323 = "configuration-2323-" + Utils.randomSuffix();
    private String[] prefInitialValue;

    @BeforeClass
    public void getPreferencesValue() {
        fallbackToToolDefaultState(registry, group, tool);
        prefInitialValue = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(launchCapabilities);
    }

    @BeforeMethod
    void openApplication() {
        logoutIfNeeded();
        loginAs(admin);
    }

    @AfterClass(alwaysRun = true)
    public void fallBackToDefaultToolSettings() {
        logoutIfNeeded();
        loginAs(admin);
        fallbackToToolDefaultState(registry, group, tool);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(launchCapabilities, prefInitialValue[0], parseBoolean(prefInitialValue[1]))
                .saveIfNeeded();
    }

    @Test(priority = 1)
    @TestCase(value = {"EPMCMBIBPC-502"})
    public void runToolThatHaveNoNginxEndpoint() {
        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .sleep(1, SECONDS)
                                .also(removeAllEndpoints())
                                .addEndpoint(invalidEndpoint)
                                .save()
                                .run(this)
                )
                .showLog(getLastRunId());
        new RunsMenuAO()
                .waitForInitializeNode(getLastRunId())
                .clickEndpoint()
                .screenshot("test501screenshot")
                .assertPageTitleIs("502 Bad Gateway");
    }

    @Test(priority = 2)
    @TestCase(value = {"2234"})
    public void customCapabilitiesImplementation() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(launchCapabilities,
                        readResourceFully(CUSTOM_CAPABILITIES_1_JSON), true)
                .saveIfNeeded();
        logout();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .selectRunCapability(CUSTOM_CAPABILITY_1)
                .click(byTitle(RUN_CAPABILITIES_TITLE))
                .selectRunCapability(CUSTOM_CAPABILITY_2)
                .checkTooltipText(CUSTOM_CAPABILITY_1, CUSTOM_CAPABILITY_DESC_1)
                .checkTooltipText(CUSTOM_CAPABILITY_2, CUSTOM_CAPABILITY_DESC_2)
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(format(capabilityParam, CUSTOM_CAPABILITY_1), "true"), exist)
                .ensure(configurationParameter(format(capabilityParam, CUSTOM_CAPABILITY_2), "true"), exist)
                .waitForSshLink()
                .clickTaskWithName("Console")
                .waitForLog("start.sh")
                .ensure(log(), matchText(format(logMessage, CUSTOM_CAPABILITY_1)))
                .ensure(log(), matchText(format(logMessage, CUSTOM_CAPABILITY_2)))
                .ensure(log(), matchText( "Command: 'echo testLine1'"))
                .ensure(log(), matchText( "Command: 'echo testLine2'"))
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute("ls")
                        .assertOutputContains("testFile1.txt")
                        .execute("cat testFile1.txt")
                        .assertOutputContains("testLine1", "testLine2")
                        .close());
    }

    @Test(priority = 2)
    @TestCase(value = {"2295"})
    public void customCapabilitiesWithConfiguredJobParameters() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(launchCapabilities,
                        readResourceFully(CUSTOM_CAPABILITIES_2_JSON), true)
                .saveIfNeeded();
        logout();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .selectRunCapability(CUSTOM_CAPABILITY_1)
                .click(byTitle(RUN_CAPABILITIES_TITLE))
                .selectRunCapability(CUSTOM_CAPABILITY_2)
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(format(capabilityParam, CUSTOM_CAPABILITY_1), "true"), exist)
                .ensure(configurationParameter(format(capabilityParam, CUSTOM_CAPABILITY_2), "true"), exist)
                .ensure(configurationParameter("MY_PARAM1", "MY_VALUE1"), exist)
                .ensure(configurationParameter("MY_PARAM2", "MY_VALUE2"), exist)
                .ensure(configurationParameter("MY_BOOLEAN_PARAM", "false"), exist)
                .ensure(configurationParameter("MY_NUMBER_PARAM", "12"), exist);
    }

    @Test
    @TestCase(value = {"2323_1"})
    public void customCapabilitiesForToolDockerImageOS() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(launchCapabilities,
                        readResourceFully(CUSTOM_CAPABILITIES_3_JSON), true)
                .saveIfNeeded();
        tools().perform(registry, group, tool, tool ->
                        tool
                                .versions()
                                .viewUnscannedVersions()
                                .scanVersionIfNeeded("latest")
        );
        ToolSettings toolSettings = tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .click(RUN_CAPABILITIES)
                                .sleep(2, SECONDS)
                                .checkCustomCapability(CUSTOM_CAPABILITY_1, false)
                                .checkCustomCapability(CUSTOM_CAPABILITY_3, false)
                                .checkCustomCapability(CUSTOM_CAPABILITY_4, false)
                                .checkCustomCapability(CUSTOM_CAPABILITY_2, true)
                                .checkCustomCapability(SYSTEM_D, false)
                                .checkCapabilityTooltip(CUSTOM_CAPABILITY_2, TOOLTIP_1)
                                .click(byTitle(RUN_CAPABILITIES_TITLE))
                                .selectRunCapability(CUSTOM_CAPABILITY_1)
                                .click(byTitle(RUN_CAPABILITIES_TITLE))
                                .selectRunCapability(CUSTOM_CAPABILITY_3));
        final PipelineRunFormAO pipelineRunFormAO = new PipelineRunFormAO()
                .checkTooltipText(CUSTOM_CAPABILITY_1, CUSTOM_CAPABILITY_DESC_1)
                .checkTooltipText(CUSTOM_CAPABILITY_3, CUSTOM_CAPABILITY_DESC_3);
        toolSettings
                .runWithCustomSettings()
                .expandTab(execEnvironmentTab)
                .openRunCapabilityDropDown()
                .sleep(2, SECONDS)
                .checkCustomCapability(CUSTOM_CAPABILITY_1, false)
                .checkCustomCapability(CUSTOM_CAPABILITY_3, false)
                .checkCustomCapability(CUSTOM_CAPABILITY_2, true)
                .checkCustomCapability(SYSTEM_D, false)
                .checkCapabilityTooltip(CUSTOM_CAPABILITY_2, TOOLTIP_1)
                .click(byTitle(RUN_CAPABILITIES_TITLE))
                .selectRunCapability(CUSTOM_CAPABILITY_1)
                .click(byTitle(RUN_CAPABILITIES_TITLE))
                .selectRunCapability(CUSTOM_CAPABILITY_3)
                .checkTooltipText(CUSTOM_CAPABILITY_1, CUSTOM_CAPABILITY_DESC_1)
                .checkTooltipText(CUSTOM_CAPABILITY_3, CUSTOM_CAPABILITY_DESC_3);
    }

    @Test(dependsOnMethods = {"customCapabilitiesForToolDockerImageOS"})
    @TestCase(value = {"2323_2"})
    public void customCapabilitiesForPipelineAndDetachConfigurationDockerImageOS() {
        library()
                .createPipeline(pipeline2323)
                .createConfiguration(configuration2323)
                .clickOnPipeline(pipeline2323)
                .firstVersion()
                .configurationTab()
                .click(execEnvironmentTab)
                .editConfiguration(DEFAULT_CONFIGURATION, profile ->
                        profile.selectDockerImage(dockerImage ->
                                dockerImage
                                        .selectRegistry(registry)
                                        .selectGroup(group)
                                        .selectTool(tool)
                                        .click(OK)
                ));
        final PipelineRunFormAO pipelineRunFormAO = new PipelineRunFormAO();
        pipelineRunFormAO
                .openRunCapabilityDropDown()
                .checkCustomCapability(CUSTOM_CAPABILITY_1, false)
                .checkCustomCapability(CUSTOM_CAPABILITY_3, false)
                .checkCustomCapability(CUSTOM_CAPABILITY_2, true)
                .checkCapabilityTooltip(CUSTOM_CAPABILITY_2, TOOLTIP_1)
                .click(byTitle(RUN_CAPABILITIES_TITLE))
                .selectRunCapability(CUSTOM_CAPABILITY_1)
                .click(byTitle(RUN_CAPABILITIES_TITLE))
                .selectRunCapability(CUSTOM_CAPABILITY_3)
                .checkTooltipText(CUSTOM_CAPABILITY_1, CUSTOM_CAPABILITY_DESC_1)
                .checkTooltipText(CUSTOM_CAPABILITY_3, CUSTOM_CAPABILITY_DESC_3)
                .sleep(1, SECONDS)
                .click(SAVE)
                .waitUntilSaveEnding(DEFAULT_CONFIGURATION);
        library()
                .configurationWithin(configuration2323, configuration -> {
                    configuration
                                    .selectPipeline(pipeline2323);
                    pipelineRunFormAO
                            .checkTooltipText(CUSTOM_CAPABILITY_1, CUSTOM_CAPABILITY_DESC_1)
                            .checkTooltipText(CUSTOM_CAPABILITY_3, CUSTOM_CAPABILITY_DESC_3)
                            .openRunCapabilityDropDown()
                            .checkCustomCapability(CUSTOM_CAPABILITY_1, false)
                            .checkCustomCapability(CUSTOM_CAPABILITY_3, false)
                            .checkCustomCapability(CUSTOM_CAPABILITY_2, true)
                            .checkCapabilityTooltip(CUSTOM_CAPABILITY_2, TOOLTIP_1)
                            .click(SAVE)
                            .waitUntilSaveEnding(DEFAULT_CONFIGURATION);
                });
    }

    @Test(dependsOnMethods = {"customCapabilitiesForToolDockerImageOS"})
    @TestCase(value = {"2323_3"})
    public void customCapabilitiesForAllToolDockerImageOS() {
        tools().perform(registry, anotherGroup, centosTool, tool ->
                tool
                        .versions()
                        .viewUnscannedVersions()
                        .scanVersionIfNeeded("latest")
        );
        Stream.of(
                Pair.of(group, tool),
                Pair.of(anotherGroup, centosTool)
        ).forEach(t -> {
            tools()
                    .perform(registry, t.first(), t.second(), tool ->
                            tool.settings()
                                    .click(RUN_CAPABILITIES)
                                    .sleep(2, SECONDS)
                                    .checkCustomCapability(CUSTOM_CAPABILITY_4, false)
                                    .click(byTitle(RUN_CAPABILITIES_TITLE))
                                    .selectRunCapability(CUSTOM_CAPABILITY_4));
            new PipelineRunFormAO().checkTooltipText(CUSTOM_CAPABILITY_4, CUSTOM_CAPABILITY_DESC_4);
        });
    }
}

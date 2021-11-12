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

import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Set;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.RUN_CAPABILITIES;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;

public class ToolsParametersTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String invalidEndpoint = "8700";
    private final String launchCapabilities = "launch.capabilities";
    private static final String CUSTOM_CAPABILITIES_1_JSON = "/customCapabilities1.json";
    private static final String CUSTOM_CAPABILITIES_2_JSON = "/customCapabilities2.json";
    private String prefInitialValue = "";
    private final String custCapability1 = "testCapability1";
    private final String custCapability2 = "testCapability2";

    @BeforeClass
    public void getPreferencesValue() {
        open(C.ROOT_ADDRESS);
        fallbackToToolDefaultState(registry, group, tool);
        prefInitialValue = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(launchCapabilities);
    }

    @AfterClass(alwaysRun = true)
    public void fallBackToDefaultToolSettings() {
        open(C.ROOT_ADDRESS);
        fallbackToToolDefaultState(registry, group, tool);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .clearAndSetJsonToPreference(launchCapabilities, prefInitialValue, true)
                .saveIfNeeded();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-502"})
    public void runToolThatHaveNoNginxEndpoint() {
        logoutIfNeeded();
        loginAs(admin);
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

    @Test
    @TestCase(value = {"2234"})
    public void customCapabilitiesImplementation() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .clearAndSetJsonToPreference(launchCapabilities,
                        readResourceFully(CUSTOM_CAPABILITIES_1_JSON), true)
                .saveIfNeeded();
        logout();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .selectValue(RUN_CAPABILITIES, custCapability1)
                .selectValue(RUN_CAPABILITIES, custCapability2)
                .checkTooltipText(custCapability1, "Custom test capability 1")
                .checkTooltipText(custCapability2, "Custom test capability 2")
                .launch(this);
        final Set<String> logMess = runsMenu()
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(format("CP_CAP_CUSTOM_%s", custCapability1), "true"), exist)
                .ensure(configurationParameter(format("CP_CAP_CUSTOM_%s", custCapability2), "true"), exist)
                .waitForSshLink()
                .click(taskWithName("Console"))
                .logMessages()
                .collect(toSet());
        runsMenu()
                .showLog(getLastRunId())
                .logContainsMessage(logMess, format("Running '%s' commands:", custCapability1))
                .logContainsMessage(logMess, "Command: 'echo testLine1'")
                .logContainsMessage(logMess, format("Running '%s' commands:", custCapability2))
                .logContainsMessage(logMess, "Command: 'echo testLine2'")
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute("ls")
                        .assertOutputContains("testFile1.txt")
                        .execute("cat testFile1.txt")
                        .assertOutputContains("testLine1", "testLine2")
                        .close());
    }

    @Test
    @TestCase(value = {"2295"})
    public void customCapabilitiesWithConfiguredJobParameters() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .clearAndSetJsonToPreference(launchCapabilities,
                        readResourceFully(CUSTOM_CAPABILITIES_2_JSON), true)
                .saveIfNeeded();
        logout();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .selectValue(RUN_CAPABILITIES, custCapability1)
                .click(RUN_CAPABILITIES)
                .selectValue(RUN_CAPABILITIES, custCapability2)
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(format("CP_CAP_CUSTOM_%s", custCapability1), "true"), exist)
                .ensure(configurationParameter(format("CP_CAP_CUSTOM_%s", custCapability2), "true"), exist)
                .ensure(configurationParameter("MY_PARAM1", "MY_VALUE1"), exist)
                .ensure(configurationParameter("MY_PARAM2", "MY_VALUE2"), exist)
                .ensure(configurationParameter("MY_BOOLEAN_PARAM", "false"), exist)
                .ensure(configurationParameter("MY_NUMBER_PARAM", "2"), exist);
    }
}

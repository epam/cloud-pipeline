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
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.RUN_CAPABILITIES;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ToolsParametersTest
        extends AbstractSinglePipelineRunningTest
        implements Tools {

    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String invalidEndpoint = "8700";
    private final String launchCapabilities = "launch.capabilities";
    private final String customCapabilities = "{\n\"testCapability1\": {\n \"description\": \"Custom test capability 1\",\n" +
            "  \"commands\": [\n \"echo cap1\",\n \"echo 'cap1' > ~/testFile1.txt\"\n ]\n },\n \"testCapability2\": {\n" +
            "  \"description\": \"Custom test capability 2\",\n \"commands\": [\n \"echo cap2\",\n" +
            "   \"echo 'cap2' >> ~/testFile1.txt\"\n ]\n }\n}";
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
        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .sleep(1, SECONDS)
                                .also(removeAllEndpoints())
                                .addEndpoint(invalidEndpoint)
                                .save()
                                .run(this)
                )
                .showLog(getRunId());
        new RunsMenuAO()
                .waitForInitializeNode(getRunId())
                .clickEndpoint()
                .screenshot("test501screenshot")
                .assertPageTitleIs("502 Bad Gateway");
    }

    @Test
    @TestCase(value = {"2234"})
    public void customCapabilitiesImplementation() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .clearAndSetJsonToPreference(launchCapabilities, customCapabilities, true)
                .saveIfNeeded();
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .selectValue(RUN_CAPABILITIES, custCapability1)
                .click(RUN_CAPABILITIES)
                .selectValue(RUN_CAPABILITIES, custCapability2)
                .launch(this)
                .showLog(getRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(format("CP_CAP_CUSTOM_%s", custCapability1), "true"), exist)
                .ensure(configurationParameter(format("CP_CAP_CUSTOM_%s", custCapability2), "true"), exist)
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute("ls")
                        .assertOutputContains("testFile1.txt")
                        .execute("cat testFile1.txt")
                        .assertOutputContains("cap1", "cap2")
                        .close());
    }
}

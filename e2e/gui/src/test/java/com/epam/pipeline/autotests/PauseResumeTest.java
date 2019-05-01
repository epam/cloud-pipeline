/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.ToolPageAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.function.Supplier;

import static com.codeborne.selenide.Condition.have;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.getParameterValueLink;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.parameterWithName;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.NodePage.labelWithType;
import static com.epam.pipeline.autotests.ao.NodePage.mainInfo;
import static com.epam.pipeline.autotests.ao.Primitive.ENDPOINT;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PAUSE;
import static com.epam.pipeline.autotests.ao.Primitive.SSH_LINK;
import static com.epam.pipeline.autotests.ao.Primitive.START_IDLE;
import static com.epam.pipeline.autotests.utils.Conditions.textMatches;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class PauseResumeTest extends AbstractSeveralPipelineRunningTest implements Tools, Authorization {

    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String testFileName = "test.txt";
    private final String testFileContent = "test";
    private final String ipField = "IP";
    private final String instanceType = C.DEFAULT_INSTANCE;
    private final String priceType = "On-demand";
    private final String pauseTask = "PausePipelineRun";

    private String endpoint;
    private String defaultClusterHddExtraMulti;

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void fallBackToDefaultToolSettings() {
        open(C.ROOT_ADDRESS);
        fallbackToToolDefaultState(registry, group, tool);
    }

    @BeforeClass(alwaysRun = true)
    public void getDefaultPreferences() {
        loginAsAdminAndPerform(() -> {
            // EPMCMBIBPC-2627 && EPMCMBIBPC-2636
            defaultClusterHddExtraMulti =
                    navigationMenu()
                            .settings()
                            .switchToPreferences()
                            .switchToCluster()
                            .getClusterHddExtraMulti();
            new SettingsPageAO(new PipelinesLibraryAO()).click(OK);
        });
    }

    @AfterClass(alwaysRun = true)
    public void fallBackPreferences() {
        loginAsAdminAndPerform(() ->
                // EPMCMBIBPC-2627
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToCluster()
                        .setClusterHddExtraMulti(defaultClusterHddExtraMulti)
                        .save()
                        .click(OK)
        );
    }

    @Test
    @TestCase({"EPMCMBIBPC-2309"})
    public void pauseAndResumeValidation() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(priceType)
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell.execute(
                                                String.format("echo '%s' > %s", testFileContent, testFileName)))
                                )
                                .waitForPauseButton()
                                .pause(getToolName())
                                .assertPausingFinishedSuccessfully()
                                .instanceParameters(parameters -> {
                                    final String nodeIp = $(parameterWithName(ipField)).text().split(" \\(")[0];
                                    final String ipHyperlink = getParameterValueLink(ipField);
                                    parameters.inAnotherTab(nodeTab ->
                                            checkNodePage(() -> nodeTab.messageShouldAppear(
                                                    String.format("The node '%s' was not found or was removed",  nodeIp)
                                            ), ipHyperlink)
                                    );
                                })
                                .resume(getToolName())
                                .assertResumingFinishedSuccessfully()
                                .waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(String.format("pipeline-%s", getLastRunId()))
                                                .execute(String.format("cat %s", testFileName))
                                                .assertOutputContains(testFileContent))
                                )
                                .instanceParameters(parameters -> {
                                    final String nodeIp = $(parameterWithName(ipField)).text().split(" \\(")[0];
                                    final String expectedTitle = String.format("^Node: %s.*", nodeIp);
                                    final String ipHyperlink = getParameterValueLink(ipField);
                                    parameters.inAnotherTab(nodeTab ->
                                            checkNodePage(() ->
                                                    nodeTab
                                                        .ensure(mainInfo(), have(textMatches(expectedTitle)))
                                                        .ensure(labelWithType("RUNID"), visible)
                                                        .ensure(labelWithType("PIPELINE-INFO"), visible), ipHyperlink)
                                    );
                                })
                );
    }

    @Test
    @TestCase({"EPMCMBIBPC-2627"})
    public void forbiddenPauseValidation() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToCluster()
                        .setClusterHddExtraMulti("1")
                        .save()
                        .click(OK));

        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setLaunchOptions("15", instanceType, null)
                .setPriceType(priceType)
                .click(START_IDLE)
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .execute("fallocate -l 25G test.big")
                                                .sleep(30, SECONDS))
                                )
                                .waitForPauseButton()
                                .clickOnPauseButton()
                                .validateException("This operation may fail due to 'Out of disk' error")
                                .click(button(PAUSE.name()))
                                .assertPausingStatus()
                                .ensure(taskWithName(pauseTask), visible)
                                .click(taskWithName(pauseTask))
                                .ensure(log(), matchText("\\[WARN] Free disk space 0Kb is not enough for committing.*"))
                                .waitForPauseButton()
                                .shouldHaveStatus(LogAO.Status.WORKING)
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .execute("rm test.big && fallocate -l 10G test2.big")
                                                .sleep(30, SECONDS))
                                )
                                .waitForPauseButton()
                                .clickOnPauseButton()
                                .click(button(PAUSE.name()))
                                .assertPausingStatus()
                                .ensure(taskWithName(pauseTask), visible)
                                .click(taskWithName(pauseTask))
                                .ensure(log(), matchText("\\[WARN] Free disk space [0-9\\.]+Kb is not enough for committing.*"))
                                .waitForPauseButton()
                                .shouldHaveStatus(LogAO.Status.WORKING)
                );
    }

    @Test
    @TestCase({"EPMCMBIBPC-2632"})
    public void pauseAndResumeRunsPageValidation() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(priceType)
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .activeRuns()
                .waitUntilPauseButtonAppear(getLastRunId())
                .pause(getLastRunId(), getToolName())
                .waitUntilResumeButtonAppear(getLastRunId())
                .showLog(getLastRunId())
                .ensure(ENDPOINT, hidden)
                .ensure(SSH_LINK, hidden);
        runsMenu()
                .resume(getLastRunId(), getToolName())
                .waitUntilPauseButtonAppear(getLastRunId())
                .showLog(getLastRunId())
                .ensure(ENDPOINT, visible)
                .ensure(SSH_LINK, visible);
    }

    @Test(priority = 99)
    @TestCase({"EPMCMBIBPC-2636"})
    public void hddExtraMultiValidation() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToCluster()
                        .setClusterHddExtraMulti("100")
                        .save()
                        .click(OK));

        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setLaunchOptions("15", instanceType, null)
                .setPriceType(priceType)
                .click(START_IDLE)
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(String.format("pipeline-%s", getLastRunId()))
                                                .execute("fallocate -l 15G test.big")
                                                .sleep(10, SECONDS))
                                )
                                .sleep(30, SECONDS)
                                .waitForPauseButton()
                                .pause(getToolName())
                                .assertPausingFinishedSuccessfully()
                                .ensure(taskWithName(pauseTask), visible)
                                .click(taskWithName(pauseTask))
                                .waitForLog("Docker service was successfully stopped")
                                .ensure(log(), matchText("Temporary container was successfully committed"))
                                .ensure(log(), matchText("Docker container logs were successfully retrieved."))
                                .ensure(log(), matchText("Docker service was successfully stopped"))
                                .resume(getToolName())
                                .assertResumingFinishedSuccessfully()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .execute("ls test.big")
                                                .assertOutputContains("test.big")
                                                .close())
                                )
                );
    }

    @Test(priority = 100)
    @TestCase({"EPMCMBIBPC-2626"})
    public void pauseAndResumeEndpointValidation() {
        endpoint = tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(priceType)
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .show(getLastRunId())
                .clickEndpoint()
                .getEndpoint();
        Utils.restartBrowser(C.ROOT_ADDRESS);
        loginAs(admin);

        runsMenu()
                .log(getLastRunId(), log -> log
                        .waitForPauseButton()
                        .pause(getToolName())
                        .assertPausingFinishedSuccessfully()
                        .sleep(2, MINUTES)
                        .inAnotherTab(nodeTab ->
                                checkNodePage(() ->
                                                new ToolPageAO(endpoint)
                                                        .assertPageTitleIs("404 Not Found"),
                                        endpoint)
                        )
                        .resume(getToolName())
                        .waitForEndpointLink()
                        .sleep(C.ENDPOINT_INITIALIZATION_TIMEOUT, MILLISECONDS)
                        .inAnotherTab(nodeTab ->
                                checkNodePage(() -> new ToolPageAO(endpoint).validateEndpointPage(), endpoint)
                        )
                );
    }

    private void checkNodePage(final Supplier<?> nodePage, final String ipHyperlink) {
        open(ipHyperlink);
        nodePage.get();
    }

    private String getToolName() {
        final String[] toolAndGroup = tool.split("/");
        return toolAndGroup[toolAndGroup.length - 1];
    }
}

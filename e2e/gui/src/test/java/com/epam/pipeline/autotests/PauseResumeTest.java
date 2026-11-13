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

import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.ToolPageAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.function.Supplier;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.have;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Utils.refresh;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.getParameterValueLink;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.parameterWithName;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.runId;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.NodePage.labelWithType;
import static com.epam.pipeline.autotests.ao.NodePage.mainInfo;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.ENDPOINT;
import static com.epam.pipeline.autotests.ao.Primitive.SSH_LINK;
import static com.epam.pipeline.autotests.ao.Primitive.START_IDLE;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static com.epam.pipeline.autotests.utils.Conditions.textMatches;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.tabWithName;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
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
    private final String priceType = ON_DEMAND;
    private final String pauseTask = "PausePipelineRun";

    private String endpoint;
    private String defaultClusterDockerExtraMulti;

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
            defaultClusterDockerExtraMulti =
                    navigationMenu()
                            .settings()
                            .switchToPreferences()
                            .switchToCluster()
                            .getDockerExtraMulti();
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
                        .setDockerExtraMulti(defaultClusterDockerExtraMulti)
                        .saveIfNeeded());
    }

    @Test
    @TestCase({"EPMCMBIBPC-2309"})
    public void pauseAndResumeValidation() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(priceType)
                .doNotMountStoragesSelect(true)
                .launchTool(this, nameWithoutGroup(tool))
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                                .sshInNewTab(shell -> shell
                                        .waitUntilTextAppears(getLastRunId())
                                        .execute(String.format("echo '%s' > %s", testFileContent, testFileName)))
                                .waitForPauseButton()
                                .pause(nameWithoutGroup(tool))
                                .assertPausingFinishedSuccessfully()
                                .instanceParameters(parameters -> {
                                    final String nodeName = $(parameterWithName(ipField)).text().split(" \\(")[0];
                                    final String expectedTitle = String.format("^Node: %s.*", nodeName);
                                    final String ipHyperlink = getParameterValueLink(ipField);
                                    // the tabs are not rendered while the node is loading either,
                                    // so their absence is checked once the page has loaded
                                    parameters.inAnotherTab(nodeTab ->
                                            checkNodePage(() -> nodeTab
                                                            .ensure(byId("refresh-cluster-node-button"), enabled)
                                                            .ensure(mainInfo(), have(textMatches(expectedTitle)))
                                                            .ensure(tabWithName("General info"), not(visible))
                                                            .ensure(tabWithName("Jobs"), not(visible))
                                                    , ipHyperlink)
                                    );
                                })
                                .resume(nameWithoutGroup(tool))
                                .assertResumingFinishedSuccessfully()
                                .waitForSshLink()
                                .sshInNewTab(shell -> shell
                                        .waitUntilTextAppears(getLastRunId())
                                        .executeAndWaitForPrompt(String.format("cat %s", testFileName),
                                                String.format("pipeline-%s", getLastRunId()))
                                        // the typed command contains the file content too, so only the text
                                        // after the command is checked
                                        .assertOutputOfLastCommandMatches(String.format("cat %s", testFileName),
                                                String.format("^%s", testFileContent)))
                                .instanceParameters(parameters -> {
                                    final String nodeIp = $(parameterWithName(ipField)).text().split(" \\(")[0];
                                    final String expectedTitle = String.format("^Node: %s.*", nodeIp);
                                    final String ipHyperlink = getParameterValueLink(ipField);
                                    parameters.inAnotherTab(nodeTab ->
                                            checkNodePage(() ->
                                                    nodeTab
                                                        .ensure(mainInfo(), have(textMatches(expectedTitle)))
                                                        .ensure(labelWithType("RUNID"), visible)
                                                        .ensure(labelWithType("pipeline-info"), visible), ipHyperlink)
                                    );
                                })
                );
    }

    @Test(enabled = false)
    @TestCase({"EPMCMBIBPC-2627"})
    public void dockerExtraMultiValidation() {
        if (impersonateMode() && "true".equalsIgnoreCase(C.AUTH_TOKEN)) {
            return;
        }
        try {
            tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .setLaunchOptions("15", instanceType, null)
                    .setPriceType(priceType)
                    .click(START_IDLE)
                    .launchTool(this, nameWithoutGroup(tool));
            loginAsAdminAndPerform(() ->
                    navigationMenu()
                            .settings()
                            .switchToPreferences()
                            .switchToCluster()
                            .setDockerExtraMulti("1000")
                            .saveIfNeeded());
            runsMenu()
                    .log(getLastRunId(), log ->
                            log
                                    .waitForPauseButton()
                                    .clickOnPauseButton()
                                    .validateException("This operation may fail due to 'Out of disk' error")
                                    .click(button(CANCEL.name()))
                                    .shouldHaveStatus(LogAO.Status.WORKING)
                    );
        } finally {
            loginAsAdminAndPerform(() ->
                    navigationMenu()
                            .settings()
                            .switchToPreferences()
                            .switchToCluster()
                            .setDockerExtraMulti(defaultClusterDockerExtraMulti)
                            .saveIfNeeded());
        }
    }

    @Test
    @TestCase({"EPMCMBIBPC-2632"})
    public void pauseAndResumeRunsPageValidation() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(priceType)
                .doNotMountStoragesSelect(true)
                .launchTool(this, nameWithoutGroup(tool))
                .activeRuns()
                .waitUntilPauseButtonAppear(getLastRunId())
                .pause(getLastRunId(), nameWithoutGroup(tool))
                .pausingLabelShouldAppear(getLastRunId())
                .waitUntilResumeButtonAppear(getLastRunId())
                .showLog(getLastRunId())
                .ensure(runId(), matchText(String.format("#%s\\b", getLastRunId())))
                .ensure(STATUS, LogAO.Status.PAUSED.reached)
                .waitForResumeButton()
                // the SSH link is requested separately after the page loads, so wait before checking its absence
                .sleep(10, SECONDS)
                .ensure(byXpath(".//th[starts-with(normalize-space(.), 'Endpoint')]"), not(exist))
                .ensure(ENDPOINT, hidden)
                .ensure(SSH_LINK, hidden);
        runsMenu()
                .resume(getLastRunId(), nameWithoutGroup(tool))
                .resumingLabelShouldAppear(getLastRunId())
                .waitUntilPauseButtonAppear(getLastRunId())
                .showLog(getLastRunId())
                // the endpoint is back when cp-edge recreates its route: its sync runs once a minute, after the pod
                // is running again, so the link appears up to about a minute after the run is RUNNING
                .waitForEndpointLink()
                .ensure(ENDPOINT, visible)
                .ensure(SSH_LINK, visible);
    }

    @Test(priority = 99, enabled = false)
    @TestCase({"EPMCMBIBPC-2636"})
    public void hddExtraMultiValidation() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToCluster()
                        .setInstanceHddExtraMulti("10")
                        .saveIfNeeded());

        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setLaunchOptions("15", instanceType, null)
                .setPriceType(priceType)
                .click(START_IDLE)
                .launchTool(this, nameWithoutGroup(tool))
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(getLastRunId())
                                                .execute("fallocate -l 15G test.big")
                                                .sleep(10, SECONDS))
                                )
                                .sleep(30, SECONDS)
                                .waitForPauseButton()
                                .pause(nameWithoutGroup(tool))
                                .assertPausingFinishedSuccessfully()
                                .ensure(taskWithName(pauseTask), visible)
                                .click(taskWithName(pauseTask))
                                .waitForLog("Docker service was successfully stopped")
                                .ensure(log(), matchText("Temporary container was successfully committed"))
                                .ensure(log(), matchText("Docker container logs were successfully retrieved."))
                                .ensure(log(), matchText("Docker service was successfully stopped"))
                                .resume(nameWithoutGroup(tool))
                                .assertResumingFinishedSuccessfully()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(getLastRunId())
                                                .execute("ls test.big")
                                                .assertOutputContains("test.big")
                                                .close())
                                )
                );
    }

    @Test(priority = 100)
    @TestCase({"EPMCMBIBPC-2626"})
    public void pauseAndResumeEndpointValidation() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(priceType)
                .doNotMountStoragesSelect(true)
                .launchTool(this, nameWithoutGroup(tool))
                .log(getLastRunId(), log -> {
                    // the application of the tool starts some time after its endpoint link appears
                    log.waitForEndpointLink()
                            .sleep(3, MINUTES);
                    endpoint = clickEndpointAndValidateApplication(log);
                    log.waitForPauseButton()
                            .pause(nameWithoutGroup(tool))
                            .assertPausingFinishedSuccessfully()
                            .sleep(2, MINUTES)
                            .inAnotherTab(nodeTab ->
                                    checkNodePage(() ->
                                                    new ToolPageAO(endpoint)
                                                            .assertPageTitleIs("404 Not Found"),
                                            endpoint)
                            )
                            .resume(nameWithoutGroup(tool))
                            .assertResumingFinishedSuccessfully()
                            .waitForEndpointLink()
                            .sleep(3, MINUTES);
                    clickEndpointAndValidateApplication(log);
                });
    }

    /**
     * Clicks the endpoint link of the run page, checks the application in the opened tab, closes that tab
     * and returns the address of the endpoint.
     */
    private String clickEndpointAndValidateApplication(final LogAO log) {
        final ToolPageAO application = log.clickOnEndpointLink();
        try {
            application
                    .sleep(10, SECONDS)
                    .validateEndpointPage(C.LOGIN);
            return application.getEndpoint();
        } finally {
            application.closeTab();
        }
    }

    private void checkNodePage(final Supplier<?> nodePage, final String ipHyperlink) {
        open(ipHyperlink);
        sleep(5, SECONDS);
        refresh();
        nodePage.get();
    }
}

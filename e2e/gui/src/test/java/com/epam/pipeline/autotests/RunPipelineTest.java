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

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.ElementsCollection;
import com.epam.pipeline.autotests.ao.ClusterMenuAO;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.NodePage;
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static com.codeborne.selenide.CollectionCondition.sizeGreaterThan;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.ClusterMenuAO.*;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.parameterWithName;
import static com.epam.pipeline.autotests.ao.LogAO.*;
import static com.epam.pipeline.autotests.ao.LogAO.Status.SUCCESS;
import static com.epam.pipeline.autotests.ao.NodePage.*;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.ESTIMATED_PRICE;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.LAUNCH_COMMANDS;
import static com.epam.pipeline.autotests.ao.Primitive.PIPELINE;
import static com.epam.pipeline.autotests.ao.Primitive.RUN;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.Conditions.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.*;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static com.epam.pipeline.autotests.utils.Utils.refresh;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.openqa.selenium.By.className;
import static org.testng.Assert.assertTrue;

public class RunPipelineTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    private static final String PYTHON_CMD_TEMPLATE = "$CP_PYTHON_PATH $SCRIPTS_DIR/src/[main_file]";
    private String pipeline100 = resourceName("epmcmbibpc-100");
    private final String pipeline298 = resourceName("epmcmbibpc-298");
    private final String pipeline303 = resourceName("epmcmbibpc-303");
    private final String pipeline314 = resourceName("epmcmbibpc-314");
    private final String pipeline306 = resourceName("epmcmbibpc-306");
    private final String pipeline312 = resourceName("epmcmbibpc-312");

    @AfterClass(alwaysRun = true)
    public void removePipelines() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
            .library()
            .removePipelineIfExists(pipeline100)
            .removePipelineIfExists(pipeline298)
            .removePipelineIfExists(pipeline303)
            .removePipelineIfExists(pipeline314)
            .removePipelineIfExists(pipeline306)
            .removePipelineIfExists(pipeline312);
    }

    @Test
    @TestCase("EPMCMBIBPC-298")
    public void shouldCreatePipelineFromPythonTemplate() {
        library()
            .createPipeline(Template.PYTHON, pipeline298)
            .validatePipelineInTreeAndList(pipeline298);
    }

    @Test(dependsOnMethods = "shouldCreatePipelineFromPythonTemplate")
    @TestCase("EPMCMBIBPC-299")
    public void pipelineRunFormShouldBeValid() {
        library()
            .clickOnPipeline(pipeline298)
            .firstVersion()
            .click(RUN);
        // the form is opened without expanding its panels, so the default state of the panels is checked
        new PipelineRunFormAO()
            .ensure(LAUNCH_COMMANDS, appears)
            .ensure(PIPELINE, visible, exactText(pipeline298))
            .ensure(ESTIMATED_PRICE, visible)
            .ensure(button("Launch"), visible, enabled)
            .ensure(collapsiblePanel("Exec environment"), visible, collapsedTab)
            .ensure(collapsiblePanel("Advanced"), visible, collapsedTab)
            .ensure(collapsiblePanel("Parameters"), visible, expandedTab)
            .ensure(button("Add parameter"), visible, enabled);
    }

    @Test
    @TestCase("EPMCMBIBPC-306")
    public void cmdTemplateShouldBeCorrect() {
        library()
            .createPipeline(Template.PYTHON, pipeline306)
            .clickOnPipeline(pipeline306)
            .firstVersion()
            .configurationTab()
            .editConfiguration("default", profile ->
                profile.expandTab(collapsiblePanel("Advanced"))
                       .ensure(inputOf(fieldWithLabel("Cmd template")),
                               have(attribute("value", PYTHON_CMD_TEMPLATE)))
            );
    }

    /**
     * {@link Test#priority()} is set to {@code 1} to prevent mixing of independent tests defined above
     * to the continuous chain of tests that starts with this test method.
     */
    @Test(priority = 1)
    @TestCase("EPMCMBIBPC-100")
    public void shouldLaunchPipeline() {
        final RunsMenuAO runs = library()
            .createPipeline(Template.PYTHON, pipeline100)
            .runPipelineFromTable(pipeline100)
            .expandTab(EXEC_ENVIRONMENT)
            .expandTab(ADVANCED_PANEL)
            .setLaunchOptions("20", C.DEFAULT_INSTANCE, "")
            .setPriceType(ON_DEMAND)
            .launch(this);
        final String runId = getLastRunId();
        runs
            .ensure(tabWithName("Active Runs"), visible, selectedTab)
            .runShouldBeWaiting(runId)
            .runCellShouldHave(runId, RunsMenuAO.HeaderColumn.RUN, text(String.format("%s-%s", pipeline100, runId)))
            .runCellShouldHave(runId, RunsMenuAO.HeaderColumn.PIPELINE, text(pipeline100), matchText("draft-.{8}"))
            .runCellShouldHave(runId, RunsMenuAO.HeaderColumn.STARTED,
                    matchText("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test(priority = 1, dependsOnMethods = "shouldLaunchPipeline")
    @TestCase("EPMCMBIBPC-304")
    public void shouldContainCorrectNode() {
        clusterMenu().waitForTheNode(pipeline100, getLastRunId());
    }

    @Test(priority = 1, dependsOnMethods = "shouldContainCorrectNode")
    @TestCase("EPMCMBIBPC-302")
    public void activePipelineShouldBeOpenedFromNodeInfo() {
        final String runIdLabel = String.format("RUN ID %s", getLastRunId());
        clusterMenu()
            .click(nodeLabel(runIdLabel), LogAO::new)
            .ensure(taskList(), visible)
            .ensure(runId(), have(matchText(String.format("#%s\\b", getLastRunId()))))
            .ensure(pipelineLink(), have(textMatches(String.format("%s \\(draft-.{8}\\)", pipeline100))));
    }

    @Test(priority = 1, dependsOnMethods = "activePipelineShouldBeOpenedFromNodeInfo")
    @TestCase("EPMCMBIBPC-301")
    public void nodePageOfPipelineRunShouldBeValid() {
        final String runId = getLastRunId();
        final ClusterMenuAO cluster = clusterMenu();
        final String nodeName = cluster.getNodeName(runId);
        // the labels cell holds the RUN ID link to the run, so the node page is opened from the name cell
        cluster
            .click(nodeNameCellOfRun(runId), NodePage::new)
            .ensure(buttonByIconClass("anticon-arrow-left"), visible, enabled)
            .ensure(button("Refresh"), visible, enabled)
            .ensure(mainInfo(), have(text(String.format("Node: %s", nodeName))))
            .ensure(labelWithType("RUNID"), have(exactText(String.format("RUN ID %s", runId))))
            .ensure(labelWithType("pipeline-info"), have(text(pipeline100)), have(text("(draft-")))
            .ensure(tabWithName("General info"), visible)
            .ensure(tabWithName("Jobs"), visible)
            .ensure(tabWithName("Monitor"), visible);
    }

    @Test(priority = 1, dependsOnMethods = "nodePageOfPipelineRunShouldBeValid")
    @TestCase("EPMCMBIBPC-281")
    public void activePipelineShouldBeOpenedFromNodePage() {
        new NodePage()
            .click(labelWithType("RUNID"), LogAO::new)
            .ensure(taskList(), visible)
            .ensure(runId(), have(matchText(String.format("#%s\\b", getLastRunId()))));
    }

    @Test(priority = 1, dependsOnMethods = "activePipelineShouldBeOpenedFromNodePage")
    @TestCase("EPMCMBIBPC-305")
    public void pipelineLogPageShouldBeValid() {
        sleep(1, SECONDS);
        refresh();
        final String runId = getLastRunId();
        runsMenu()
            .activeRuns()
            .showLog(runId)
            .ensure(runId(), have(text(String.format("#%s", runId))))
            .ensure(pipelineLink(), have(textMatches(String.format("%s \\(draft-.{8}\\)", pipeline100))))
            .ensure(detailsWithLabel("Owner"), have(text(getUserNameByAccountLogin(C.LOGIN))))
            .waitForCompletion()
            .ensure(taskWithName("Task1"), Status.SUCCESS.reached);
        runsMenu()
                .completedRuns()
                .showLog(runId)
                .ensure(taskWithName(pipeline100), Status.SUCCESS.reached);
    }

    @Test(priority = 1, dependsOnMethods = "pipelineLogPageShouldBeValid")
    @TestCase("EPMCMBIBPC-307")
    public void instanceTabShouldBeValid() {
        onRunPage()
            .instanceParameters(
                p -> p.ensure(parameterWithName("Node type"), have(text(C.DEFAULT_INSTANCE)))
                      .ensure(parameterWithName("Disk"), have(text("20 Gb")))
                      .ensure(parameterWithName("Cmd template"), have(text(PYTHON_CMD_TEMPLATE)))
            );
    }

    @Test(priority = 1, dependsOnMethods = "instanceTabShouldBeValid")
    @TestCase("EPMCMBIBPC-309")
    public void logShouldBeValid() {
        final String mountStorageTaskName = "MountDataStorages";
        final String[] texts = prepareExpectedLogMessages(pipeline100, getLastRunId(),
                "expectedRunLogPython.txt").toArray(String[]::new);
        final String[] mountDataTexts = prepareExpectedLogMessages(mountStorageTaskName, getLastRunId(),
                "expectedRunLogMountDataStoragesTask.txt").toArray(String[]::new);
        Set<String> pipelineLog = onRunPage()
                .click(taskWithName("Task1"))
                .ensure(log(), containsMessage("Running python pipeline"))
                .click(taskWithName(pipeline100))
                .logMessages()
                .collect(toSet());
        Arrays.stream(texts)
                .map(String::trim)
                .filter(RunPipelineTest::isMeaningfulLogLine)
                .forEach(t -> onRunPage().logContainsMessage(pipelineLog, t));
        Set<String> mountMessages = onRunPage()
                .click(taskWithName(mountStorageTaskName))
                .ensure(STATUS, SUCCESS.reached)
                .logMessages()
                .collect(toSet());
        Arrays.stream(mountDataTexts)
                .map(String::trim)
                .filter(RunPipelineTest::isMeaningfulLogLine)
                .forEach(t -> onRunPage().logContainsMessage(mountMessages, t));
    }

    @Test(priority = 1, dependsOnMethods = "logShouldBeValid")
    @TestCase("EPMCMBIBPC-310")
    public void timingsShouldBeValid() {
        onRunPage()
            .showTimings()
            .ensureAll(task(), contains(timeInfo("Scheduled"), timeInfo("Started"), timeInfo("Finished")));
    }

    @Test(priority = 1, dependsOnMethods = "timingsShouldBeValid")
    @TestCase("EPMCMBIBPC-311")
    public void shouldNavigateToTheNode() {
        final String nodeIp = $(parameterWithName("IP")).text().split(" \\(")[0];
        final String expectedTitle = String.format("^Node: %s.*", nodeIp);
        onRunPage()
            .click(parameterWithName("IP"), NodePage::new)
            .ensure(mainInfo(), have(textMatches(expectedTitle)))
            .ensure(labelWithType("RUNID"), visible)
            .ensure(labelWithType("pipeline-info"), visible);
    }

    /**
     * {@link Test#priority()} is set to {@code 2} to prevent mixing of independent tests defined above
     * to the continuous chain of tests that starts with this test method.
     */
    @Test(priority = 2)
    @TestCase("EPMCMBIBPC-312")
    public void runShouldNotAppearInActiveRuns() {
        library()
            .createPipeline(Template.SHELL, pipeline312)
            .clickOnPipeline(pipeline312)
            .firstVersion()
            .runPipeline()
	    .setPriceType(ON_DEMAND)
            .launch(this)
            .showLog(getLastRunId())
            .ensure(pipelineLink(), have(text(pipeline312)))
            .waitForCompletion()
            .ensure(STATUS, SUCCESS.reached);

        runsMenu()
            .activeRuns()
            .ensure(runWithId(getLastRunId()), not(exist))
            .completedRuns()
            .ensure(runWithId(getLastRunId()), visible)
            .validateStatus(getLastRunId(), SUCCESS);
    }

    @Test(priority = 2, dependsOnMethods = "runShouldNotAppearInActiveRuns")
    @TestCase({"EPMCMBIBPC-300", "EPMCMBIBPC-267"})
    public void clusterNodePageShouldBeValid() {
        clusterMenu()
            .ensure(className("cluster__node-main-info"), visible, have(text("Cluster nodes")),
                    have(matchText("Cluster nodes \\(\\d+ nodes?(, \\d+ nodes? with associated RunId)?\\)")))
            .ensure(button("Refresh"), visible, enabled);
        $$(node()).shouldHave(sizeGreaterThan(0));
        int nodesWithTerminateButton = checkNodes();
        while ("false".equals($(className("ant-pagination-next")).getAttribute("aria-disabled"))) {
            $(className("ant-pagination-next")).click();
            nodesWithTerminateButton += checkNodes();
        }
        assertTrue(nodesWithTerminateButton > 0, "There is no node with a TERMINATE button");
    }

    @Test(priority = 2, dependsOnMethods = "runShouldNotAppearInActiveRuns")
    @TestCase("EPMCMBIBPC-280")
    public void nodePageShouldBeValid() {
        final By nonMasterNode = Combiners.select(
                and("node neither master nor windows", not(master()), not(windows())),
                node(), "any non-master node");
        refresh();
        clusterMenu()
            .click(nonMasterNode, NodePage::new)
            .ensure(button("Refresh"), visible, enabled)
            .ensure(buttonByIconClass("anticon-arrow-left"), visible, enabled)
            .ensure(labelWithType("RUNID"), visible)
            .ensure(labelWithType("pipeline-info"), visible)
            .ensure(tabWithName("General info"), visible, selectedTab)
            .ensure(tabWithName("Jobs"), visible)
            .ensure(tabWithName("Monitor"), visible)
            .ensure(section("System info"), visible)
            .ensure(section("Addresses"), visible)
            .ensure(section("Labels"), visible);
    }

    private static Stream<String> prepareExpectedLogMessages(final String pipelineName, final String runId,
                                                             final String fileName) {
        try (final BufferedReader reader = Utils.getResourcesReader("/" + fileName)) {
            return reader.lines()
                         .map(line -> {
                             final String scriptName = pipelineName.replaceAll("-", "").toLowerCase();
                             return line.replaceAll("\\{main_file_name}", pipelineName)
                                        .replaceAll("\\{pipeline_id}", runId)
                                        .replaceAll("\\{main_file_name_without_dash}", scriptName);
                         })
                         .collect(toList())
                         .stream();
        } catch (final IOException cause) {
            throw new RuntimeException(cause);
        }
    }

    private static By nodeNameCellOfRun(final String runId) {
        return By.xpath(String.format(
            ".//*[contains(@class, 'cluster__table')]//tr[contains(@class, 'cluster-row') and " +
                ".//*[normalize-space(text()) = 'RUN ID %s']]/td[contains(@class, 'cluster__cluster-node-row-name')]",
            runId));
    }

    private static LogAO onRunPage() {
        return new LogAO();
    }

    /**
     * Checks the nodes of the current page of the cluster table and returns the number of nodes that are not master
     * nodes, i.e. nodes with a TERMINATE button.
     */
    private int checkNodes() {
        final ElementsCollection nodes = $$(node());
        nodes.forEach(node -> node.should(node.is(master()) ?
                not(contains(button("TERMINATE"))) : contains(button("TERMINATE"))));
        return nodes.filter(not(master())).size();
    }

    /**
     * An empty line or a line of dashes (a section separator) is contained in almost any log line.
     */
    private static boolean isMeaningfulLogLine(final String line) {
        return !line.replace("-", "").trim().isEmpty();
    }
}

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
import com.epam.pipeline.autotests.ao.NodePage;
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
import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.ClusterMenuAO.*;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.parameterWithName;
import static com.epam.pipeline.autotests.ao.LogAO.*;
import static com.epam.pipeline.autotests.ao.NodePage.*;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.Conditions.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.*;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.className;

public class RunPipelineTest extends AbstractSeveralPipelineRunningTest implements Authorization {
    private String pipeline100 = resourceName("epmcmbibpc-100");
    private final String pipeline298 = resourceName("epmcmbibpc-298");
    private final String pipeline299 = resourceName("epmcmbibpc-299");
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
            .removePipelineIfExists(pipeline299)
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
            .ensure(pipelineWithName(pipeline298), visible);
    }

    @Test
    @TestCase("EPMCMBIBPC-299")
    public void pipelineRunFormShouldBeValid() {
        library()
            .createPipeline(Template.SHELL, pipeline299)
            .clickOnPipeline(pipeline299)
            .firstVersion()
            .runPipeline()
            .ensure(byText(pipeline299), visible)
            .ensure(byText("Estimated price per hour:"), visible)
            .ensure(button("Launch"), visible, enabled)
            .ensure(collapsiblePanel("Exec environment"), visible, expandedTab)
            .ensure(collapsiblePanel("Advanced"), visible, expandedTab)
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
            .runPipeline()
            .expandTab(collapsiblePanel("Advanced"))
            .ensure(inputOf(fieldWithLabel("Cmd template")), have(value("python $SCRIPTS_DIR/src/[main_file]")));
    }

    /**
     * {@link Test#priority()} is set to {@code 1} to prevent mixing of independent tests defined above
     * to the continuous chain of tests that starts with this test method.
     */
    @Test(priority = 1)
    @TestCase("EPMCMBIBPC-100")
    public void shouldLaunchPipeline() {
        library()
            .createPipeline(Template.PYTHON, pipeline100)
            .clickOnPipeline(pipeline100)
            .firstVersion()
            .runPipeline()
            .setLaunchOptions("20", C.DEFAULT_INSTANCE, "")
            .launch(this)
            .ensure(tabWithName("Active Runs"), visible, selectedTab)
            .ensure(runWithId(getLastRunId()), visible);
    }

    @Test(priority = 1, dependsOnMethods = "shouldLaunchPipeline")
    @TestCase("EPMCMBIBPC-304")
    public void shouldContainCorrectNode() {
        clusterMenu().waitForTheNode(getLastRunId());
    }

    @Test(priority = 1, dependsOnMethods = "shouldContainCorrectNode")
    @TestCase({"EPMCMBIBPC-281", "EPMCMBIBPC-302"})
    public void activePipelineShouldBeOpenedFromNodeInfo() {
        final String runIdLabel = String.format("RUN ID %s", getLastRunId());
        clusterMenu()
            .click(nodeLabel(runIdLabel), LogAO::new)
            .ensure(taskList(), visible);
    }

    @Test(priority = 1, dependsOnMethods = "activePipelineShouldBeOpenedFromNodeInfo")
    @TestCase("EPMCMBIBPC-305")
    public void pipelineLogPageShouldBeValid() {
        sleep(1, SECONDS);
        refresh();
        final String runId = getLastRunId();
        runsMenu()
            .activeRuns()
            .showLog(runId)
            .ensure(runId(), have(text(String.format("Run #%s", runId))))
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
                      .ensure(parameterWithName("Cmd template"), have(text("python $SCRIPTS_DIR/src/[main_file]")))
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
        onRunPage()
            .click(taskWithName("Task1"))
            .ensure(log(), containsMessage("Running python pipeline"))
            .click(taskWithName(mountStorageTaskName))
            .ensure(log(), containsMessages(mountDataTexts))
            .click(taskWithName(pipeline100))
            .ensure(log(), containsMessages(texts));
    }

    @Test(priority = 1, dependsOnMethods = "logShouldBeValid")
    @TestCase("EPMCMBIBPC-310")
    public void timingsShouldBeValid() {
        onRunPage()
            .click(byText("SHOW TIMINGS"))
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
            .ensure(labelWithType("PIPELINE-INFO"), visible);
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
            .launch(this)
            .showLog(getLastRunId())
            .waitForCompletion();

        runsMenu()
            .activeRuns()
            .ensure(runWithId(getLastRunId()), not(exist))
            .completedRuns()
            .ensure(runWithId(getLastRunId()), exist);
    }

    @Test(priority = 2, dependsOnMethods = "runShouldNotAppearInActiveRuns")
    @TestCase({"EPMCMBIBPC-300", "EPMCMBIBPC-267"})
    public void clusterNodePageShouldBeValid() {
        clusterMenu()
            .ensure(className("cluster__node-main-info"), visible, have(text("Cluster nodes")))
            .ensure(button("Refresh"), visible, enabled)
            .ensureAll(Combiners.select(not(master()), node(), "non-master node"), contains(button("TERMINATE")))
            .ensureAll(Combiners.select(master(), node(), "master node"), not(contains(button("TERMINATE"))));
    }

    @Test(priority = 2, dependsOnMethods = "runShouldNotAppearInActiveRuns")
    @TestCase({"EPMCMBIBPC-280", "EPMCMBIBPC-301"})
    public void nodePageShouldBeValid() {
        final By nonMasterNode = Combiners.select(not(master()), node(), "any non-master node");
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

    private static LogAO onRunPage() {
        return new LogAO();
    }
}

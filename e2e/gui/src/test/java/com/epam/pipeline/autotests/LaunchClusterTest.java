/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LaunchClusterTest extends AbstractAutoRemovingPipelineRunningTest implements Authorization {

    private final String autoScaledSettingForm = "Auto-scaled cluster";

    @AfterMethod(alwaysRun = true)
    @Override
    public void removePipeline() {
        super.removePipeline();
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void removeNode() {
        super.removeNode();
    }

    @BeforeClass
    public void setPreferences() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToAutoscaling()
                        .setScaleDownTimeout("30")
                        .setScaleUpTimeout("30")
                        .save()
        );
    }

    @Test
    @TestCase({"EPMCMBIBPC-975"})
    public void launchPipelineWithLaunchFlag() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm("Cluster")
                .setWorkingNodesCount("2")
                .click(button("OK"))
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(2, getRunId())
                .showLog(getRunId())
                .waitForCompletion();

        navigationMenu()
                .runs()
                .completedRuns()
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(2, getRunId());
    }

    @Test
    @TestCase({"EPMCMBIBPC-2618"})
    public void launchAutoScaledClusterTest() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .setWorkingNodesCount("1")
                .click(button("OK"))
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId());
        $(byClassName("run-" + getRunId()))
                .find(byClassName("ant-table-row-expand-icon"))
                .waitUntil(appears, C.COMPLETION_TIMEOUT);
        navigationMenu()
                .runs()
                .activeRuns()
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(1, getRunId())
                .stopRunIfPresent(String.valueOf(Integer.valueOf(getRunId())+1))
                .stopRunIfPresent(getRunId());
    }

    @Test(priority = 1)
    @TestCase({"EPMCMBIBPC-2620"})
    public void invalidValuesOnConfigureClusterPopUp() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm("Cluster")
                .setWorkingNodesCount("0")
                .messageShouldAppear("Value should be greater than 0")
                .setWorkingNodesCount("-1")
                .messageShouldAppear("Value should be greater than 0")
                .setWorkingNodesCount("asdf")
                .messageShouldAppear("Enter positive number")
                .clusterSettingsForm(autoScaledSettingForm)
                .setWorkingNodesCount("0")
                .messageShouldAppear("Value should be greater than 0")
                .setWorkingNodesCount("-1")
                .messageShouldAppear("Value should be greater than 0")
                .setWorkingNodesCount("asdf")
                .messageShouldAppear("Enter positive number")
                .setDefaultChildNodes("3")
                .messageShouldAppear("Max child nodes count should be greater than child nodes count")
                .resetClusterChildNodes()
                .setDefaultChildNodes("0")
                .messageShouldAppear("Value should be greater than 0")
                .resetClusterChildNodes()
                .setDefaultChildNodes("-1")
                .messageShouldAppear("Value should be greater than 0")
                .resetClusterChildNodes()
                .setDefaultChildNodes("asdf")
                .messageShouldAppear("Enter positive number");
    }

    @Test
    @TestCase({"EPMCMBIBPC-2628"})
    public void autoScaledClusterWithDefaultChildNodesValidationTest() {
        final String gridEngineAutoscalingTask = "GridEngineAutoscaling";
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .setPriceType("On-demand")
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 5m && sleep infinity")
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .setDefaultChildNodes("1")
                .setWorkingNodesCount("2")
                .click(button("OK"))
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(1, getRunId())
                .shouldContainRun(getPipelineName(), String.valueOf(Integer.parseInt(getRunId()) + 1))
                .showLog(getRunId())
                .waitForTask(gridEngineAutoscalingTask)
                .click(taskWithName(gridEngineAutoscalingTask))
                .waitForLog(String.format("Additional worker with host=%s has been created.",
                        String.format("pipeline-%s", Integer.parseInt(getRunId()) + 2))
                );

        navigationMenu()
                .runs()
                .activeRuns()
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(2, getRunId())
                .shouldContainRun("pipeline", String.valueOf(Integer.parseInt(getRunId()) + 2))
                .showLog(getRunId())
                .ensure(taskWithName(gridEngineAutoscalingTask), visible)
                .click(taskWithName(gridEngineAutoscalingTask))
                .waitForLog(String.format("Additional worker with host=%s has been stopped.",
                        String.format("pipeline-%s", Integer.parseInt(getRunId()) + 2)));

        navigationMenu()
                .runs()
                .activeRuns()
                .openClusterRuns(getRunId())
                .validateStatus(getRunId(), LogAO.Status.WORKING)
                .validateStatus(String.valueOf(Integer.parseInt(getRunId()) + 1), LogAO.Status.WORKING)
                .validateStatus(String.valueOf(Integer.parseInt(getRunId()) + 2), LogAO.Status.STOPPED);
    }
}

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

import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LaunchClusterTest extends AbstractAutoRemovingPipelineRunningTest {

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
                .clusterSettingsForm("Auto-scaled cluster")
                .setWorkingNodesCount("1")
                .click(button("OK"))
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId());
        $(byClassName("run-" + getRunId()))
                .find(byClassName("ant-table-row-expand-icon"))
                .waitUntil(appears, MILLISECONDS.convert(600, SECONDS));
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
                .clusterSettingsForm("Auto-scaled cluster")
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
}

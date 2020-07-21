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
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.START_IDLE;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static com.epam.pipeline.autotests.ao.Primitive.TYPE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LaunchClusterTest extends AbstractAutoRemovingPipelineRunningTest implements Authorization {

    private final String autoScaledSettingForm = "Auto-scaled cluster";
    private final String clusterSettingForm = "Cluster";
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = "library";
    private final String testingTool = "rstudio";
    private final String testingNode = C.ANOTHER_INSTANCE;
    private final String instanceFamilyName = C.DEFAULT_INSTANCE_FAMILY_NAME;
    private final String gridEngineAutoscalingTask = "GridEngineAutoscaling";

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

    @BeforeMethod
    public void refreshPage() {
        getWebDriver().navigate().refresh();
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
                .clusterSettingsForm(clusterSettingForm)
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
                .clusterSettingsForm(clusterSettingForm)
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
                .waitForLog(String.format("Additional worker with host=%s and instance type=%s has been created.",
                        String.format("pipeline-%s", Integer.parseInt(getRunId()) + 2), C.DEFAULT_INSTANCE)
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

    @Test
    @TestCase({"EPMCMBIBPC-3150"})
    public void validationOfGECluster() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(clusterSettingForm)
                .clusterEnableCheckboxSelect("Enable GridEngine")
                .click(button("OK"))
                .checkConfigureClusterLabel("GridEngine Cluster (1 child node)")
                .expandTab(ADVANCED_PANEL)
                .click(START_IDLE)
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(1, getRunId())
                .showLog(getRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_SGE", "true"), exist)
                .waitForSshLink()
                .click(taskWithName("SGEMasterSetup"))
                .ensure(log(), containsMessages("SGE master node was successfully configured"))
                .click(taskWithName("SGEMasterSetupWorkers"))
                .ensure(log(), containsMessages("All execution hosts are connected"))
                .ssh(shell -> shell
                        .assertPageContains(String.format("[root@%s-%s",
                                getPipelineName().toLowerCase(), getRunId()))
                        .execute("qhost")
                        .assertOutputContains("HOSTNAME", "global", String.format("%s-%s lx-amd64",
                                getPipelineName().toLowerCase(), getRunId()), String.format("%s-%s lx-amd64",
                                getPipelineName().toLowerCase(), Integer.parseInt(getRunId()) + 1))
                        .execute("qstat")
                        .execute("qsub -b y -t 1:10 sleep 10m")
                        .assertOutputContains("Your job-array 1.1-10:1 (\"sleep\") has been submitted")
                        .execute("qstat")
                        .assertOutputContains(String.format("main.q@%s", getPipelineName().toLowerCase()).substring(0, 30))
                        .close());
    }

    @Test
    @TestCase({"EPMCMBIBPC-3151"})
    public void validationOfSlurmCluster() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(clusterSettingForm)
                .clusterEnableCheckboxSelect("Enable Slurm")
                .click(button("OK"))
                .checkConfigureClusterLabel("Slurm Cluster (1 child node)")
                .expandTab(ADVANCED_PANEL)
                .click(START_IDLE)
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(1, getRunId())
                .showLog(getRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_SLURM", "true"), exist)
                .waitForSshLink()
                .click(taskWithName("SLURMMasterSetup"))
                .ensure(log(), containsMessages("Master ENV is ready"))
                .click(taskWithName("SLURMMasterSetupWorkers"))
                .ensure(log(), containsMessages("All SLURM hosts are connected"))
                .ssh(shell -> shell
                        .execute("sinfo")
                        .assertOutputContains("main.q*", "idle", String.format("%s-[%s-%s]",
                                getPipelineName().toLowerCase(), getRunId(), Integer.parseInt(getRunId()) + 1))
                        .execute("srun -N2 -l /bin/hostname")
                        .assertOutputContains(String.format("0: %s-%s", getPipelineName().toLowerCase(), getRunId()),
                                String.format("1: %s-%s", getPipelineName().toLowerCase(), Integer.parseInt(getRunId()) + 1))
                        .close());
    }

    @Test
    @TestCase({"EPMCMBIBPC-3152"})
    public void validationOfApacheSparkCluster() {
        tools()
                .perform(defaultRegistry, defaultGroup, String.format("%s/%s", defaultGroup, testingTool), ToolTab::runWithCustomSettings)
                .selectValue(INSTANCE_TYPE, testingNode)
                .enableClusterLaunch()
                .clusterSettingsForm(clusterSettingForm)
                .clusterEnableCheckboxSelect("Enable Apache Spark")
                .click(button("OK"))
                .checkConfigureClusterLabel("Apache Spark Cluster (1 child node)")
                .click(START_IDLE)
                .launch(this)
                .shouldContainRun("pipeline", getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(1, getRunId())
                .showLog(getRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_SPARK", "true"), exist)
                .waitForEndpointLink()
                .click(taskWithName("SparkMasterSetup"))
                .ensure(log(), containsMessages("Spark master is started"))
                .click(taskWithName("SparkWorkerSetup"))
                .ensure(log(), containsMessages("Spark worker is started and connected to the master"))
                .click(taskWithName("SparkMasterSetupWorkers"))
                .ensure(log(), containsMessages("All workers are connected"))
                .clickOnEndpointLink("SparkUI")
                .sleep(3, SECONDS)
                .validationPageHeader(String.format("Spark Master at spark://pipeline-%s", getRunId()))
                .validateAliveWorkersSparkPage(" 2")
                .assertPageContains("Workers (2)")
                .closeTab();
    }

    @Test
    @TestCase({"EPMCMBIBPC-3155"})
    public void hybridAutoScaledClusterInstanceTypeFamily() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .enableHybridClusterSelect()
                .click(button("OK"))
                .setCommand("qsub -b y -t 1:10 sleep 15m && sleep infinity")
                .clickAddSystemParameter()
                .selectSystemParameters("CP_CAP_AUTOSCALE_HYBRID_FAMILY")
                .inputSystemParameterValue("CP_CAP_AUTOSCALE_HYBRID_FAMILY", instanceFamilyName)
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(TYPE, text(C.DEFAULT_INSTANCE_FAMILY_NAME))
                );
    }

    @Test
    @TestCase({"EPMCMBIBPC-3154"})
    public void hybridAutoScaledClusterCPUDeadlock() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .enableHybridClusterSelect()
                .click(button("OK"))
                .click(START_IDLE)
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .execute("qsub -b y -pe local 150 sleep 5m")
                        .assertOutputContains("Your job 1 (\"sleep\") has been submitted")
                        .sleep(20, SECONDS)
                        .execute("qstat")
                        .close());
        navigationMenu()
                .runs()
                .activeRuns()
                .showLog(getRunId())
                .waitForTask(gridEngineAutoscalingTask)
                .click(taskWithName(gridEngineAutoscalingTask))
                .ensure(log(), containsMessages("The following jobs cannot be satisfied with the " +
                        "requested resources and therefore they will be rejected: 1 (150 cpu)"))
                .ssh(shell -> shell
                        .execute("qsub -b y -pe local 50 sleep 5m")
                        .close());
        navigationMenu()
                .runs()
                .activeRuns()
                .showLog(getRunId())
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .ensure(STATUS, text(String.valueOf(Integer.parseInt(getRunId()) + 1)));
    }

    @Test
    @TestCase({"EPMCMBIBPC-3159"})
    public void hybridAutoScaledClusterCPUDeadlockWithAdditionalRestrictions() {
        final String systemParam = "CP_CAP_AUTOSCALE_HYBRID_MAX_CORE_PER_NODE";
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .enableHybridClusterSelect()
                .click(button("OK"))
                .click(START_IDLE)
                .clickAddSystemParameter()
                .selectSystemParameters(systemParam)
                .inputSystemParameterValue(systemParam, "40")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .execute("qsub -b y -pe local 50 sleep 5m")
                        .assertOutputContains("Your job 1 (\"sleep\") has been submitted")
                        .sleep(20, SECONDS)
                        .execute("qstat")
                        .close());
        navigationMenu()
                .runs()
                .activeRuns()
                .showLog(getRunId())
                .waitForTask(gridEngineAutoscalingTask)
                .click(taskWithName(gridEngineAutoscalingTask))
                .ensure(log(), containsMessages("The following jobs cannot be satisfied with the " +
                        "requested resources and therefore they will be rejected: 1 (50 cpu)"));
    }

    @Test
    @TestCase({"EPMCMBIBPC-3156"})
    public void autoScaledClusterWorkersPriceTypeSpot() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .setWorkingNodesCount("1")
                .setWorkersPriceType("Spot")
                .click(button("OK"))
                .setPriceType("On-demand")
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text("On-demand")))
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text("Spot")));
    }

    @Test
    @TestCase({"EPMCMBIBPC-3160"})
    public void autoScaledClusterWorkersPriceTypeOnDemand() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .setWorkingNodesCount("1")
                .setWorkersPriceType("On-demand")
                .click(button("OK"))
                .setPriceType("Spot")
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text("Spot")))
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text("On-demand")));
    }

    @Test
    @TestCase({"EPMCMBIBPC-3161"})
    public void autoScaledClusterWorkersPriceTypeMastersConfig() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .setWorkingNodesCount("1")
                .setWorkersPriceType("Master's config")
                .click(button("OK"))
                .setPriceType("Spot")
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text("Spot")))
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text("Spot")));
    }
}

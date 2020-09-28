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
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
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
import static com.codeborne.selenide.Condition.not;
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
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LaunchClusterTest extends AbstractAutoRemovingPipelineRunningTest implements Authorization {

    private final String autoScaledSettingForm = "Auto-scaled cluster";
    private final String clusterSettingForm = "Cluster";
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.ANOTHER_GROUP;
    private final String testingTool = C.ANOTHER_TESTING_TOOL_NAME;
    private final String testingNode = C.ANOTHER_INSTANCE;
    private final String instanceFamilyName = C.DEFAULT_INSTANCE_FAMILY_NAME;
    private final String gridEngineAutoscalingTask = "GridEngineAutoscaling";
    private final String spotPrice = C.SPOT_PRICE_NAME;
    private final String onDemandPrice = ON_DEMAND;
    private final String mastersConfigPrice = "Master's config";
    private final String sleepCommand = "sleep";

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
                .ok()
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
                .ok()
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
                .stopRunIfPresent(String.valueOf(Integer.parseInt(getRunId())+1))
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
        String childRunID1 = library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .setPriceType(onDemandPrice)
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 5m && sleep infinity")
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .setDefaultChildNodes("1")
                .setWorkingNodesCount("2")
                .ok()
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .waitForNestedRunsLink()
                .getNestedRunID(1);

        String childRunID2 = runsMenu()
                .activeRuns()
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(1, getRunId())
                .shouldContainRun(getPipelineName(), childRunID1)
                .showLog(getRunId())
                .waitForTask(gridEngineAutoscalingTask)
                .getNestedRunID(2);

        runsMenu()
                .activeRuns()
                .showLog(getRunId())
                .click(taskWithName(gridEngineAutoscalingTask))
                .waitForLog(String.format("Additional worker with host=%s and instance type=%s has been created.",
                        String.format("pipeline-%s", childRunID2), C.DEFAULT_INSTANCE));
        navigationMenu()
                .runs()
                .activeRuns()
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(2, getRunId())
                .shouldContainRun("pipeline", childRunID2)
                .showLog(getRunId())
                .ensure(taskWithName(gridEngineAutoscalingTask), visible)
                .click(taskWithName(gridEngineAutoscalingTask))
                .waitForLog(String.format("Additional worker with host=%s has been stopped.",
                        String.format("pipeline-%s", childRunID2)));

        navigationMenu()
                .runs()
                .activeRuns()
                .openClusterRuns(getRunId())
                .validateStatus(getRunId(), LogAO.Status.WORKING)
                .validateStatus(childRunID1, LogAO.Status.WORKING)
                .validateStatus(childRunID2, LogAO.Status.STOPPED);
    }

    @Test
    @TestCase({"EPMCMBIBPC-3150"})
    public void validationOfGECluster() {
        String childRunID = library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(clusterSettingForm)
                .clusterEnableCheckboxSelect("Enable GridEngine")
                .ok()
                .checkConfigureClusterLabel("GridEngine Cluster (1 child node)")
                .expandTab(ADVANCED_PANEL)
                .click(START_IDLE)
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(1, getRunId())
                .showLog(getRunId())
                .getNestedRunID(1);

        runsMenu()
                .activeRuns()
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
                                getPipelineName().toLowerCase(), childRunID))
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
        String childRunID = library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(clusterSettingForm)
                .clusterEnableCheckboxSelect("Enable Slurm")
                .ok()
                .checkConfigureClusterLabel("Slurm Cluster (1 child node)")
                .expandTab(ADVANCED_PANEL)
                .click(START_IDLE)
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainRunsWithParentRun(1, getRunId())
                .showLog(getRunId())
                .getNestedRunID(1);

        runsMenu()
                .activeRuns()
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
                                getPipelineName().toLowerCase(), getRunId(), childRunID))
                        .execute("srun -N2 -l /bin/hostname")
                        .assertOutputContains(String.format("0: %s-%s", getPipelineName().toLowerCase(), getRunId()),
                                String.format("1: %s-%s", getPipelineName().toLowerCase(), childRunID))
                        .close());
    }

    @Test
    @TestCase({"EPMCMBIBPC-3152"})
    public void validationOfApacheSparkCluster() {
        tools()
                .perform(defaultRegistry, defaultGroup, String.format("%s/%s", defaultGroup, testingTool),
                        ToolTab::runWithCustomSettings)
                .selectValue(INSTANCE_TYPE, testingNode)
                .enableClusterLaunch()
                .clusterSettingsForm(clusterSettingForm)
                .clusterEnableCheckboxSelect("Enable Apache Spark")
                .ok()
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
                .ok()
                .setCommand("qsub -b y -t 1:10 sleep 15m && sleep infinity")
                .clickAddSystemParameter()
                .selectSystemParameters("CP_CAP_AUTOSCALE_HYBRID_FAMILY")
                .ok()
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
                .ok()
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
    @TestCase({"EPMCMBIBPC-3153"})
    public void hybridAutoScaledCluster() {
        String cpu = library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .getCPU();
        onLaunchPage()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .enableHybridClusterSelect()
                .ok()
                .setCommand(String.format("qsub -b y -pe local %s sleep 15m && sleep infinity", Integer.parseInt(cpu) + 1))
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_AUTOSCALE", "true"), exist)
                .ensure(configurationParameter("CP_CAP_AUTOSCALE_WORKERS", "1"), exist)
                .ensure(configurationParameter("CP_CAP_AUTOSCALE_HYBRID", "true"), exist)
                .waitForSshLink()
                .ssh(shell -> shell
                        .execute("qhost")
                        .assertOutputContains("HOSTNAME", "global", String.format("%s-%s lx-amd64",
                                getPipelineName().toLowerCase(), getRunId()))
                        .sleep(20, SECONDS)
                        .execute("qstat")
                        .assertPageContains(sleepCommand, " 1 ")
                        .assertPageContains(sleepCommand, " qw ")
                        .assertPageContains(sleepCommand, String.format(" %s ", Integer.parseInt(cpu) + 1))
                        .close());
        String nestedRunID = navigationMenu()
                                .runs()
                                .activeRuns()
                                .showLog(getRunId())
                                .waitForNestedRunsLink()
                                .getNestedRunID(1);
        runsMenu()
                .activeRuns()
                .showLog(getRunId())
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(TYPE, text(C.DEFAULT_INSTANCE.substring(0, C.DEFAULT_INSTANCE.indexOf("."))))
                        .ensure(TYPE, not(text(C.DEFAULT_INSTANCE.substring(C.DEFAULT_INSTANCE.indexOf(".")))))
                )
                .waitForSshLink()
                .ssh(shell -> shell
                        .execute("qhost")
                        .assertOutputContains("HOSTNAME", "global", String.format("%s-%s lx-amd64",
                                getPipelineName().toLowerCase(), getRunId()), String.format("pipeline-%s",
                                nestedRunID))
                        .sleep(20, SECONDS)
                        .execute("qstat")
                        .assertPageContains(sleepCommand, " r ")
                        .assertPageContains(sleepCommand, String.format("main.q@pipeline-%s", nestedRunID))
                        .close());
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
                .ok()
                .click(START_IDLE)
                .clickAddSystemParameter()
                .selectSystemParameters(systemParam)
                .ok()
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
                .setWorkersPriceType(spotPrice)
                .ok()
                .setPriceType(onDemandPrice)
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text(onDemandPrice)))
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text(spotPrice)));
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
                .setWorkersPriceType(onDemandPrice)
                .ok()
                .setPriceType(spotPrice)
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text(spotPrice)))
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text(onDemandPrice)));
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
                .setWorkersPriceType(mastersConfigPrice)
                .ok()
                .setPriceType(spotPrice)
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .showLog(getRunId())
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text(spotPrice)))
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text(spotPrice)));
    }

    private static PipelineRunFormAO onLaunchPage() {
        return new PipelineRunFormAO();
    }
}

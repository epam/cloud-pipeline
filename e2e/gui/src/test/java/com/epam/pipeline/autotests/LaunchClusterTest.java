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
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Objects;

import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessage;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.DEFAULT_CHILD_NODES;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.START_IDLE;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static com.epam.pipeline.autotests.ao.Primitive.TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.WORKING_NODES;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.C.COMPLETION_TIMEOUT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.lang.String.format;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MINUTES;

public class LaunchClusterTest extends AbstractAutoRemovingPipelineRunningTest implements Authorization {

    private final String autoScaledSettingForm = "Auto-scaled cluster";
    private final String clusterSettingForm = "Cluster";
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.ANOTHER_GROUP;
    private final String testingTool = C.ANOTHER_TESTING_TOOL_NAME;
    private final String testingNode = C.ANOTHER_INSTANCE;
    private final String instanceFamilyName = C.DEFAULT_INSTANCE_FAMILY_NAME;
    private final String gridEngineAutoscalingTask = "SGEAutoscaling";
    private final String spotPrice = C.SPOT_PRICE_NAME;
    private final String onDemandPrice = ON_DEMAND;
    private final String mastersConfigPrice = "Master's config";
    private final String sleepCommand = "sleep";
    private final String childNodesField = "Child nodes:";
    private final String autoScaledUpToField = "Auto-scaled up to:";
    private final String defaultChildNodesField = "Default child nodes:";

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

    @BeforeMethod
    public void refreshPage() {
        open(C.ROOT_ADDRESS);
        logout();
        loginAs(admin);
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
		.setPriceType(onDemandPrice)
                .enableClusterLaunch()
                .ensureOpened()
                .clusterSettingsForm(clusterSettingForm)
                .setWorkingNodesCount("2")
                .ok()
                .launch(this)
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainExactlyRunsWithParentRun(2, getRunId())
                .showLog(getRunId())
                .waitForCompletion()
                .ensure(STATUS, LogAO.Status.SUCCESS.reached);

        navigationMenu()
                .runs()
                .completedRuns()
                .shouldContainRun(getPipelineName(), getRunId())
                .openClusterRuns(getRunId())
                .shouldContainExactlyRunsWithParentRun(2, getRunId())
                .validateStatus(getRunId(), LogAO.Status.SUCCESS)
                .childRunsShouldHaveStatus(getRunId(), LogAO.Status.SUCCESS);
    }

    @Test
    @TestCase({"EPMCMBIBPC-2618"})
    public void launchAutoScaledClusterTest() {
        String childRunId = null;
        try {
            library()
                    .createPipeline(Template.SHELL, getPipelineName())
                    .clickOnPipeline(getPipelineName())
                    .firstVersion()
                    .runPipeline()
                    .setDefaultLaunchOptions()
                    .setPriceType(onDemandPrice)
                    .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity")
                    .enableClusterLaunch()
                    .clusterSettingsForm(autoScaledSettingForm)
                    .setWorkingNodesCount("1")
                    .ok()
                    .launch(this)
                    .shouldContainRun(getPipelineName(), getRunId());
            $(byClassName("run-" + getRunId()))
                    .find(byClassName("ant-table-row-expand-icon"))
                    .shouldBe(appears, ofMillis(C.COMPLETION_TIMEOUT));
            final RunsMenuAO clusterRuns = navigationMenu()
                    .runs()
                    .activeRuns()
                    .shouldContainRun(getPipelineName(), getRunId())
                    .runShouldBeRunning(getRunId())
                    .openClusterRuns(getRunId())
                    .shouldContainExactlyRunsWithParentRun(1, getRunId());
            childRunId = clusterRuns.childRunIds(getRunId()).get(0);
            clusterRuns
                    .runShouldBeRunning(childRunId)
                    .showLog(getRunId())
                    .waitForSshLink()
                    .shareWithUser(user.login, false);
            addAccountToPipelinePermissions(user, getPipelineName());
            givePermissions(user, PipelinePermission.allow(READ, getPipelineName()));
            logout();
            loginAs(user)
                    .runs()
                    .viewAvailableActiveRuns()
                    .openClusterRuns(getRunId())
                    .stopAndTerminateShouldBeUnavailable(getRunId())
                    .stopAndTerminateShouldBeUnavailable(childRunId);
        } finally {
            logoutIfNeeded();
            loginAs(admin);
            stopClusterRuns(getRunId(), childRunId);
        }
    }

    @Test(priority = 1)
    @TestCase({"EPMCMBIBPC-2620"})
    public void invalidValuesOnConfigureClusterPopUp() {
        library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .runFirstVersion()
                .setDefaultLaunchOptions()
                .enableClusterLaunch()
                .clusterSettingsForm(clusterSettingForm)
                .setWorkingNodesCount("0")
                .fieldMessageShouldBe(childNodesField, "Value should be greater than 0")
                .setWorkingNodesCount("-1")
                .ensure(WORKING_NODES, attribute("value", "-1"))
                .fieldMessageShouldBe(childNodesField, "Value should be greater than 0")
                .setWorkingNodesCount("asdf")
                .fieldMessageShouldBe(childNodesField, "Enter positive number")
                .clusterSettingsForm(autoScaledSettingForm)
                .setWorkingNodesCount("0")
                .fieldMessageShouldBe(autoScaledUpToField, "Value should be greater than 0")
                .setWorkingNodesCount("-1")
                .ensure(WORKING_NODES, attribute("value", "-1"))
                .fieldMessageShouldBe(autoScaledUpToField, "Value should be greater than 0")
                .setWorkingNodesCount("asdf")
                .fieldMessageShouldBe(autoScaledUpToField, "Enter positive number")
                .setDefaultChildNodes("3")
                .fieldMessageShouldBe(defaultChildNodesField,
                        "Max child nodes count should be greater than child nodes count")
                .setDefaultChildNodesCount("0")
                .fieldMessageShouldBe(defaultChildNodesField, "Value should be greater than 0")
                .setDefaultChildNodesCount("-1")
                .ensure(DEFAULT_CHILD_NODES, attribute("value", "-1"))
                .fieldMessageShouldBe(defaultChildNodesField, "Value should be greater than 0")
                .setDefaultChildNodesCount("asdf")
                .fieldMessageShouldBe(defaultChildNodesField, "Enter positive number");
    }

    @Test
    @TestCase({"EPMCMBIBPC-2628"})
    public void autoScaledClusterWithDefaultChildNodesValidationTest() {
        String childRunID1 = null;
        String childRunID2 = null;
        try {
            LogAO logAO = library()
                    .createPipeline(Template.SHELL, getPipelineName())
                    .clickOnPipeline(getPipelineName())
                    .firstVersion()
                    .runPipeline()
                    .setDefaultLaunchOptions()
                    .setPriceType(onDemandPrice)
                    // the second worker must come up while jobs still wait: without storage mounts it starts in ~6 min
                    // (26 storages took ~5.5 min more), and the jobs of 10 min keep the queue busy beyond that
                    .doNotMountStoragesSelect(true)
                    .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 10m && sleep infinity")
                    .enableClusterLaunch()
                    .clusterSettingsForm(autoScaledSettingForm)
                    .click(byText("Setup default child nodes count"))
                    .setWorkingNodesCount("2")
                    .setDefaultChildNodesCount("1")
                    .ok()
                    .launch(this)
                    .shouldContainRun(getPipelineName(), getRunId())
                    .showLog(getRunId())
                    .waitForTask(gridEngineAutoscalingTask)
                    .waitForSshLink();
            childRunID1 = logAO
                    .waitForNestedRunsLink()
                    .getNestedRunID(1);

            runsMenu()
                    .activeRuns()
                    .openClusterRuns(getRunId())
                    .shouldContainRunsWithParentRun(1, getRunId())
                    .shouldContainRun(getPipelineName(), childRunID1)
                    .runShouldHaveParentRun(childRunID1, getRunId())
                    .showLog(getRunId())
                    .click(taskWithName(gridEngineAutoscalingTask))
                    .waitForLog(String.format("Additional worker pipeline-\\d+ \\(%s\\) has been scaled up.",
                            C.DEFAULT_INSTANCE));
            childRunID2 = new LogAO().getNestedRunID(2);
            new LogAO()
                    .ensure(log(), containsMessage(String.format("Additional worker pipeline-%s (%s) has been scaled up.",
                            childRunID2, C.DEFAULT_INSTANCE)));
            final String scaledDownMessage = String.format("Additional worker pipeline-%s has been scaled down.",
                    childRunID2);
            navigationMenu()
                    .runs()
                    .activeRuns()
                    .openClusterRuns(getRunId())
                    .shouldContainExactlyRunsWithParentRun(2, getRunId())
                    .shouldContainRun("pipeline", childRunID2)
                    .runShouldHaveParentRun(childRunID2, getRunId())
                    .runShouldBeRunning(getRunId())
                    .runShouldBeRunning(childRunID1)
                    .runShouldBeRunning(childRunID2)
                    .showLog(getRunId())
                    .ensure(taskWithName(gridEngineAutoscalingTask), visible)
                    .click(taskWithName(gridEngineAutoscalingTask))
                    .waitForLog(scaledDownMessage)
                    .ensure(log(), containsMessage(scaledDownMessage));

            navigationMenu()
                    .runs()
                    .activeRuns()
                    .openClusterRuns(getRunId())
                    .runShouldBeRunning(getRunId())
                    .runShouldBeRunning(childRunID1)
                    .validateStatus(childRunID2, LogAO.Status.STOPPED);
        } finally {
            stopClusterRuns(getRunId(), childRunID1, childRunID2);
        }
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
                .setPriceType(onDemandPrice)
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
                        .sleep(2, SECONDS)
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
                .setPriceType(onDemandPrice)
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
                .waitForSshLink()
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
                .waitForSshLink()
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
                .setPriceType(onDemandPrice)
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
                        .sleep(20, SECONDS)
                        .execute("qstat")
                        .close());
        final String nestedRunID = navigationMenu()
                .runs()
                .activeRuns()
                .showLog(getRunId())
                .waitForSshLink()
                .waitForNestedRunsLink()
                .getNestedRunID(1);
        new LogAO()
                .clickOnNestedRunLink()
                .ensure(STATUS, text(nestedRunID));
    }

    @Test
    @TestCase({"EPMCMBIBPC-3153"})
    public void hybridAutoScaledCluster() {
        String[] runID = new String[2];
        String cpu = library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .setDefaultLaunchOptions()
                .setPriceType(onDemandPrice)
                .getCPU();
        onLaunchPage()
                .enableClusterLaunch()
                .clusterSettingsForm(autoScaledSettingForm)
                .enableHybridClusterSelect()
                .ok()
                .setCommand(String.format("qsub -b y -pe local %s sleep 15m && sleep infinity", Integer.parseInt(cpu) + 1))
                .launch(this)
                .shouldContainRun(runID[1] = getPipelineName(), runID[0] = getRunId())
                .showLog(runID[0])
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_AUTOSCALE", "true"), exist)
                .ensure(configurationParameter("CP_CAP_AUTOSCALE_WORKERS", "1"), exist)
                .ensure(configurationParameter("CP_CAP_AUTOSCALE_HYBRID", "true"), exist)
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runID[1], runID[0])
                        .execute("qhost")
                        .assertOutputContains("HOSTNAME", "global", String.format("%s-%s lx-amd64",
                                runID[1].toLowerCase(), runID[0]))
                        .sleep(20, SECONDS)
                        .execute("qstat")
                        .assertPageContains(sleepCommand, " 1 ")
                        .assertPageContains(sleepCommand, " qw ")
                        .assertPageContains(sleepCommand, String.format(" %s ", Integer.parseInt(cpu) + 1))
                        .close());
        String nestedRunID = navigationMenu()
                                .runs()
                                .activeRuns()
                                .showLog(runID[0])
                                .waitForSshLink()
                                .waitForNestedRunsLink()
                                .getNestedRunID(1);
        runsMenu()
                .activeRuns()
                .showLog(runID[0])
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(TYPE, text(C.DEFAULT_INSTANCE.substring(0, C.DEFAULT_INSTANCE.indexOf("."))))
                        .ensure(TYPE, not(text(C.DEFAULT_INSTANCE.substring(C.DEFAULT_INSTANCE.indexOf(".")))))
                )
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runID[1], runID[0])
                        .execute("qhost")
                        .assertOutputContains("HOSTNAME", "global", String.format("%s-%s lx-amd64",
                                runID[1].toLowerCase(), runID[0]), String.format("pipeline-%s", nestedRunID))
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
                .setPriceType(onDemandPrice)
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
                .waitForSshLink()
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
                .waitForSshLink()
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
                .waitForSshLink()
                .waitForNestedRunsLink()
                .clickOnNestedRunLink()
                .instanceParameters(instance ->
                        instance.ensure(PRICE_TYPE, text(spotPrice)));
    }

    private static PipelineRunFormAO onLaunchPage() {
        return new PipelineRunFormAO();
    }

    /**
     * Stops the still running child runs of the cluster first and then the parent run.
     * The rows of the child runs are shown only while the parent run is expanded.
     */
    private void stopClusterRuns(final String parentRunId, final String... childRunIds) {
        if (parentRunId == null) {
            return;
        }
        try {
            final RunsMenuAO runs = navigationMenu()
                    .runs()
                    .activeRuns()
                    .openClusterRuns(parentRunId);
            Arrays.stream(childRunIds)
                    .filter(Objects::nonNull)
                    .filter(childRunId -> $("#run-" + childRunId + "-stop-button").is(visible))
                    .forEach(runs::stopRun);
        } catch (Throwable e) {
            System.out.printf("Child runs of %s could not be stopped: %s%n", parentRunId, e.getMessage());
        }
        try {
            runsMenu().stopRunIfPresent(parentRunId);
        } catch (Throwable e) {
            System.out.printf("Run %s could not be stopped: %s%n", parentRunId, e.getMessage());
        }
    }
}

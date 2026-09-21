/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.codeborne.selenide.Condition.exist;
import com.epam.pipeline.autotests.ao.LogAO;
import static com.epam.pipeline.autotests.ao.LogAO.Status.PAUSED;
import static com.epam.pipeline.autotests.ao.LogAO.Status.STOPPED;
import static com.epam.pipeline.autotests.ao.NodePage.labelWithText;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import static com.epam.pipeline.autotests.utils.Utils.readResourceFully;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.SPOT;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.Boolean.parseBoolean;

import java.util.concurrent.TimeUnit;

public class IdleRunsTest extends AbstractSeveralPipelineRunningTest implements Tools, Authorization {

    private static final String SYSTEM_IDLE_MONITORING_CONFIG = "system.idle.monitoring.config";
    private static final String SYSTEM_RUN_TAG_DATE_SUFFIX = "system.run.tag.date.suffix";
    private static final String TAG_DATE_SUFFIX = "_date";

    private static final String IDLE_CPU = "IDLE_CPU";
    private static final String IDLE_GPU = "IDLE_GPU";
    private static final String IDLE = "IDLE";

    private static final String IDLE_CPU_MONITORING_CONFIG_JSON = "/idleRunsMonitoringConfigCpu.json";
    private static final String IDLE_GPU_MONITORING_CONFIG_JSON = "/idleRunsMonitoringConfigGpu.json";
    private static final String IDLE_CPU_DISABLED_MONITORING_CONFIG_JSON = "/idleRunsMonitoringConfigCpuDisabled.json";
    private static final String IDLE_GPU_DISABLED_MONITORING_CONFIG_JSON = "/idleRunsMonitoringConfigGpuDisabled.json";
    private static final String SYSTEM_IDLE_MONITORING_CONFIG_JSON = "/systemIdleMonitoringConfig.json";

    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = "library";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String gpuTool = C.GPU_TESTING_TOOL;
    private final String instanceType = C.DEFAULT_INSTANCE;
    private final String gpuInstanceType = C.DEFAULT_GPU_INSTANCE;
    private final String diskSize = "30";
    private String notificationTitle = "Job #%s is %s Idle for a long time";

    private String[] systemIdleMonitoringConfigInit;
    private String[] systemRunTagDateSuffixInit;

    private String gpuRunId;

    @BeforeClass(alwaysRun = true)
    public void savePreferences() {
        systemIdleMonitoringConfigInit = navigationMenu()
                .settings()
                .switchToPreferences()
                .searchPreference(SYSTEM_IDLE_MONITORING_CONFIG)
                .getPreference(SYSTEM_IDLE_MONITORING_CONFIG);
        systemRunTagDateSuffixInit = navigationMenu()
                .settings()
                .switchToPreferences()
                .searchPreference(SYSTEM_RUN_TAG_DATE_SUFFIX)
                .getLinePreference(SYSTEM_RUN_TAG_DATE_SUFFIX);
    }

    @BeforeClass(alwaysRun = true)
    public void setRunTagDateSuffix() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setPreference(SYSTEM_RUN_TAG_DATE_SUFFIX, TAG_DATE_SUFFIX, true)
                .saveIfNeeded();
    }

    @AfterClass(alwaysRun = true)
    public void restorePreferences() {
        setIdleMonitoringConfig(systemIdleMonitoringConfigInit[0],
                parseBoolean(systemIdleMonitoringConfigInit[1]));
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setPreference(SYSTEM_RUN_TAG_DATE_SUFFIX, systemRunTagDateSuffixInit[0],
                        parseBoolean(systemRunTagDateSuffixInit[1]))
                .saveIfNeeded();
        if (gpuRunId != null) {
            navigationMenu().runs().stopRunIfPresent(gpuRunId);
        }
    }

    @Test
    @TestCase({"4512_1"})
    public void idleCPUandIdleTagsForNonGPUrun() {
        setIdleMonitoringConfig(readResourceFully(IDLE_CPU_MONITORING_CONFIG_JSON), true);

        final String runId = launchCpuTool();
        try {
            runsMenu()
                    .activeRuns()
                    .showLog(getLastRunId())
                    .waitForSshLink()
                    .expandTab(INSTANCE)
                    .checkTagsVisible(IDLE_CPU, IDLE)
                    .clickTag(IDLE_CPU)
                    .ensure(labelWithText(format("RUN ID %s", getLastRunId())), exist)
                    .click(labelWithText(format("RUN ID %s", getLastRunId())), LogAO::new)
                    .clickTag(IDLE)
                    .ensure(labelWithText(format("RUN ID %s", getLastRunId())), exist);
            runsMenu()
                    .activeRuns()
                    .checkTagsVisible(runId, IDLE_CPU, IDLE)
                    .openMonitorPageViaTag(runId, IDLE_CPU)
                    .ensure(labelWithText(format("RUN ID %s", getLastRunId())), exist);
            runsMenu()
                    .activeRuns()
                    .openMonitorPageViaTag(runId, IDLE)
                    .ensure(labelWithText(format("RUN ID %s", getLastRunId())), exist);
            home()
                    .checkTagsVisible(runId, IDLE_CPU, IDLE);
        } finally {
            navigationMenu().runs().stopRunIfPresent(runId);
        }
    }

    @Test
    @TestCase({"4512_2"})
    public void idleGPUtagForGPUrunWithNoGPUactivity() {
        setIdleMonitoringConfig(readResourceFully(IDLE_GPU_MONITORING_CONFIG_JSON), true);

        gpuRunId = launchGpuTool(ON_DEMAND);
        runsMenu()
                .activeRuns()
                .activeRuns()
                .showLog(getLastRunId())
                .waitForSshLink()
                .expandTab(INSTANCE)
                .checkTagsVisible(IDLE_GPU)
                .checkTagsNotVisible(IDLE_CPU, IDLE)
                .clickTag(IDLE_GPU)
                .ensure(labelWithText(format("RUN ID %s", gpuRunId)), exist);
        runsMenu()
                .activeRuns()
                .checkTagsVisible(gpuRunId, IDLE_GPU)
                .checkTagsNotVisible(gpuRunId, IDLE_CPU, IDLE);
        sleep(2, MINUTES);
        home()
                .checkTagsVisible(gpuRunId, IDLE_GPU, IDLE_CPU, IDLE);
    }

    @Test(dependsOnMethods = "idleGPUtagForGPUrunWithNoGPUactivity")
    @TestCase({"4512_3"})
    public void notificationsForIdleStates() {
        runsMenu()
                .activeRuns()
                .checkTagsVisible(gpuRunId, IDLE_CPU, IDLE_GPU, IDLE);
        notifications()
                .checkNotificationExist(format(notificationTitle, gpuRunId, "GPU"))
                .checkNotificationExist(format(notificationTitle, gpuRunId, "CPU"))
                .checkNotificationExist(format(notificationTitle, gpuRunId, "completely"))
        ;
    }

    @Test(dependsOnMethods = "notificationsForIdleStates")
    @TestCase({"4512_4"})
    public void idleTagDisappearsWhenActivityAppears() {
        try {
            runsMenu()
                    .activeRuns()
                    .checkTagsVisible(gpuRunId, IDLE_GPU, IDLE_CPU, IDLE)
                    .showLog(gpuRunId)
                    .waitForSshLink()
                    .ssh(shell -> shell
                            .waitUntilTextAppears(gpuRunId)
                            .execute("sudo dnf config-manager --set-enabled powertools")
                            .execute("sudo dnf install epel-release -y")
                            .execute("sudo dnf install stress -y")
                            .execute("sudo stress --cpu 4 --io 2 --vm 2 --vm-bytes 256M --timeout 1200s")
                    )
                    .expandTab(INSTANCE)
                    .checkTagsNotVisible(IDLE_CPU, IDLE)
                    .checkTagsVisible(IDLE_GPU);
        } finally {
            navigationMenu().runs().stopRunIfPresent(gpuRunId);
        }
    }

    @Test
    @TestCase({"4512_5"})
    public void checkPauseActionForGpuIdleRun() {
        setIdleMonitoringConfig(gpuActionConfig("PAUSE"), true);

        final String runId = launchGpuTool(ON_DEMAND);
        try {
            runsMenu()
                    .activeRuns()
                    .showLog(runId)
                    .waitForSshLink()
                    .checkTagsVisible(IDLE_GPU, IDLE_CPU, IDLE)
                    .sleep(2, MINUTES)
                    .waitForResumeButton()
                    .shouldHaveStatus(PAUSED);
            notifications()
                    .checkNotificationExist(format("Job #%s was PAUSED as it was Idle for a long time", gpuRunId));
        } finally {
            if (runsMenu().isActiveRun(runId)) {
                runsMenu().terminateRun(runId, format("pipeline-%s", runId));
            }
        }
    }

    @Test
    @TestCase({"4512_6"})
    public void checkPauseOrStopActionForSpotGpuRun() {
        setIdleMonitoringConfig(gpuActionConfig("PAUSE_OR_STOP"), true);

        final String runId = launchGpuTool(SPOT);
        try {
            runsMenu()
                    .activeRuns()
                    .checkTagsVisible(runId, IDLE_GPU, IDLE_CPU, IDLE)
                    .sleep(2, MINUTES)
                    .waitForCompletion(runId)
                    .completedRuns()
                    .validateStatus(runId, STOPPED);
            notifications()
                    .checkNotificationExist(format("Job #%s was STOPPED as it was Idle for a long time", gpuRunId));
        } finally {
            navigationMenu().runs().stopRunIfPresent(runId);
        }
    }

    @Test
    @TestCase({"4512_7"})
    public void checkPauseOrStopActionForOnDemandGpuRun() {
        setIdleMonitoringConfig(gpuActionConfig("PAUSE_OR_STOP"), true);

        final String runId = launchGpuTool(ON_DEMAND);
        try {
            runsMenu()
                    .activeRuns()
                    .checkTagsVisible(runId, IDLE_GPU, IDLE_CPU, IDLE)
                    .sleep(3, MINUTES)
                    .waitUntilResumeButtonAppear(runId)
                    .validateStatus(runId, PAUSED);
            notifications()
                    .checkNotificationExist(format("Job #%s was PAUSED as it was Idle for a long time", gpuRunId));
        } finally {
            if (runsMenu().isActiveRun(runId)) {
                runsMenu().terminateRun(runId, format("pipeline-%s", runId));
            }
        }
    }

    @Test
    @TestCase({"4512_8"})
    public void checkRunNOTmarkedIDLEwhenCPUconfigdisabled() {
        setIdleMonitoringConfig(readResourceFully(IDLE_CPU_DISABLED_MONITORING_CONFIG_JSON), true);

        final String runId = launchGpuTool(ON_DEMAND);
        try {
            runsMenu()
                    .activeRuns()
                    .activeRuns()
                    .showLog(runId)
                    .waitForSshLink()
                    .checkTagsVisible(IDLE_GPU)
                    .checkTagsNotVisible(IDLE_CPU, IDLE);
            runsMenu()
                    .activeRuns()
                    .checkTagsVisible(runId, IDLE_GPU)
                    .checkTagsNotVisible(runId, IDLE_CPU, IDLE);
            home()
                    .checkTagsVisible(runId, IDLE_GPU)
                    .checkTagsNotVisible(runId, IDLE_CPU, IDLE);
        } finally {
            navigationMenu().runs().stopRunIfPresent(runId);
        }
    }

    @Test
    @TestCase({"4512_9"})
    public void checkRunNOTmarkedIDLEwhenGPUconfigdisabled() {
        setIdleMonitoringConfig(readResourceFully(IDLE_GPU_DISABLED_MONITORING_CONFIG_JSON), true);

        final String runId = launchGpuTool(ON_DEMAND);
        try {
            runsMenu()
                    .activeRuns()
                    .activeRuns()
                    .showLog(runId)
                    .waitForSshLink()
                    .checkTagsVisible(IDLE_CPU)
                    .checkTagsNotVisible(IDLE_GPU, IDLE);
            runsMenu()
                    .activeRuns()
                    .checkTagsVisible(runId, IDLE_CPU)
                    .checkTagsNotVisible(runId, IDLE_GPU, IDLE);
            home()
                    .checkTagsVisible(runId, IDLE_CPU)
                    .checkTagsNotVisible(runId, IDLE_GPU, IDLE);
        } finally {
            navigationMenu().runs().stopRunIfPresent(runId);
        }
    }

    private String launchCpuTool() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setLaunchOptions(diskSize, instanceType, null)
                .doNotMountStoragesSelect(true)
                .setPriceType(ON_DEMAND)
                .launchTool(this, nameWithoutGroup(tool));
        return getLastRunId();
    }

    private String launchGpuTool(final String priceType) {
        tools()
                .perform(registry, group, gpuTool, ToolTab::runWithCustomSettings)
                .setLaunchOptions(diskSize, gpuInstanceType, null)
                .doNotMountStoragesSelect(true)
                .setPriceType(priceType)
                .launchTool(this, nameWithoutGroup(gpuTool));
        return getLastRunId();
    }

    private void setIdleMonitoringConfig(final String config, boolean eyeIsChecked) {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .searchPreference(SYSTEM_IDLE_MONITORING_CONFIG)
                .updateCodeText(SYSTEM_IDLE_MONITORING_CONFIG, config, eyeIsChecked)
                .saveIfNeeded();
    }

    private String gpuActionConfig(final String action) {
        return readResourceFully(SYSTEM_IDLE_MONITORING_CONFIG_JSON)
                .replace("{{action}}", action);
    }
}

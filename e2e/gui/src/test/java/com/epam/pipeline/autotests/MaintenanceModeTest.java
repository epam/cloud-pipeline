/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.NotificationAO;
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import static com.epam.pipeline.autotests.ao.ToolVersions.hasOnPage;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.LogAO.Status.PAUSED;
import static com.epam.pipeline.autotests.ao.Primitive.AUTOSCALED;
import static com.epam.pipeline.autotests.ao.Primitive.CLOUD_REGION;
import static com.epam.pipeline.autotests.ao.Primitive.COMMIT;
import static com.epam.pipeline.autotests.ao.Primitive.CONDITION;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.ENDS_ON;
import static com.epam.pipeline.autotests.ao.Primitive.ENDS_ON_TIME;
import static com.epam.pipeline.autotests.ao.Primitive.INFO;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.PAUSE;
import static com.epam.pipeline.autotests.ao.Primitive.POOL_NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.RESUME;
import static com.epam.pipeline.autotests.ao.Primitive.STARTS_ON;
import static com.epam.pipeline.autotests.ao.Primitive.STARTS_ON_TIME;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.utils.Utils.randomSuffix;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.time.format.TextStyle.FULL;
import static java.util.Locale.getDefault;
import static java.util.concurrent.TimeUnit.SECONDS;

public class MaintenanceModeTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    private final String testSystemMaintenanceModeBanner = "Test of maintenance mode. Some of the features are disabled.";
    private final String maintenanceModeTooltip = "Platform is in a maintenance mode, operation is temporary unavailable";
    private final String titleText = "Maintenance mode";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String defaultInstance = C.DEFAULT_INSTANCE;
    private final String poolName = format("test_pool-%s", randomSuffix());
    private final String version = format("version-%s", randomSuffix());
    private final String clusterInstanceHddExtraMmulti = "cluster.instance.hdd_extra_multi";
    private final String clusterDockerExtraMulti = "cluster.docker.extra_multi";
    private String defaultSystemMaintenanceModeBanner;
    private String run1ID = "";
    private String run2ID = "";
    private String run3ID = "";
    private String[] defaultRegion;
    private String[] initialClusterInstanceHddExtraMulti;
    private String[] initialClusterDockerExtraMulti;

    @BeforeClass(alwaysRun = true)
    public void getDefaultPreferences() {
        defaultSystemMaintenanceModeBanner = navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem()
                .getSystemMaintenanceModeBanner();
        defaultRegion = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference("default.edge.region");
        initialClusterInstanceHddExtraMulti = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference(clusterInstanceHddExtraMmulti);
        initialClusterDockerExtraMulti = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference(clusterDockerExtraMulti);
    }

    @BeforeMethod
    void openApplication() {
        open(C.ROOT_ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public void restorePreferences() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem()
                .setSystemMaintenanceModeBanner(defaultSystemMaintenanceModeBanner)
                .switchToSystem()
                .disableSystemMaintenanceMode()
                .saveIfNeeded()
                .setPreference(clusterInstanceHddExtraMmulti, initialClusterInstanceHddExtraMulti[0],
                        parseBoolean(initialClusterInstanceHddExtraMulti[1]))
                .saveIfNeeded()
                .setPreference(clusterDockerExtraMulti, initialClusterDockerExtraMulti[0],
                        parseBoolean(initialClusterDockerExtraMulti[1]))
                .saveIfNeeded();
        clusterMenu()
                .switchToHotNodePool()
                .searchForNodeEntry(poolName)
                .deleteNode(poolName);
        final RunsMenuAO runsMenuAO = runsMenu();
        if (runsMenuAO.isActiveRun(run2ID)) {
            runsMenuAO
                    .terminateRun(run2ID, format("pipeline-%s", run2ID));
        }
    }

    @Test(priority = 1)
    @TestCase(value = {"2423_1"})
    public void maintenanceModeNotification() {
        setSystemMaintenanceModeBanner(testSystemMaintenanceModeBanner);
        setEnableSystemMaintenanceMode();
        new NotificationAO(titleText)
                .ensureSeverityIs(INFO.name())
                .ensureTitleIs(titleText)
                .ensureBodyIs(testSystemMaintenanceModeBanner)
                .close();
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem()
                .setEmptySystemMaintenanceModeBanner()
                .save();
        refresh();
        ensureNotificationIsAbsent(titleText);
    }

    @Test(priority = 2, dependsOnMethods = {"maintenanceModeNotification"})
    @TestCase(value = {"2423_2"})
    public void checkLaunchRunInMaintenanceMode() {
        launchToolOnDemand()
                .waitUntilPauseButtonAppear(run1ID = getLastRunId())
                .ensurePauseButtonDisabled(run1ID)
                .checkPauseButtonTooltip(run1ID, maintenanceModeTooltip)
                .showLog(run1ID)
                .ensureButtonDisabled(PAUSE)
                .ensureButtonDisabled(COMMIT)
                .checkButtonTooltip(PAUSE, maintenanceModeTooltip)
                .checkButtonTooltip(COMMIT, maintenanceModeTooltip);
        home()
                .checkPauseLinkIsDisabledOnActiveRunsPanel(run1ID)
                .checkActiveRunPauseLinkTooltip(run1ID, maintenanceModeTooltip);
        setDisableSystemMaintenanceMode();
        navigationMenu()
                .runs()
                .showLog(run1ID)
                .ensureAll(enabled, PAUSE, COMMIT);
    }

    @Test(priority = 3, dependsOnMethods = {"checkLaunchRunInMaintenanceMode"})
    @TestCase(value = {"2423_3"})
    public void checkSwitchToMaintenanceModeDuringTheRunCommittingOperation() {
        try {
            setDisableSystemMaintenanceMode();
            runsMenu()
                    .log(run1ID, log ->
                            log.waitForCommitButton()
                                    .commit(commit ->
                                            commit.sleep(1, SECONDS)
                                                    .setVersion(version)
                                                    .sleep(1, SECONDS)
                                                    .ok())
                    );
            setEnableSystemMaintenanceMode();
            runsMenu()
                    .showLog(run1ID)
                    .assertCommittingFinishedSuccessfully()
                    .ensureButtonDisabled(PAUSE)
                    .ensureButtonDisabled(COMMIT);
        } finally {
            tools()
                    .perform(registry, group, tool, tool ->
                            tool.versions()
                                    .viewUnscannedVersions()
                                    .performIf(hasOnPage(version), t -> t.deleteVersion(version))
                    );
        }
    }

    @Test(priority = 4, dependsOnMethods = {"maintenanceModeNotification"})
    @TestCase(value = {"2423_4"})
    public void checkSwitchToMaintenanceModeDuringTheRunPausingAndResumingOperation() {
        setDisableSystemMaintenanceMode();
        launchToolOnDemand();
        run2ID = getLastRunId();
        launchToolOnDemand()
                .waitUntilPauseButtonAppear(run3ID = getLastRunId())
                .pause(run3ID, nameWithoutGroup(tool))
                .waitUntilResumeButtonAppear(run3ID);
        runsMenu()
                .pause(run2ID, nameWithoutGroup(tool))
                .resume(run3ID, nameWithoutGroup(tool));
        setEnableSystemMaintenanceMode();
        runsMenu()
                .showLog(run2ID)
                .waitForDisabledButton(RESUME)
                .ensureButtonDisabled(RESUME)
                .checkButtonTooltip(RESUME, maintenanceModeTooltip)
                .shouldHaveStatus(PAUSED);
        runsMenu()
                .showLog(run3ID)
                .waitForDisabledButton(PAUSE)
                .ensureButtonDisabled(PAUSE)
                .ensureButtonDisabled(COMMIT);
        setDisableSystemMaintenanceMode();
    }

    @Test(priority = 4, dependsOnMethods = {"maintenanceModeNotification"})
    @TestCase(value = {"2423_5"})
    public void hotNodePoolInMaintenanceMode() {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setPreference(clusterInstanceHddExtraMmulti, "1", true)
                .saveIfNeeded()
                .setPreference(clusterDockerExtraMulti,  "1", true)
                .saveIfNeeded();
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        String currentDay = day.getDisplayName(FULL, getDefault());
        String nextDay = day.plus(1).getDisplayName(FULL, getDefault());
        setDisableSystemMaintenanceMode();
        clusterMenu()
                .switchToHotNodePool()
                .clickCreatePool()
                .setValue(POOL_NAME, poolName)
                .selectValue(STARTS_ON, currentDay)
                .setScheduleTime(STARTS_ON_TIME, "00:01")
                .selectValue(ENDS_ON, nextDay)
                .setScheduleTime(ENDS_ON_TIME, "23:59")
                .click(AUTOSCALED)
                .setAutoscaledParameter("Min Size", 2)
                .setAutoscaledParameter("Max Size", 4)
                .setAutoscaledParameter("Scale Up Threshold", 70)
                .setAutoscaledParameter("Scale Step", 1)
                .selectValue(INSTANCE_TYPE, defaultInstance)
                .selectValue(PRICE_TYPE, ON_DEMAND)
                .selectValue(CLOUD_REGION, defaultRegion[0])
                .setValue(DISK, "50")
                .addDockerImage(registry, group, tool)
                .selectValue(CONDITION,"Matches all filters (\"and\")")
                .addFilter("Run owner")
                .addRunOwnerFilterValue(admin.login)
                .ok()
                .waitUntilRunningNodesAppear(poolName, 2);
        launchTool();
        clusterMenu()
                .switchToHotNodePool()
                .waitUntilActiveNodesAppear(poolName, 1)
                .switchToCluster()
                .checkNodeContainsHotNodePoolsLabel(getLastRunId(), poolName);
        launchTool();
        clusterMenu()
                .switchToHotNodePool()
                .waitUntilActiveNodesAppear(poolName, 2)
                .waitUntilRunningNodesAppear(poolName, 3)
                .switchToCluster()
                .checkNodeContainsHotNodePoolsLabel(getLastRunId(), poolName);
        setEnableSystemMaintenanceMode();
        launchTool();
        clusterMenu()
                .checkNodeContainsHotNodePoolsLabel(getLastRunId(), poolName)
                .switchToHotNodePool()
                .waitUntilActiveNodesAppear(poolName, 3)
                .waitUntilRunningNodesAppear(poolName, 3);
        launchTool();
        clusterMenu()
                .checkNodeNotContainsHotNodePoolsLabel(getLastRunId(), poolName);
    }

    private SettingsPageAO.PreferencesAO setSystemMaintenanceModeBanner(String textBanner) {
        return navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem()
                .setSystemMaintenanceModeBanner(textBanner)
                .saveIfNeeded();
    }

    private SettingsPageAO.PreferencesAO setEnableSystemMaintenanceMode() {
        return navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem()
                .enableSystemMaintenanceMode()
                .saveIfNeeded();
    }

    private SettingsPageAO.PreferencesAO setDisableSystemMaintenanceMode() {
        return navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem()
                .disableSystemMaintenanceMode()
                .saveIfNeeded();
    }

    private void ensureNotificationIsAbsent(String title) {
        $(byXpath(format("//*[contains(@class, 'system-notification__container') and contains(., '%s')]",
                        title))).shouldNot(exist);
    }

    private void launchTool() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setTypeValue(defaultInstance)
                .setDisk("20")
                .selectValue(PRICE_TYPE, ON_DEMAND)
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForIP();
    }

    private RunsMenuAO launchToolOnDemand() {
        return navigationMenu()
                .tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(ON_DEMAND)
                .doNotMountStoragesSelect(true)
                .launch(this);
    }
}

/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.LogAO.Status.PAUSED;
import static com.epam.pipeline.autotests.ao.Primitive.COMMIT;
import static com.epam.pipeline.autotests.ao.Primitive.INFO;
import static com.epam.pipeline.autotests.ao.Primitive.PAUSE;
import static com.epam.pipeline.autotests.ao.Primitive.RESUME;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class MaintenanceModeTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    private final String testSystemMaintenanceModeBanner = "Test of maintenance mode. Some of the features are disabled.";
    private final String maintenanceModeTooltip = "Platform is in a maintenance mode, operation is temporary unavailable";
    private final String titleText = "Maintenance mode";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String customTag = "test_tag";

    private String defaultSystemMaintenanceModeBanner;
    private String run1ID = "";
    private String run2ID = "";
    private String run3ID = "";


    @BeforeClass(alwaysRun = true)
    public void getDefaultPreferences() {
        defaultSystemMaintenanceModeBanner = navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem()
                .getSystemMaintenanceModeBanner();
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
                .saveIfNeeded();
    }

    @Test
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

    @Test(dependsOnMethods = {"maintenanceModeNotification"})
    @TestCase(value = {"2423_2"})
    public void checkLaunchRunInMaintenanceMode() {
        navigationMenu()
                .tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                        .setPriceType("On-demand")
                .doNotMountStoragesSelect(true)
                .launch(this)
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

    @Test(dependsOnMethods = {"checkLaunchRunInMaintenanceMode"})
    @TestCase(value = {"2423_3"})
    public void checkSwitchToMaintenanceModeDuringTheRunCommittingOperation() {
        runsMenu()
                .log(run1ID, log ->
                        log.waitForCommitButton()
                                .commit(commit ->
                                        commit.setVersion(customTag)
                                              .sleep(1, SECONDS)
                                              .ok())
                );
        setEnableSystemMaintenanceMode();
        runsMenu()
                .showLog(run1ID)
                .assertCommittingFinishedSuccessfully()
                .ensureButtonDisabled(PAUSE)
                .ensureButtonDisabled(COMMIT);
    }

    @Test(dependsOnMethods = {"maintenanceModeNotification"})
    @TestCase(value = {"2423_4"})
    public void checkSwitchToMaintenanceModeDuringTheRunPausingAndResumingOperation() {
            setDisableSystemMaintenanceMode();
            navigationMenu()
                    .tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .setPriceType("On-demand")
                    .doNotMountStoragesSelect(true)
                    .launch(this);
            run2ID = getLastRunId();
            navigationMenu()
                    .tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .setPriceType("On-demand")
                    .doNotMountStoragesSelect(true)
                    .launch(this)
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
            runsMenu()
                    .resume(run2ID, nameWithoutGroup(tool))
                    .waitUntilPauseButtonAppear(run2ID);
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
        $(byXpath(format("//*[contains(@class, 'system-notification__container') and contains(., '%s')]", title)))
                .shouldNotBe(visible);
    }
}

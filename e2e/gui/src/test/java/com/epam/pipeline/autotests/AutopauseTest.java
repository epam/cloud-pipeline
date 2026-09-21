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

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.ao.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.AUTO_PAUSE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.runWithId;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.utils.Utils.SPOT;

import static java.lang.Boolean.parseBoolean;
import java.util.stream.Stream;

public class AutopauseTest extends AbstractSeveralPipelineRunningTest implements Tools, Authorization {

    private final String tool = C.GPU_TESTING_TOOL;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = "library";
    private final String instanceType = C.DEFAULT_GPU_INSTANCE;
    private final String diskSize = "30";
    private final String ROLE_ADVANCED_USER = "ROLE_ADVANCED_USER";
    private final String SYSTEM_IDLE_MONITORING_CONFIG = "system.idle.monitoring.config";
    private static final String SYSTEMIDLEMONITORINGCONFIG_JSON = "/systemIdleMonitoringConfig.json";

    private String[] systemIdleMonitoringConfigInit;
    private boolean userIsAdvancedUser;

    @BeforeClass(alwaysRun = true)
    public void getDefaultPreferences() {
        loginAsAdminAndPerform(() -> {
            systemIdleMonitoringConfigInit = navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .searchPreference(SYSTEM_IDLE_MONITORING_CONFIG)
                    .getPreference(SYSTEM_IDLE_MONITORING_CONFIG);
            EditUserPopup editUserPopup = navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchUserEntry(user.login)
                    .edit();
            userIsAdvancedUser = editUserPopup
                    .isUserHasRoleOrGroup(ROLE_ADVANCED_USER);
            editUserPopup
                    .addRoleOrGroupIfNonExist(ROLE_ADVANCED_USER)
                    .ok();
        });
    }

    @AfterClass(alwaysRun = true)
    public void fallBackPreferences() {
        setSystemPreferences(systemIdleMonitoringConfigInit[0],
                parseBoolean(systemIdleMonitoringConfigInit[1]));
        if(!userIsAdvancedUser) {
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchUserEntry(user.login)
                    .edit()
                    .deleteRoleOrGroupIfExist(ROLE_ADVANCED_USER)
                    .ok();
        }
    }

    @Test
    @TestCase({"EPMCMBIBPC-2633"})
    public void autopauseValidationStop() {
        setSystemPreferences(configJson("STOP"), true);
        relogin();

        final String run1 = launchTool(ON_DEMAND, enabled);
        final String run2 = launchTool(SPOT, hidden);
        try {
            runsMenu()
                    .activeRuns()
                    .ensure(runWithId(run2), visible)
                    .ensure(runWithId(run1), visible)
                    .waitForCompletion(run2)
                    .waitForCompletion(run1)
                    .completedRuns()
                    .ensure(runWithId(run2), visible)
                    .ensure(runWithId(run1), visible);
        } finally {
            Stream.of(run1, run2)
                    .forEach(runId -> navigationMenu().runs().stopRunIfPresent(runId));
        }
    }

    @Test
    @TestCase({"EPMCMBIBPC-2634"})
    public void autopauseValidationPauseOrStop() {
        setSystemPreferences(configJson("PAUSE_OR_STOP"), true);
        relogin();

        final String run1 = launchTool(ON_DEMAND, enabled);
        final String run2 = launchTool(SPOT, hidden);
        try {
            runsMenu()
                    .activeRuns()
                    .waitUntilResumeButtonAppear(run1)
                    .validateStatus(run1, LogAO.Status.PAUSED)
                    .waitForCompletion(run2)
                    .ensure(runWithId(run2), hidden)
                    .completedRuns()
                    .ensure(runWithId(run2), visible)
                    .activeRuns()
                    .resume(run1, nameWithoutGroup(tool))
                    .waitUntilStopButtonAppear(run1)
                    .stopRun(run1);
        } finally {
            Stream.of(run1, run2)
                    .forEach(runId -> navigationMenu().runs().stopRunIfPresent(runId));
        }

    }

    @Test
    @TestCase({"EPMCMBIBPC-2635"})
    public void autopauseValidationPause() {
        setSystemPreferences(configJson("PAUSE"), true);
        relogin();

        final String runId = launchTool(ON_DEMAND, enabled);
        try {
            runsMenu()
                    .activeRuns()
                    .checkTagIs(runId, "IDLE_GPU", visible)
                    .waitUntilResumeButtonAppear(runId)
                    .validateStatus(runId, LogAO.Status.PAUSED)
                    .resume(runId, nameWithoutGroup(tool))
                    .waitUntilStopButtonAppear(runId)
                    .stopRun(runId);
        } finally {
            navigationMenu().runs().stopRunIfPresent(runId);
        }
    }

    private String launchTool(final String priceType, final Condition autoPause) {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setLaunchOptions(diskSize, instanceType, null)
                .setPriceType(priceType)
                .ensure(AUTO_PAUSE, autoPause)
                .launchTool(this, Utils.nameWithoutGroup(tool));
        return getLastRunId();
    }

    private String configJson(final String action) {
        return Utils.readResourceFully(SYSTEMIDLEMONITORINGCONFIG_JSON)
                .replace("{{action}}", action);
    }

    private void setSystemPreferences(final String idleConfig, final boolean bool) {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .searchPreference(SYSTEM_IDLE_MONITORING_CONFIG)
                        .updateCodeText(SYSTEM_IDLE_MONITORING_CONFIG, idleConfig, bool)
                        .saveIfNeeded()
        );
    }

    private void relogin() {
        logout();
        loginAs(user);
    }
}

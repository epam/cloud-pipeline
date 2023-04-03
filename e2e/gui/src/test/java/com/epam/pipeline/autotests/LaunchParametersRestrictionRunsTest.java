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

import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.ToolSettings;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;

public class LaunchParametersRestrictionRunsTest
        extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private final String LAUNCH_MAX_RUNS_USER_GLOBAL = "launch.max.runs.user.global";
    private final String ALLOWED_INSTANCES_MAX_COUNT = "Allowed instance max count";
    private final String USER_LIMIT = "<user-contextual-limit>";
    private final String GLOBAL_LIMIT = "<user-global-limit>";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String USER_GROUP = C.ROLE_USER;
    private final String GLOBAL_MAX_RUNS = "3";
    private final String GROUP_MAX_RUNS1 = "2";
    private final String GROUP_MAX_RUNS2 = "4";
    private final String USER_MAX_RUNS = "3";
    private final String USER_GROUP2 = "TEST_GROUP_2642";
    private String[] launchMaxRunsUserGlobalInitial;
    private final String warningMessage = "You have exceeded maximum number of running jobs (%s).";
    private final String autoScaledClusterWarning = "Your cluster configuration may exceed the maximum " +
            "number of running jobs. There %s running out of %s.";
    private String launchErrorMessage = "Launch of new jobs is restricted as [%s] user " +
            "will exceed [%s] runs limit [%s]";
    private final String runToolCommand = format("pipe run -di %s:latest -y", tool);
    private final String[] runID3 = new String[2];
    private final List<String> runID5 = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    public void getPreferences() {
        launchMaxRunsUserGlobalInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(LAUNCH_MAX_RUNS_USER_GLOBAL);
        setGroupAllowedInstanceMaxCount(USER_GROUP, StringUtils.EMPTY);
        setUserAllowedInstanceMaxCount(user, StringUtils.EMPTY);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .createGroupIfNoPresent(USER_GROUP2)
                .editGroup(USER_GROUP2)
                .addUser(user)
                .ok();
        setGroupAllowedInstanceMaxCount(USER_GROUP2, StringUtils.EMPTY);
    }

    @BeforeMethod
    private void refreshPage() {
        open(C.ROOT_ADDRESS);
        logout();
        loginAs(admin);
    }

    @AfterClass(alwaysRun = true)
    public void restorePreferences() {
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setNumberPreference(LAUNCH_MAX_RUNS_USER_GLOBAL, launchMaxRunsUserGlobalInitial[0], true)
                .saveIfNeeded();
        setGroupAllowedInstanceMaxCount(USER_GROUP, StringUtils.EMPTY);
        setUserAllowedInstanceMaxCount(user, StringUtils.EMPTY);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .deleteGroupIfPresent(USER_GROUP2);
    }

    @Test(priority = 1)
    @TestCase(value = {"2642_1"})
    public void checkGlobalRestrictionCountOfRunningInstances() {
        final String message = format(launchErrorMessage, user.login, GLOBAL_LIMIT, GLOBAL_MAX_RUNS);
        final List<String> runIDs = new ArrayList<>();
        try {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setNumberPreference(LAUNCH_MAX_RUNS_USER_GLOBAL, GLOBAL_MAX_RUNS, true)
                    .saveIfNeeded();
            logout();
            loginAs(user);
            runIDs.addAll(launchSeveralRuns(parseInt(GLOBAL_MAX_RUNS)));
            launchToolWithError(GLOBAL_MAX_RUNS, format(warningMessage, GLOBAL_MAX_RUNS), message);

            logout();
            loginAs(admin);
            runIDs.addAll(launchSeveralRuns(parseInt(GLOBAL_MAX_RUNS)));
            tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .ensure(byText(format(warningMessage, GLOBAL_MAX_RUNS)), not(visible))
                    .checkLaunchMessage("message",
                            format(warningMessage, GLOBAL_MAX_RUNS), false)
                    .launch(this);
            runIDs.add(getLastRunId());
        } finally {
            logout();
            loginAs(admin);
            runIDs.forEach(runID -> runsMenu().viewAvailableActiveRuns().stopRun(runID));
        }
    }

    @Test(priority = 1, dependsOnMethods = "checkGlobalRestrictionCountOfRunningInstances")
    @TestCase(value = {"2642_2"})
    public void checkRunningInstancesRestrictionAppliedToGroup() {
        final List<String> runIDs = new ArrayList<>();
        try {
            setGroupAllowedInstanceMaxCount(USER_GROUP, GROUP_MAX_RUNS1);
            logout();
            loginAs(user);
            runIDs.addAll(launchSeveralRuns(parseInt(GROUP_MAX_RUNS1)));
            launchToolWithError(GROUP_MAX_RUNS1, format(warningMessage, GROUP_MAX_RUNS1),
                    format(launchErrorMessage, user.login, USER_GROUP, GROUP_MAX_RUNS1));
            logout();
            loginAs(admin);
            setGroupAllowedInstanceMaxCount(USER_GROUP, GROUP_MAX_RUNS2);
            logout();
            loginAs(user);
            runIDs.addAll(launchSeveralRuns(parseInt(GROUP_MAX_RUNS1)));
            launchToolWithError(GROUP_MAX_RUNS1, format(warningMessage, GROUP_MAX_RUNS2),
                    format(launchErrorMessage, user.login, USER_GROUP, GROUP_MAX_RUNS2));
        } finally {
            logout();
            loginAs(admin);
            runIDs.forEach(runID -> runsMenu().viewAvailableActiveRuns().stopRun(runID));
        }
    }

    @Test(priority = 1, dependsOnMethods = "checkRunningInstancesRestrictionAppliedToGroup")
    @TestCase(value = {"2642_3"})
    public void checkSimultaneousApplyingTwoGroupLevelRunningInstancesRestrictions() {
        setGroupAllowedInstanceMaxCount(USER_GROUP2, GROUP_MAX_RUNS1);
        logout();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(ON_DEMAND)
                .doNotMountStoragesSelect(true)
                .launch(this);
        runID3[0] = getLastRunId();
        tools().perform(registry, group, tool, tool -> tool.run(this));
        runID3[1] = getLastRunId();
        launchToolWithError(GROUP_MAX_RUNS1, format(warningMessage, GROUP_MAX_RUNS1),
                    format(launchErrorMessage, user.login, USER_GROUP2, GROUP_MAX_RUNS1));
        runsMenu()
                .showLog(runID3[0])
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runID3[0])
                        .execute(runToolCommand)
                        .assertPageAfterCommandContainsStrings(runToolCommand,
                                format(launchErrorMessage, user.login, USER_GROUP2, GROUP_MAX_RUNS1))
                        .execute("pipe users instances")
                        .assertPageContains(
                                format("Active runs detected for a user: [%s: %s]", user.login, GROUP_MAX_RUNS1))
                        .assertPageContains(
                                format("The following restriction applied on runs launching: [%s: %s]", USER_GROUP2,
                                        GROUP_MAX_RUNS1))
                        .close());
        runsMenu()
                .pause(runID3[0], nameWithoutGroup(tool))
                .waitUntilResumeButtonAppear(runID3[0])
                .stopRun(runID3[1]);
    }

    @Test(priority = 1, dependsOnMethods = "checkSimultaneousApplyingTwoGroupLevelRunningInstancesRestrictions")
    @TestCase(value = {"2642_4"})
    public void checkRunningInstancesRestrictionAppliedToUser() {
        final List<String> runIDs = new ArrayList<>();
        try {
            setUserAllowedInstanceMaxCount(user, USER_MAX_RUNS);
            logout();
            loginAs(user);
            runIDs.addAll(launchSeveralRuns(parseInt(USER_MAX_RUNS)));
            launchToolWithError(USER_MAX_RUNS, format(warningMessage, USER_MAX_RUNS),
                        format(launchErrorMessage, user.login, USER_LIMIT, USER_MAX_RUNS));
            runsMenu()
                    .viewAvailableActiveRuns()
                    .resume(runID3[0], nameWithoutGroup(tool))
                    .messageShouldAppear(format(launchErrorMessage, user.login, USER_LIMIT, USER_MAX_RUNS))
                    .completedRuns()
                    .rerun(runID3[1])
                    .expandTab(EXEC_ENVIRONMENT)
                    .ensure(byText(format(warningMessage, USER_MAX_RUNS)), visible)
                    .checkLaunchMessage("message",
                            format(warningMessage, USER_MAX_RUNS), true)
                    .launchWithError(format(launchErrorMessage, user.login, USER_LIMIT, USER_MAX_RUNS));
            runsMenu()
                    .showLog(runIDs.get(0))
                    .waitForSshLink()
                    .ssh(shell -> shell
                            .waitUntilTextAppears(runIDs.get(0))
                            .execute(runToolCommand)
                            .assertPageAfterCommandContainsStrings(runToolCommand,
                                    format(launchErrorMessage, user.login, USER_LIMIT, USER_MAX_RUNS))
                            .execute("pipe users instances --verbose")
                            .assertPageContainsString("The following restrictions applied on runs launching:")
                            .assertPageContains(USER_GROUP, GROUP_MAX_RUNS2)
                            .assertPageContains(USER_GROUP2, GROUP_MAX_RUNS1)
                            .assertPageContains(GLOBAL_LIMIT, GLOBAL_MAX_RUNS)
                            .assertPageContains(USER_LIMIT, USER_MAX_RUNS)
                            .close());
        } finally {
            refreshPage();
            final RunsMenuAO runsMenuAO = runsMenu();
            if (runsMenuAO.isActiveRun(runID3[0])) {
                runsMenuAO
                        .terminateRun(runID3[0], format("pipeline-%s", runID3[0]));
            }
            runIDs.forEach(runID -> runsMenuAO.viewAvailableActiveRuns().stopRun(runID));
        }
    }

    @Test(priority = 2)
    @TestCase(value = {"2642_5"})
    public void checkRunningInstancesRestrictionForClusterRun() {
        setUserAllowedInstanceMaxCount(user, USER_MAX_RUNS);
        logout();
        loginAs(user);
        runID5.addAll(launchSeveralRuns(1));
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .enableClusterLaunch()
                .clusterSettingsForm("Cluster")
                .setWorkingNodesCount("2")
                .checkWarningMessageExist(format(warningMessage, USER_MAX_RUNS))
                .ok()
                .doNotMountStoragesSelect(true)
                .ensure(byText(format(warningMessage, USER_MAX_RUNS)), visible)
                .checkLaunchMessage("message",
                            format(warningMessage, USER_MAX_RUNS), true)
                .launchWithError(format(launchErrorMessage, user.login, USER_LIMIT, USER_MAX_RUNS));
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .enableClusterLaunch()
                .clusterSettingsForm("Cluster")
                .setWorkingNodesCount("1")
                .checkWarningMessageNotExist()
                .ok()
                .doNotMountStoragesSelect(true)
                .launch(this);
        runID5.add(getLastRunId());
        runsMenu()
                .showLog(runID5.get(0))
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runID5.get(0))
                        .execute("pipe users instances")
                        .assertPageContains(
                                format("Active runs detected for a user: [%s: %s]", user.login, USER_MAX_RUNS))
                        .close());
    }

    @Test(priority = 2, dependsOnMethods = "checkRunningInstancesRestrictionForClusterRun")
    @TestCase(value = {"2642_6"})
    public void checkRunningInstancesRestrictionForLaunchToolWithConfiguredClusterRun() {
        try {
            tools()
                    .performWithin(registry, group, tool, tool ->
                            tool.settings()
                                    .enableClusterLaunch()
                                    .clusterSettingsForm("Cluster")
                                    .setWorkingNodesCount("2")
                                    .ok()
                                    .performIf(SAVE, enabled, ToolSettings::save)
                    );
            logout();
            loginAs(user);
            launchToolWithError(USER_MAX_RUNS, format(warningMessage, USER_MAX_RUNS),
                    format(launchErrorMessage, user.login, USER_LIMIT, USER_MAX_RUNS));
        } finally {
            refreshPage();
            tools()
                    .performWithin(registry, group, tool, tool ->
                            tool.settings()
                                    .enableClusterLaunch()
                                    .clusterSettingsForm("Single node")
                                    .ok()
                                    .performIf(SAVE, enabled, ToolSettings::save)
                    );
            runID5.forEach(runID -> runsMenu().viewAvailableActiveRuns().stopRun(runID));
        }
    }

    @Test(priority = 3)
    @TestCase(value = {"2642_7"})
    public void checkRunningInstancesRestrictionForAutoScaledClusterRun() {
        setUserAllowedInstanceMaxCount(user, USER_MAX_RUNS);
        logout();
        loginAs(user);
        launchSeveralRuns(1);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .enableClusterLaunch()
                .clusterSettingsForm("Auto-scaled cluster")
                .setWorkingNodesCount("3")
                .checkWarningMessageExist(format(autoScaledClusterWarning, "is 1 job", USER_MAX_RUNS))
                .setDefaultChildNodes("2")
                .checkWarningMessageExist(format(warningMessage, USER_MAX_RUNS))
                .setDefaultChildNodes("1")
                .checkWarningMessageExist(format(autoScaledClusterWarning, "is 1 job", USER_MAX_RUNS))
                .ok()
                .doNotMountStoragesSelect(true)
                .ensure(byText(format(autoScaledClusterWarning, "is 1 job", USER_MAX_RUNS)), visible)
                .checkLaunchMessage("message",
                        format(autoScaledClusterWarning, "is 1 job", USER_MAX_RUNS), true)
                .launch(this)
                .shouldContainRun("pipeline", getLastRunId())
                .openClusterRuns(getLastRunId())
                .shouldContainRunsWithParentRun(1, getLastRunId())
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute("pipe users instances")
                        .assertPageContains(
                                format("Active runs detected for a user: [%s: %s]", user.login, USER_MAX_RUNS))
                        .close());
    }

    private List<String> launchSeveralRuns(int count) {
        final List<String> runIDs = new ArrayList<>();
        IntStream.range(0, count)
                .forEach(i -> {
                    tools().perform(registry, group, tool, tool ->
                            tool.run(this));
                    runIDs.add(getLastRunId());
                });
        return runIDs;
    }

    private void launchToolWithError(String count, String formMessage, String launchMessage) {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .ensure(byText(format(formMessage, count)), visible)
                .checkLaunchMessage("message", format(formMessage, count), true)
                .launchWithError(launchMessage);
    }

    private void setGroupAllowedInstanceMaxCount(String group, String value) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .editGroup(group)
                .ensure(byText(ALLOWED_INSTANCES_MAX_COUNT), visible, enabled)
                .addAllowedInstanceMaxCount(value)
                .ok();
    }

    private void setUserAllowedInstanceMaxCount(Account userName, String value) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(userName.login)
                .edit()
                .ensure(byText(ALLOWED_INSTANCES_MAX_COUNT), visible, enabled)
                .addAllowedInstanceMaxCount(value)
                .ok();
    }
}

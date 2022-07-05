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

import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

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
    private String errorMessage = "You have exceeded maximum number of running jobs (%s).";
    private String launchErrorMessage = "Launch of new jobs is restricted as [%s] user " +
            "will exceed [%s] runs limit [%s]";
    private String[] runID3 = new String[2];


    @BeforeClass(alwaysRun = true)
    public void getPreferences() {
        launchMaxRunsUserGlobalInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(LAUNCH_MAX_RUNS_USER_GLOBAL);
        setGroupAllowedInstanceMaxCount(USER_GROUP, "");
        setUserAllowedInstanceMaxCount(user, "");
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .createGroupIfNoPresent(USER_GROUP2)
                .editGroup(USER_GROUP2)
                .addUser(user)
                .ok();
        setGroupAllowedInstanceMaxCount(USER_GROUP2, "");
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
        setGroupAllowedInstanceMaxCount(USER_GROUP, "");
        setUserAllowedInstanceMaxCount(user, "");
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .deleteGroupIfPresent(USER_GROUP2);
    }

    @Test
    @TestCase(value = {"2642_1"})
    public void checkGlobalRestrictionCountOfRunningInstances() {
        String message = format(launchErrorMessage, user.login, GLOBAL_LIMIT, GLOBAL_MAX_RUNS);
        List<String> runIDs = new ArrayList<>();
        try {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .setNumberPreference(LAUNCH_MAX_RUNS_USER_GLOBAL, GLOBAL_MAX_RUNS, true)
                    .saveIfNeeded();
            logout();
            loginAs(user);
            runIDs.addAll(launchSeveralRuns(parseInt(GLOBAL_MAX_RUNS)));
            launchToolWithError(GLOBAL_MAX_RUNS, format(errorMessage, GLOBAL_MAX_RUNS), message);

            logout();
            loginAs(admin);
            runIDs.addAll(launchSeveralRuns(parseInt(GLOBAL_MAX_RUNS)));
            tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .ensure(byText(format(errorMessage, GLOBAL_MAX_RUNS)), not(visible))
                    .checkLaunchMessage("message",
                            format(errorMessage, GLOBAL_MAX_RUNS), false)
                    .launch(this);
            runIDs.add(getLastRunId());
        } finally {
            logout();
            loginAs(admin);
            for (String runID : runIDs) {
                      runsMenu().viewAvailableActiveRuns().stopRun(runID);
            }
        }
    }

    @Test(dependsOnMethods = "checkGlobalRestrictionCountOfRunningInstances")
    @TestCase(value = {"2642_2"})
    public void checkRunningInstancesRestrictionAppliedToGroup() {
        List<String> runIDs = new ArrayList<>();
        try {
            setGroupAllowedInstanceMaxCount(USER_GROUP, GROUP_MAX_RUNS1);
            logout();
            loginAs(user);
            runIDs.addAll(launchSeveralRuns(parseInt(GROUP_MAX_RUNS1)));
            launchToolWithError(GROUP_MAX_RUNS1, format(errorMessage, GROUP_MAX_RUNS1),
                    format(launchErrorMessage, user.login, USER_GROUP, GROUP_MAX_RUNS1));
            logout();
            loginAs(admin);
            setGroupAllowedInstanceMaxCount(USER_GROUP, GROUP_MAX_RUNS2);
            logout();
            loginAs(user);
            runIDs.addAll(launchSeveralRuns(parseInt(GROUP_MAX_RUNS1)));
            launchToolWithError(GROUP_MAX_RUNS1, format(errorMessage, GROUP_MAX_RUNS2),
                    format(launchErrorMessage, user.login, USER_GROUP, GROUP_MAX_RUNS2));
        } finally {
            logout();
            loginAs(admin);
            for (String runID : runIDs) {
                runsMenu().viewAvailableActiveRuns().stopRun(runID);
            }
        }
    }

    @Test(dependsOnMethods = "checkRunningInstancesRestrictionAppliedToGroup")
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
        tools().perform(registry, group, tool, tool ->
                tool.run(this));
        runID3[1] = getLastRunId();
        launchToolWithError(GROUP_MAX_RUNS1, format(errorMessage, GROUP_MAX_RUNS1),
                    format(launchErrorMessage, user.login, USER_GROUP2, GROUP_MAX_RUNS1));
        runsMenu()
                .showLog(runID3[0])
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runID3[0])
                        .execute(format("pipe run -di %s:latest -y", tool))
                        .assertPageContainsString(format(launchErrorMessage, user.login,
                                    USER_GROUP2, GROUP_MAX_RUNS1))
                        .execute("pipe users instances")
                        .assertPageContains(format("Active runs detected for a user: [%s: %s]", user.login, GROUP_MAX_RUNS1))
                        .assertPageContains(format("The following restriction applied on runs launching: [%s: %s]", USER_GROUP2, GROUP_MAX_RUNS1))
                        .close());
        runsMenu()
                .pause(runID3[0], nameWithoutGroup(tool))
                .waitUntilResumeButtonAppear(runID3[0])
                .stopRun(runID3[1]);
    }

    @Test(dependsOnMethods = "checkSimultaneousApplyingTwoGroupLevelRunningInstancesRestrictions")
    @TestCase(value = {"2642_4"})
    public void checkRunningInstancesRestrictionAppliedToUser() {
        List<String> runIDs = new ArrayList<>();
        try {
            setUserAllowedInstanceMaxCount(user, USER_MAX_RUNS);
            logout();
            loginAs(user);
            runIDs.addAll(launchSeveralRuns(parseInt(USER_MAX_RUNS)));
            launchToolWithError(USER_MAX_RUNS, format(errorMessage, USER_MAX_RUNS),
                        format(launchErrorMessage, user.login, USER_LIMIT, USER_MAX_RUNS));
            runsMenu()
                    .viewAvailableActiveRuns()
                    .resume(runID3[0], nameWithoutGroup(tool))
                    .messageShouldAppear(format(launchErrorMessage, user.login,
                            USER_LIMIT, USER_MAX_RUNS))
                    .completedRuns()
                    .rerun(runID3[1], nameWithoutGroup(tool))
                    .expandTab(EXEC_ENVIRONMENT)
                    .ensure(byText(format(format(errorMessage, USER_MAX_RUNS), USER_MAX_RUNS)), visible)
                    .checkLaunchMessage("message",
                            format(format(errorMessage, USER_MAX_RUNS), USER_MAX_RUNS), true)
                    .launchWithError(format(launchErrorMessage, user.login, USER_LIMIT, USER_MAX_RUNS));
            runsMenu()
                    .showLog(runIDs.get(0))
                    .waitForSshLink()
                    .ssh(shell -> shell
                            .waitUntilTextAppears(runIDs.get(0))
                            .execute(format("pipe run -di %s:latest -y", tool))
                            .assertPageContainsString(format(launchErrorMessage, user.login,
                                    USER_LIMIT, USER_MAX_RUNS))
                            .execute("pipe users instances --verbose")
                            .assertPageContainsString("The following restrictions applied on runs launching:")
                            .assertPageContains(USER_GROUP, GROUP_MAX_RUNS2)
                            .assertPageContains(USER_GROUP2, GROUP_MAX_RUNS1)
                            .assertPageContains(GLOBAL_LIMIT, GLOBAL_MAX_RUNS)
                            .assertPageContains(USER_LIMIT, USER_MAX_RUNS)
                            .close());
        } finally {
            logout();
            loginAs(admin);
            final RunsMenuAO runsMenuAO = runsMenu();
            if (runsMenuAO.isActiveRun(runID3[0])) {
                runsMenuAO
                        .terminateRun(runID3[0], format("pipeline-%s", runID3[0]));
            }
            for (String runID : runIDs) {
                runsMenuAO.viewAvailableActiveRuns().stopRun(runID);
            }
        }
    }

    private List<String> launchSeveralRuns(int count) {
        List<String> runIDs = new ArrayList<>();
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
                .sleep(3, SECONDS)
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
                .sleep(3, SECONDS)
                .ok();
    }
}

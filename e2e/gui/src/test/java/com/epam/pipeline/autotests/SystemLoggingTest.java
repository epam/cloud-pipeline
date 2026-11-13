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

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.SystemManagementAO.SystemLogsAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDateTime;

import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;

public class SystemLoggingTest extends AbstractSeveralPipelineRunningTest implements Authorization, Navigation, Tools {

    private static final String TYPE = "security";
    private static final String USER_NAME = "TEST_SYSTEM_USER_NAME";
    private static final String USER_FILTER = "User";
    private static final String ROLE_USER_ID = "2";
    private static final int RECENT_ROW_MAX_AGE_SECONDS = 1800;
    private static final int RECENT_ROW_TIMEOUT_SECONDS = 600;
    private static final int CLOCK_MARGIN_SECONDS = 60;
    private final String pipeline = "SystemLogging" + Utils.randomSuffix();
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String endpoint = C.VALID_ENDPOINT;

    @BeforeClass
    public void prerequisites() {
        navigationMenu()
                .library()
                .createPipeline(pipeline);
    }

    @AfterClass(alwaysRun = true)
    public void removePipelines() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .removePipelineIfExists(pipeline);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3162"})
    public void userAuthentication() {
        final LocalDateTime since = testStart();
        logoutIfNeeded();
        loginAs(admin);
        impersonateAs(user.login);
        sleep(30, SECONDS);
        stopImpersonation();
        sleep(1, MINUTES);
        final SystemLogsAO systemLogsAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs();
        sleep(20, SECONDS);
        systemLogsAO.filterByMessage("impersonation");
        final SelenideElement userInfo = systemLogsAO.getInfoRowLoggedSince(
                format("Successful impersonation action: START, user: %s", user.login.toUpperCase()),
                admin.login, TYPE, since, RECENT_ROW_TIMEOUT_SECONDS);
        userInfo.findAll("td").get(3).shouldHave(exactText(admin.login));
        // a row is found by its position in the table, and the next search reloads the table
        final LocalDateTime userInfoTime = loggedAt(userInfo);
        final LocalDateTime adminInfoTime = loggedAt(systemLogsAO.getInfoRowLoggedSince(
                format("Successful impersonation action: STOP, user: %s", admin.login.toUpperCase()),
                admin.login, TYPE, since, RECENT_ROW_TIMEOUT_SECONDS));
        assertTrue(adminInfoTime.isAfter(userInfoTime),
                format("The impersonation stop (%s) is not later than its start (%s)", adminInfoTime, userInfoTime));
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3163"})
    public void userAuthenticationUnsuccessfulAttempt() {
        final LocalDateTime since = testStart();
        try {
            logoutIfNeeded();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchForUserEntry(userWithoutCompletedRuns.login)
                    .edit()
                    .blockUser(userWithoutCompletedRuns.login)
                    .ok();
            impersonateAs(userWithoutCompletedRuns.login);
            navigationMenu()
                    .settings()
                    .switchToMyProfile()
                    .validateUserName(admin.login);
            final SystemLogsAO systemLogsAO = navigationMenu()
                    .settings()
                    .switchToSystemManagement()
                    .switchToSystemLogs()
                    .filterByField(USER_FILTER, admin.login)
                    .filterByMessage("blocked");
            validateRowLoggedSince(systemLogsAO, format("Authentication failed! User %s is blocked!",
                    userWithoutCompletedRuns.login), admin.login, since);
            validateRowLoggedSince(systemLogsAO, format("Failed impersonation action: START, message: User: %s is blocked!",
                    userWithoutCompletedRuns.login), admin.login, since);
        } finally {
            logoutIfNeeded();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchForUserEntry(userWithoutCompletedRuns.login)
                    .edit()
                    .unblockUser(userWithoutCompletedRuns.login)
                    .ok();
        }
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3164"})
    public void userProfileModifications() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(userWithoutCompletedRuns.login)
                .edit()
                .blockUser(userWithoutCompletedRuns.login.toUpperCase())
                .unblockUser(userWithoutCompletedRuns.login.toUpperCase())
                .ok();
        final SystemLogsAO systemLogsAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs()
                .filterByField(USER_FILTER, admin.login)
                .pressEnter()
                .filterByMessage("Blocking status=false");

        final SelenideElement blockingInfoRow = systemLogsAO.getRecentInfoRow("Blocking status=false",
                admin.login, TYPE, RECENT_ROW_MAX_AGE_SECONDS, RECENT_ROW_TIMEOUT_SECONDS);
        final String userId = systemLogsAO.getUserId(blockingInfoRow);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(userWithoutCompletedRuns.login)
                .edit()
                .sleep(1, SECONDS)
                .deleteRoleOrGroup(C.ROLE_USER)
                .ok();
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(userWithoutCompletedRuns.login)
                .edit()
                .addRoleOrGroup(C.ROLE_USER)
                .sleep(2, SECONDS)
                .ok();
        final String roleId = "ROLE_USER".equals(C.ROLE_USER) ? ROLE_USER_ID : "[0-9]+";
        final SystemLogsAO roleLogsAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs()
                .filterByField(USER_FILTER, admin.login)
                .filterByMessage(format("id=%s", userId));
        validateRecentRow(roleLogsAO, format("Assing role. RoleId=%s UserIds=%s\\b", roleId, userId),
                admin.login.toUpperCase());
        validateRecentRow(roleLogsAO, format("Unassing role. RoleId=%s UserIds=%s\\b", roleId, userId),
                admin.login.toUpperCase());
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3165"})
    public void permissionsManagement() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .library()
                .clickOnPipeline(pipeline)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(userWithoutCompletedRuns.login)
                .closeAll();
        givePermissions(userWithoutCompletedRuns,
                PipelinePermission.allow(READ, pipeline),
                PipelinePermission.allow(EXECUTE, pipeline),
                PipelinePermission.deny(WRITE, pipeline));
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs()
                .filterByField(USER_FILTER, admin.login)
                .filterByMessage("Granting permissions")
                .validateRow(format(".*Granting permissions. Entity: class=PIPELINE id=[0-9]+, name=%s, permission: " +
                                "\\(mask: 0\\). Sid: name=%s isPrincipal=true.*", pipeline, userWithoutCompletedRuns.login),
                        admin.login, TYPE)
                .validateRow(format(".*Granting permissions. Entity: class=PIPELINE id=[0-9]+, name=%s, permission: " +
                                "READ,NO_WRITE,EXECUTE \\(mask: 25\\). Sid: name=%s isPrincipal=true.*", pipeline,
                        userWithoutCompletedRuns.login),
                        admin.login, TYPE);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3166"})
    public void interactiveEndpointsAccess() {
        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .sleep(2, SECONDS)
                .setDefaultLaunchOptions()
                .launchTool(this, nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForEndpointLink()
                .clickOnEndpointLink()
                .sleep(30, SECONDS)
                .closeTab();
        runsMenu()
                .activeRuns()
                .showLog(getLastRunId())
                .waitForSshLink()
                .sshInNewTab(shell -> shell.assertPageContains(format("pipeline-%s", getLastRunId())));
        open(C.ROOT_ADDRESS);
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs()
                .filterByField(USER_FILTER, user.login.toUpperCase())
                .filterByField("Service", "edge")
                .validateRow(format(
                        ".*\\[SECURITY\\] Application: /pipeline-%s-%s-0/; User: %s; Status: Successfully authenticated",
                        getLastRunId(), endpoint, user.login), user.login, TYPE)
                .validateRow(format(
                        ".*\\[SECURITY\\] Application: SSH-/ssh/pipeline/%s; User: %s; Status: Successfully authenticated",
                        getLastRunId(), user.login), user.login, TYPE);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3167"})
    public void createAndDeleteUser() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .createUser(USER_NAME)
                .searchForUserEntry(USER_NAME)
                .edit()
                .deleteUser(USER_NAME);
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs()
                .sleep(2, SECONDS)
                .filterByMessage(USER_NAME)
                .validateRow(format("Create user with name: %s", USER_NAME), admin.login, TYPE)
                .validateRow(format("Delete user with name: %s", USER_NAME), admin.login, TYPE);
    }

    // earlier runs log the same messages for the same users, so only a recent row proves this run's action
    private void validateRecentRow(final SystemLogsAO systemLogsAO, final String message, final String user) {
        systemLogsAO.getRecentInfoRow(message, user, TYPE, RECENT_ROW_MAX_AGE_SECONDS, RECENT_ROW_TIMEOUT_SECONDS)
                .findAll("td").get(3).shouldHave(text(user));
    }

    // earlier runs and other test classes log the same messages for the same users
    private void validateRowLoggedSince(final SystemLogsAO systemLogsAO, final String message, final String user,
                                        final LocalDateTime since) {
        systemLogsAO.getInfoRowLoggedSince(message, user, TYPE, since, RECENT_ROW_TIMEOUT_SECONDS)
                .findAll("td").get(3).shouldHave(text(user));
    }

    // the clocks of the test host and of the platform may differ a little
    private static LocalDateTime testStart() {
        return LocalDateTime.now().minusSeconds(CLOCK_MARGIN_SECONDS);
    }

    private static LocalDateTime loggedAt(final SelenideElement row) {
        return Utils.validateDateTimeString(row.findAll("td").get(0).getText());
    }
}

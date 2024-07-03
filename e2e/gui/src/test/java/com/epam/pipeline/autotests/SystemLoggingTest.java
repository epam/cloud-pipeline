/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.ShellAO;
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

import java.util.Collections;
import java.util.NoSuchElementException;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SystemLoggingTest extends AbstractSeveralPipelineRunningTest implements Authorization, Navigation, Tools {

    private static final String TYPE = "security";
    private static final String USER_NAME = "TEST_SYSTEM_USER_NAME";
    private static final String USER = "User";
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
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .deleteUserIfExist(USER_NAME);
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
        final String startAction = "START";
        final String stopAction = "STOP";
        logoutIfNeeded();
        loginAs(user);
        sleep(30, SECONDS);
        logout();
        loginAs(admin);
        sleep(30, SECONDS);
        SystemLogsAO systemLogsAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs();
        SelenideElement adminInfo;
        SelenideElement userInfo;
        try {
            if (impersonateMode()) {
                systemLogsAO.filterByMessage("impersonation");
            }
            adminInfo = getInfo(systemLogsAO, format("Successfully authenticate user: %s", admin.login.toUpperCase()),
                    stopAction, admin);
            userInfo = getInfo(systemLogsAO, format("Successfully authenticate user: %s", user.login.toUpperCase()), startAction,
                    user);
        } catch (NoSuchElementException e) {
            adminInfo = getInfo(systemLogsAO, format("Successfully authenticate user .*: %s", admin.login.toUpperCase()),
                    stopAction, admin);
            userInfo = getInfo(systemLogsAO, format("Successfully authenticate user .*: %s", user.login.toUpperCase()), startAction,
                    user);
        }
        systemLogsAO.validateTimeOrder(adminInfo, userInfo);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3163"})
    public void userAuthenticationUnsuccessfulAttempt() {
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
            logout();
            loginAs(userWithoutCompletedRuns);
            if (impersonateMode()) {
                navigationMenu()
                        .settings()
                        .switchToMyProfile()
                        .validateUserName(admin.login);
            } else {
                if ("false".equals(C.AUTH_TOKEN)) {
                    validateErrorPage(Collections.singletonList(format("%s was not able to authorize you",
                            C.PLATFORM_NAME)));
                    loginBack();
                    return;
                }
                validateErrorPage(Collections.singletonList("User is blocked!"));
                Selenide.clearBrowserCookies();
                sleep(10, SECONDS);
            }
            loginAs(admin);
            SystemLogsAO systemLogsAO = navigationMenu()
                    .settings()
                    .switchToSystemManagement()
                    .switchToSystemLogs();
            if (impersonateMode()) {
                systemLogsAO
                        .filterByField(USER, admin.login)
                        .validateRow(format("Authentication failed! User %s is blocked!",
                                userWithoutCompletedRuns.login), admin.login, TYPE)
                        .validateRow(format("Failed impersonation action: START, message: User: %s is blocked!",
                                userWithoutCompletedRuns.login), admin.login, TYPE);
                return;
            }
            systemLogsAO
                    .filterByField(USER, userWithoutCompletedRuns.login)
                    .validateRow(format("Authentication failed! User %s is blocked!", userWithoutCompletedRuns.login),
                            admin.login, TYPE);
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
                .filterByField(USER, admin.login)
                .pressEnter()
                .filterByMessage("Blocking status=false");

        final SelenideElement blockingInfoRow = systemLogsAO
                .getInfoRow("Blocking status=false", admin.login, TYPE);
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
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs()
                .filterByField(USER, admin.login)
                .filterByMessage(format("id=%s", userId))
                .validateRow(format("Assing role. RoleId=[0-9]+ UserIds=%s", userId), admin.login.toUpperCase(), TYPE)
                .validateRow(format("Unassing role. RoleId=[0-9]+ UserIds=%s", userId), admin.login.toUpperCase(), TYPE);
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
                .filterByField(USER, admin.login)
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
        final String sshLink =
                runsMenu()
                        .activeRuns()
                        .showLog(getLastRunId())
                        .waitForSshLink()
                        .getSshLink();
        ShellAO.open(sshLink)
                .assertPageContains(format("pipeline-%s", getLastRunId()));
        open(C.ROOT_ADDRESS);
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToSystemLogs()
                .filterByField(USER, user.login.toUpperCase())
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

    private SelenideElement getInfo(final SystemLogsAO systemLogsAO,
                                    final String message,
                                    final String action,
                                    final Account account) {

        clearFiltersIfNeeded(systemLogsAO);
        return impersonateMode()
                ? systemLogsAO.getInfoRow(
                        format("Successful impersonation action: %s, user: %s", action, account.login.toUpperCase()), account.login.toUpperCase(),
                        TYPE)
                : systemLogsAO.filterByField(USER, account.login.toUpperCase()).getInfoRow(message, account.login, TYPE);
    }

    private void clearFiltersIfNeeded(final SystemLogsAO systemLogsAO) {
        if (impersonateMode()) {
            return;
        }
        systemLogsAO.clearFiltersBy(USER);
    }
}

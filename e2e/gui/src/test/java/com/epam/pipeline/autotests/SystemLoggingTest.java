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

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.ShellAO;
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

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SystemLoggingTest extends AbstractSeveralPipelineRunningTest implements Authorization, Navigation, Tools {

    private static final String TYPE = "security";
    private static final String USER_NAME = "TEST_SYSTEM_USER_NAME";

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
        logoutIfNeeded();
        loginAs(user);
        logout();
        loginAs(admin);
        SettingsPageAO.SystemLogsAO systemLogsAO = navigationMenu()
                .settings()
                .switchToSystemLogs();
        SelenideElement adminInfo = systemLogsAO.getInfoRow(format("Successfully authenticate user: %s", admin.login),
                admin.login, TYPE);
        SelenideElement userInfo = systemLogsAO.getInfoRow(format("Successfully authenticate user: %s", user.login),
                user.login, TYPE);
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
                    .searchForUserEntry(userWithoutCompletedRuns.login.toUpperCase())
                    .edit()
                    .blockUser(userWithoutCompletedRuns.login.toUpperCase())
                    .ok();
            logout();
            loginAs(userWithoutCompletedRuns);
            validateErrorPage(format("%s was not able to authorize you", C.PLATFORM_NAME));
            loginBack();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToSystemLogs()
                    .filterByUser(userWithoutCompletedRuns.login.toUpperCase())
                    .validateRow(format("Authentication failed! User %s is blocked!", userWithoutCompletedRuns.login),
                            userWithoutCompletedRuns.login, TYPE);
        } finally {
            logoutIfNeeded();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchForUserEntry(userWithoutCompletedRuns.login.toUpperCase())
                    .edit()
                    .unblockUser(userWithoutCompletedRuns.login.toUpperCase())
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
                .searchForUserEntry(userWithoutCompletedRuns.login.toUpperCase())
                .edit()
                .blockUser(userWithoutCompletedRuns.login.toUpperCase())
                .unblockUser(userWithoutCompletedRuns.login.toUpperCase())
                .ok();
        final SettingsPageAO.SystemLogsAO systemLogsAO = navigationMenu()
                .settings()
                .switchToSystemLogs()
                .filterByUser(admin.login)
                .pressEnter()
                .filterByMessage("Blocking status=false");
        final SelenideElement blockingInfoRow = systemLogsAO
                .getInfoRow("Blocking status=false", admin.login, TYPE);
        final String userId = systemLogsAO.getUserId(blockingInfoRow);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(userWithoutCompletedRuns.login.toUpperCase())
                .edit()
                .sleep(1, SECONDS)
                .deleteRoleOrGroup("ROLE_USER")
                .ok();
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(userWithoutCompletedRuns.login.toUpperCase())
                .edit()
                .addRoleOrGroup("ROLE_USER")
                .sleep(2, SECONDS)
                .ok();
        navigationMenu()
                .settings()
                .switchToSystemLogs()
                .filterByUser(admin.login)
                .filterByMessage(format("id=%s", userId))
                .validateRow(format("Assing role. RoleId=2 UserIds=%s", userId), admin.login, TYPE)
                .validateRow(format("Unassing role. RoleId=2 UserIds=%s", userId), admin.login, TYPE);
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
                .switchToSystemLogs()
                .filterByUser(admin.login)
                .filterByMessage("Granting permissions")
                .validateRow(format(".*Granting permissions. Entity: class=PIPELINE id=[0-9]+, name=%s, permission: " +
                                "\\(mask: 0\\). Sid: name=%s isPrincipal=true", pipeline, userWithoutCompletedRuns.login),
                        admin.login, TYPE)
                .validateRow(format(".*Granting permissions. Entity: class=PIPELINE id=[0-9]+, name=%s, permission: " +
                                "READ,NO_WRITE,EXECUTE \\(mask: 25\\). Sid: name=%s isPrincipal=true", pipeline,
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
                .switchToSystemLogs()
                .filterByUser(user.login.toUpperCase())
                .filterByService("edge")
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
                .switchToSystemLogs()
                .filterByMessage(USER_NAME)
                .validateRow(format("Create user with name: %s", USER_NAME), admin.login, TYPE)
                .validateRow(format("Delete user with name: %s", USER_NAME), admin.login, TYPE);
    }
}

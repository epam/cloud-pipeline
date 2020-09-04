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

import com.codeborne.selenide.Selenide;
import com.epam.pipeline.autotests.ao.ShellAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.screenshot;
import static com.epam.pipeline.autotests.ao.Primitive.CLOUD_REGIONS_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.EMAIL_NOTIFICATIONS_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.PREFERENCES_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.SYSTEM_EVENTS_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.USER_MANAGEMENT_TAB;
import static com.epam.pipeline.autotests.utils.Utils.assertStringContainsList;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;

public class RoleBasedAccessControlTest extends AbstractSeveralPipelineRunningTest implements Authorization, Tools {

    private final String testUser = "rbacTestUser" + Utils.randomSuffix();
    private final String roleAdmin = "ROLE_ADMIN";
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String toolEndpoint = testingTool.substring(testingTool.lastIndexOf("/") + 1);
    private static final String localFilePath = URI.create(C.DOWNLOAD_FOLDER + "/").resolve("export.csv")
            .toString();

    @BeforeClass
    public void initialLogout() {
        logout();
    }

    @AfterMethod(alwaysRun = true)
    public void deletingEntities() {
        logoutIfNeeded();
    }

    @AfterClass(alwaysRun = true)
    public void deleteDownloaded() {
        new File(localFilePath).delete();
    }

    @AfterClass(alwaysRun = true)
    public void loginToDeleteEntities() {
        loginAs(admin);
    }

    @Test
    @TestCase(value = "EPMCMBIBPC-3014")
    public void authenticationInPlatform() {
        loginAs(admin);
        navigationMenu()
                .library();
    }

    @Test
    @TestCase(value = "EPMCMBIBPC-3015")
    public void failedAuthentication() {
        Utils.restartBrowser(C.ROOT_ADDRESS);
        final Account wrongAccount = new Account(C.LOGIN, format("123%s", C.PASSWORD));
        loginAs(wrongAccount);
        if ("true".equals(C.AUTH_TOKEN)) {
            validateErrorPage(singletonList("type=Unauthorized, status=401"));
            Selenide.clearBrowserCookies();
            sleep(1, SECONDS);
        } else {
            validateErrorPage(singletonList("Incorrect user name or password"));
        }
        loginAs(admin);
    }

    @Test
    @TestCase({"EPMCMBIBPC-3019"})
    public void addTheUser() {
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .createUser(testUser);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .checkUserExist(testUser)
                .checkUserRoles(testUser, "ROLE_USER", "ROLE_PIPELINE_MANAGER",
                        "ROLE_FOLDER_MANAGER", "ROLE_CONFIGURATION_MANAGER");
    }

    @Test(dependsOnMethods = {"addTheUser"})
    @TestCase({"EPMCMBIBPC-3020"})
    public void removeTheUser() {
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .checkUserExist(testUser)
                .deleteUser(testUser)
                .checkUserTabIsEmpty();
    }

    @Test
    @TestCase({"EPMCMBIBPC-3017"})
    public void provideAdminRightsToTheUser() {
        loginAs(user);
        navigationMenu()
                .settings()
                .ensureNotVisible(SYSTEM_EVENTS_TAB, USER_MANAGEMENT_TAB,
                        EMAIL_NOTIFICATIONS_TAB, PREFERENCES_TAB, CLOUD_REGIONS_TAB);
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(user.login)
                .edit()
                .addRoleOrGroup(roleAdmin)
                .ok();
        logout();
        loginWithToken(C.ANOTHER_ADMIN_TOKEN);
        navigationMenu()
                .settings()
                .ensureVisible(SYSTEM_EVENTS_TAB, USER_MANAGEMENT_TAB,
                        EMAIL_NOTIFICATIONS_TAB, PREFERENCES_TAB, CLOUD_REGIONS_TAB);
    }

    @Test(dependsOnMethods = {"provideAdminRightsToTheUser"})
    @TestCase({"EPMCMBIBPC-3018"})
    public void removeAdminRightsFromTheUser() {
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(user.login)
                .edit()
                .deleteRoleOrGroup(roleAdmin)
                .ok();
        logout();
        loginAs(user);
        navigationMenu()
                .settings()
                .ensureNotVisible(SYSTEM_EVENTS_TAB, USER_MANAGEMENT_TAB,
                        EMAIL_NOTIFICATIONS_TAB, PREFERENCES_TAB, CLOUD_REGIONS_TAB);
    }

    @Test
    @TestCase({"EPMCMBIBPC-3016"})
    public void blockUnblockUser() {
        try {
            loginAs(user);
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .setDefaultLaunchOptions()
                    .launchTool(this, Utils.nameWithoutGroup(testingTool));
            logout();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchUserEntry(user.login.toUpperCase())
                    .edit()
                    .blockUser(user.login.toUpperCase())
                    .ok();
            logout();
            loginAs(user);
            validateWhileErrorPageMessage();
            loginBack();
            loginAs(user);
            validateWhileErrorPageMessage();
        } finally {
            loginBack();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchUserEntry(user.login.toUpperCase())
                    .edit()
                    .unblockUser(user.login.toUpperCase())
                    .ok();
        }
        logout();
        loginAs(user);
        runsMenu()
                .activeRuns()
                .shouldContainRun("pipeline", getLastRunId())
                .validatePipelineOwner(getLastRunId(), user.login.toLowerCase());
    }

    @Test
    @TestCase({"EPMCMBIBPC-3168"})
    public void generateListOfUsers() throws IOException {
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .exportUsers()
                .sleep(6, SECONDS);
        List<String> readFileLines = Files.readAllLines(Paths.get(localFilePath));

        assertTrue(readFileLines.size() >= 2);
        assertStringContainsList(readFileLines.get(0), "id", "userName", "registrationDate UTC",
                "firstLoginDate UTC", "roles", "groups", "blocked", "defaultStorageId", "defaultStoragePath");
        assertStringContainsList(readFileLines.toString(), format(",%s,", admin.login.toUpperCase()),
                format(",%s,", user.login.toUpperCase()));
    }

    @Test
    @TestCase(value = "EPMCMBIBPC-3169")
    public void checkAnonymousAccess() {
        if ("false".equals(C.AUTH_TOKEN)) {
            return;
        }
        logoutIfNeeded();
        loginAs(admin);
        String endpoint = tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .launchTool(this, toolEndpoint)
                .show(getLastRunId())
                .waitEndpoint()
                .attr("href");
        logout();
        Utils.restartBrowser(endpoint);
        new ShellAO().assertPageContains("type=Unauthorized, status=401").close();
        open(C.ROOT_ADDRESS);
        loginAs(admin);
        runsMenu()
                .log(getLastRunId(), log -> log
                        .shareWithGroup("ROLE_ANONYMOUS_USER")
                        .validateShareLink("role_anonymous_user")
        );
        sleep(1, MINUTES);
        logout();
        final Account anonymousAccount = new Account(C.ANONYMOUS_NAME, C.ANONYMOUS_TOKEN);
        loginAs(anonymousAccount);
        sleep(2, SECONDS);
        open(endpoint);
        screenshot("Endpoint_page");
        new ShellAO().assertPageContains(C.ANONYMOUS_NAME);
        Utils.restartBrowser(C.ROOT_ADDRESS);
        loginAs(admin);
    }

    private void validateWhileErrorPageMessage() {
        validateErrorPage(Arrays.asList(
                "Please contact",
                format("%s support team", C.PLATFORM_NAME),
                "to request the access",
                format("login back to the %s", C.PLATFORM_NAME),
                "if you already own an account")
        );
    }

    private void loginWithToken(final String token) {
        if ("false".equals(C.AUTH_TOKEN)) {
            loginAs(user);
            return;
        }
        final Account userWithToken = new Account(user.login, token);
        loginAs(userWithToken);
    }
}

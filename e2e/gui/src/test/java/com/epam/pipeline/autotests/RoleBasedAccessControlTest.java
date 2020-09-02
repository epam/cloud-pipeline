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
import java.util.List;

import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.Utils.assertStringContainsList;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;

public class RoleBasedAccessControlTest
        extends AbstractSinglePipelineRunningTest
        implements Authorization, Tools {

    private final String testUser = "rbacTestUser" + Utils.randomSuffix();
    private final String roleAdmin = "ROLE_ADMIN";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
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
        loginAs(user);
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
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, Utils.nameWithoutGroup(tool));
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
        logout();
        loginAs(user);
        runsMenu()
                .activeRuns()
                .shouldContainRun("pipeline", getRunId())
                .validatePipelineOwner(getRunId(), user.login.toLowerCase());
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
        assertStringContainsList(readFileLines.get(0), "id", "userName", "Email", "FirstName",
                "LastName", "Name", "registrationDate UTC", "firstLoginDate UTC", "roles", "groups", "blocked",
                "defaultStorageId", "defaultStoragePath");
        assertStringContainsList(readFileLines.toString(), format(",%s,", admin.login.toUpperCase()),
                format(",%s,", user.login.toUpperCase()));
    }


    private void validateWhileErrorPageMessage() {
        validateErrorPage("Please contact");
        validateErrorPage(format("%s support team",C.PLATFORM_NAME));
        validateErrorPage("to request the access");
        validateErrorPage(format("login back to the %s",C.PLATFORM_NAME));
        validateErrorPage("if you already own an account");
    }


}

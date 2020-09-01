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

import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.ao.Primitive.*;

public class RoleBasedAccessControlTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private final String testUser = "rbacTestUser" + Utils.randomSuffix();
    private final String roleAdmin = "ROLE_ADMIN";

    @BeforeClass
    public void initialLogout() {
        logout();
    }

    @AfterMethod(alwaysRun = true)
    public void deletingEntities() {
        logout();
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
}

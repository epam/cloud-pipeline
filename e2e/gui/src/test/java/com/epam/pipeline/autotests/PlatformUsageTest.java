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

import com.epam.pipeline.autotests.ao.SettingsPageAO.UserManagementAO.UsersTabAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDateTime;

import static com.epam.pipeline.autotests.ao.Primitive.SHOW_USERS;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static java.time.format.DateTimeFormatter.ofPattern;

public class PlatformUsageTest extends AbstractBfxPipelineTest implements Navigation, Authorization {

    static final String showOnlineUsers = "Show online users";
    static final String ROLE_USER_READER = "ROLE_USER_READER";

    @BeforeClass
    public void addUserRole() {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(userWithoutCompletedRuns.login)
                .edit()
                .addRoleOrGroupIfNonExist(ROLE_USER_READER)
                .ok();
    }

    @AfterClass
    public void removeUserRole() {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(userWithoutCompletedRuns.login)
                .edit()
                .deleteRoleOrGroup(ROLE_USER_READER)
                .ok();
    }

    @Test
    @TestCase(value = {"2433"})
    public void showUserStatuses() {
        logout();
        loginAs(userWithoutCompletedRuns);
        String lastVisited  = LocalDateTime.now().format(ofPattern("d MMMM yyyy, HH:mm"));
        final UsersTabAO usersTabAO = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers();
        usersTabAO
                .searchUserEntry(userWithoutCompletedRuns.login)
                .ensureNotVisible(STATUS);
        usersTabAO.checkValueIsNotInDropDown(SHOW_USERS, showOnlineUsers);
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers();
        usersTabAO
                .searchUserEntry(userWithoutCompletedRuns.login)
                .validateUserStatus("offline")
                .validateStatusTooltipText(lastVisited)
                .selectValue(SHOW_USERS, showOnlineUsers);
        usersTabAO
                .checkUserExist(admin.login)
                .checkUserNotExist(userWithoutCompletedRuns.login);
    }
}

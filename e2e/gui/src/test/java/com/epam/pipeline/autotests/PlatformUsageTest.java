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

import com.epam.pipeline.autotests.ao.SettingsPageAO.UserManagementAO.UsersTabAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import java.time.LocalDateTime;

import static com.epam.pipeline.autotests.ao.Primitive.SEARCH;
import static com.epam.pipeline.autotests.ao.Primitive.SHOW_USERS;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static java.time.format.DateTimeFormatter.ofPattern;

public class PlatformUsageTest extends AbstractBfxPipelineTest implements Navigation, Authorization {

    static final String showOnlineUsers = "Show online users";
    static final String showOfflineUsers = "Show offline users";

    @Test
    @TestCase(value = {"2433"})
    public void showUserStatuses() {
        logout();
        loginAs(user);
        String lastVisited  = LocalDateTime.now().format(ofPattern("d MMMM yyyy, HH:mm"));
        final UsersTabAO usersTabAO = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers();
        usersTabAO
                .searchUserEntry(user.login)
                .ensureNotVisible(STATUS);
        usersTabAO.checkValueIsNotInDropDown(SHOW_USERS, showOnlineUsers, showOfflineUsers);
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers();
        usersTabAO
                .searchUserEntry(user.login)
                .validateUserStatus("offline")
                .validateStatusTooltipText(lastVisited)
                .selectDropDownValue(SHOW_USERS, showOfflineUsers)
                .checkUserExist(user.login)
                .clear(SEARCH);
        usersTabAO
                .selectDropDownValue(SHOW_USERS, showOnlineUsers)
                .checkUserExist(admin.login);
    }
}

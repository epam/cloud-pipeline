/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.SettingsPageAO.UserManagementAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.FolderPermission;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.DENY;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.INHERIT;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoragePermissionsTest extends AbstractBfxPipelineTest implements Authorization, Navigation {
    private final String folder1 = "Folder3970-1-" + Utils.randomSuffix();
    private final String storage1 = "storage3970-1-" + Utils.randomSuffix();
    private final String userGroup = "NEW_TEST_GROUP";
    private final String uiStoragesPermissionsRestrictions = "ui.storages.permissions.restrictions";
    private String[] uiStoragesPermissionsRestrictionsInitial;
    private String uiStoragesPermissionsRestrictionsJson1 = format("[{\n \"role\": \"ROLE_%s\"," +
            "\n \"disable\": \"WRITE,EXECUTE\"\n }]", userGroup);
    private String uiStoragesPermissionsRestrictionsJson2 = "[{\n \"role\": \"ALL\"," +
            "\n \"disable\": \"WRITE,EXECUTE\"\n }]";
    private static final String ROLE_STORAGE_MANAGER = "ROLE_STORAGE_MANAGER";
    private static final String TEST_ROLE = "ROLE_CONFIGURATION_MANAGER";

    @BeforeClass(alwaysRun = true)
    public void preparations() {
        loginAs(admin);
        UserManagementAO userManagementAO = navigationMenu()
                .settings()
                .switchToUserManagement();
        userManagementAO
                .switchToGroups()
                .deleteGroupIfPresent(userGroup)
                .pressCreateGroup()
                .enterGroupName(userGroup)
                .sleep(2, SECONDS)
                .create()
                .sleep(2, SECONDS);
        userManagementAO
                .switchToUsers()
                .searchUserEntry(user.login)
                .edit()
                .addRoleOrGroupIfNonExist(ROLE_STORAGE_MANAGER)
                .ok();
        uiStoragesPermissionsRestrictionsInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(uiStoragesPermissionsRestrictions);
        library()
                .createFolder(folder1)
                .clickOnFolder(folder1)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user,
                FolderPermission.allow(READ, folder1),
                FolderPermission.allow(WRITE, folder1),
                FolderPermission.allow(EXECUTE, folder1));
        loginAs(user);
        library()
                .cd(folder1)
                .createStorage(storage1);
    }

    @AfterClass(alwaysRun = true)
    public void restoreSettings() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .deleteGroupIfPresent(userGroup);
    }

    @AfterClass(alwaysRun = true)
    public void restorePreference() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(uiStoragesPermissionsRestrictions,
                        uiStoragesPermissionsRestrictionsInitial[0],
                        parseBoolean(uiStoragesPermissionsRestrictionsInitial[1]))
                .saveIfNeeded();
    }

    @AfterClass(alwaysRun = true)
    public void restoreUserSettings() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(user.login)
                .edit()
                .deleteRoleOrGroupIfExist(ROLE_STORAGE_MANAGER)
                .ok();
    }

    @AfterClass(alwaysRun = true)
    public void removeTestEntyties() {
        logoutIfNeeded();
        loginAs(admin);
        library()
                .removeNotEmptyFolder(folder1);
    }

    @Test
    @TestCase(value = "3970_1")
    public void storagePermissionsRestrictions() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(uiStoragesPermissionsRestrictions,
                        uiStoragesPermissionsRestrictionsJson1, true)
                .saveIfNeeded();

        logout();
        loginAs(user);
        library()
                .cd(folder1)
                .selectStorage(storage1)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewGroup(userGroup)
                .selectByName(userGroup)
                .showPermissions()
                .validatePrivilegeIsDisabled(WRITE, DENY)
                .validatePrivilegeIsDisabled(EXECUTE, DENY)
                .validatePrivilegeIsNotDisabled(READ, INHERIT)
                .closeAll();
    }

    @Test
    @TestCase(value = "3970_2")
    public void storagePermissionsRestrictionsForAllGroupsRoles() {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(uiStoragesPermissionsRestrictions,
                        uiStoragesPermissionsRestrictionsJson2, true)
                .saveIfNeeded();

        logout();
        loginAs(user);
        library()
                .cd(folder1)
                .selectStorage(storage1)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewGroup(TEST_ROLE)
                .selectByName(TEST_ROLE)
                .showPermissions()
                .validatePrivilegeIsDisabled(WRITE, DENY)
                .validatePrivilegeIsDisabled(EXECUTE, DENY)
                .validatePrivilegeIsNotDisabled(READ, INHERIT)
                .closeAll();
    }
}

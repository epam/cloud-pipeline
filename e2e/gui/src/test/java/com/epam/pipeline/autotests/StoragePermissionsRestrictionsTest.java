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

import com.codeborne.selenide.Condition;
import static com.codeborne.selenide.Condition.visible;
import com.epam.pipeline.autotests.ao.CreateStoragePopupAO;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_KEY;
import static com.epam.pipeline.autotests.ao.Primitive.ALL_STORAGES;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_STORAGE;
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

import java.util.stream.Stream;

public class StoragePermissionsRestrictionsTest extends AbstractBfxPipelineTest implements Authorization, Navigation {
    private final String folder1 = "Folder3970-" + Utils.randomSuffix();
    private final String folder2 = "Folder3389-" + Utils.randomSuffix();
    private final String folder3 = "Folder3389-" + Utils.randomSuffix();
    private final String storage1 = "storage3970-" + Utils.randomSuffix();
    private final String storage2 = "storage3389-" + Utils.randomSuffix();
    private final String storage2new = format("new%s", storage2);
    private final String storage3 = "storage3389-" + Utils.randomSuffix();
    private final String storage4 = "storage3389-" + Utils.randomSuffix();
    private final String testFile1 = "testfile1.txt";
    private final String testFile2 = "testfile2.txt";
    private final String key1 = "key1";
    private final String value1 = "value1";
    private final String key2 = "key2";
    private final String value2 = "value2";
    private final String userGroup = "NEW_TEST_GROUP";
    private final String uiStoragesPermissionsRestrictions = "ui.storages.permissions.restrictions";
    private String[] uiStoragesPermissionsRestrictionsInitial;
    private String uiStoragesPermissionsRestrictionsJson1 = format("[{\n \"role\": \"ROLE_%s\"," +
            "\n \"disable\": \"WRITE,EXECUTE\"\n }]", userGroup);
    private String uiStoragesPermissionsRestrictionsJson2 = "[{\n \"role\": \"ALL\"," +
            "\n \"disable\": \"WRITE,EXECUTE\"\n }]";
    private static final String ROLE_STORAGE_MANAGER = "ROLE_STORAGE_MANAGER";
    private static final String TEST_ROLE = "ROLE_CONFIGURATION_MANAGER";
    private static final String ROLE_STORAGE_ADMIN = "ROLE_STORAGE_ADMIN";

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
                .createStorage(storage2)
                .selectStorage(storage2)
                .createFile(testFile1);
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
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(userWithoutCompletedRuns.login)
                .edit()
                .deleteRoleOrGroupIfExist(ROLE_STORAGE_ADMIN)
                .ok();
    }

    @AfterClass(alwaysRun = true)
    public void removeTestEntyties() {
        logoutIfNeeded();
        loginAs(admin);
        Stream.of(folder1, folder2, folder3)
                .forEach(folder -> library().removeNotEmptyFolder(folder));
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

    @Test
    @TestCase(value = "3389_1")
    public void roleStorageAdminOperationsWithExistingStorage() {
        logout();
        loginAs(userWithoutCompletedRuns);
        library()
                .ensure(ALL_STORAGES, Condition.visible)
                .click(ALL_STORAGES)
                .ensurePipelineOrStorageIsNotPresentInTable(storage2);
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(userWithoutCompletedRuns.login)
                .edit()
                .addRoleOrGroupIfNonExist (ROLE_STORAGE_ADMIN)
                .ok();
        logout();
        loginAs(userWithoutCompletedRuns);
        library()
                .validateStorage(storage2)
                .selectStorage(storage2)
                .clickEditStorageButton()
                .setAlias(storage2new)
                .clickSaveButton()
                .validateStorage(storage2new);
        library()
                .selectStorage(storage2new)
                .rmFile(testFile1)
                .validateElementNotPresent(testFile1)
                .createFile(testFile2)
                .validateElementIsPresent(testFile2);
        library()
                .removeStorage(storage2new)
                .validateStorageIsNotPresent(storage2new);
    }

    @Test(dependsOnMethods = "roleStorageAdminOperationsWithExistingStorage")
    @TestCase(value = "3389_2")
    public void roleStorageAdminStorageCreation() {
        logoutIfNeeded();
        loginAs(admin);
        Stream.of(folder2, folder3).forEach( folder -> library()
                .createFolder(folder)
                .clickOnFolder(folder)
                .sleep(2, SECONDS)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(userWithoutCompletedRuns.login)
                .closeAll());
        givePermissions(userWithoutCompletedRuns,
                FolderPermission.allow(READ, folder2),
                FolderPermission.allow(WRITE, folder3));

        logout();
        loginAs(userWithoutCompletedRuns);
        library()
                .cd(folder2)
                .ensureNotVisible(CREATE)
                .cd(folder3)
                .ensureVisible(CREATE)
                .click(CREATE)
                .ensureVisible(CREATE_STORAGE)
                .click(CREATE_STORAGE);
        new CreateStoragePopupAO()
                .setStoragePath(storage3)
                .ok()
                .validateStorage(storage3);
    }

    @Test(dependsOnMethods = "roleStorageAdminOperationsWithExistingStorage")
    @TestCase(value = "3389_3")
    public void roleStorageAdminOperationsWithStorageTags() {
        logout();
        loginAs(admin);
        library()
                .createStorage(storage4)
                .selectStorage(storage4)
                .createFile(testFile1)
                .showMetadata()
                .addKeyWithValue(key1, value1)
                .addKeyWithValue(key2, value2)
                .ok();
        navigationMenu()
                .library()
                .selectStorage(storage4)
                .fileMetadata(testFile1)
                .addKeyWithValue(key1, value1)
                .addKeyWithValue(key2, value2)
                .ok();


    }
}

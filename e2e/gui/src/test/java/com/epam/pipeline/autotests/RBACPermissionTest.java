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

import com.epam.pipeline.autotests.ao.UserManagementAO.GroupsTabAO.EditGroupPopup;
import com.epam.pipeline.autotests.ao.UserManagementAO.UsersTabAO.UserEntry;
import com.epam.pipeline.autotests.ao.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.BLOCK;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.IMPERSONATE;
import static com.epam.pipeline.autotests.ao.Primitive.PROFILE;
import static com.epam.pipeline.autotests.ao.Primitive.SEARCH;
import static com.epam.pipeline.autotests.ao.Primitive.USER_MANAGEMENT_TAB;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.INHERIT;
import static java.lang.String.format;

public class RBACPermissionTest extends AbstractBfxPipelineTest implements Authorization {

    private final String key1 = "key1";
    private final String value1 = "value1";
    private final String key2 = "key2";
    private final String value2 = "value2";
    private String key3 = "key3";
    private String value3 = "value3";
    private final String key4 = "key4";
    private final String value4 = "value4";
    private final String toolInstanceTypesMask = "Allowed tool instance types mask";
    private final String mask = "*";
    private String[] initialMiscMetadataSensitiveKeys = {""};
    private final String miscMetadataSensitiveKeys = "misc.metadata.sensitive.keys";
    private final String miscMetadataSensitiveKeysTestValue = format("[\"%s\"]", key1);
    private final String testGroup1 = "TEST_GROUP_1";
    private final String testGroup2 = "TEST_GROUP_2";


    @BeforeClass
    public void prepareTestGroups() {
        createTestGroup(testGroup1, null);
        createTestGroup(testGroup2, user);
        initialMiscMetadataSensitiveKeys = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(miscMetadataSensitiveKeys);
    }

    @AfterClass
    public void removeTestGroups() {
        loginAsUser(admin);
        setMiscMetadataSensitiveKeys(initialMiscMetadataSensitiveKeys[0]);
        Stream.of(testGroup1, testGroup2)
                .forEach(group -> {
                     navigationMenu()
                         .settings()
                         .switchToUserManagement()
                         .switchToGroups()
                         .searchGroupBySubstring(group)
                         .deleteGroupIfPresent(group);
                });
    }

    @AfterClass
    public void cleanUpUserPermissions() {
        loginAsUser(admin);
        UserEntry testUser = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login);
        testUser
                .edit()
                .getUserPermissionsTab()
                .deleteIfPresent(user.login)
                .deleteIfPresent(testGroup2)
                .closeAll();
        testUser
                .edit()
                .addAllowedLaunchOptions(toolInstanceTypesMask, "")
                .showMetadata()
                .deleteKeys(key1, key2, key3, key4)
                .ok();
    }

    @Test(priority = 1)
    @TestCase(value = "3229_1")
    public void readGrantPermissionsToUserAccount() {
        userPermissionsPreparations();
        loginAsUser(user);
        navigationMenu()
                .settings()
                .ensure(USER_MANAGEMENT_TAB, visible)
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login)
                .openEditUserPopUp()
                .ensureNotVisible(IMPERSONATE)
                .ensureDisable(DELETE, BLOCK)
                .isListOfRolesBlocked()
                .isAllowedLaunchOptionsDisable(toolInstanceTypesMask)
                .showMetadata()
                .assertKeysArePresent(key1, key2, key3)
                .assertKeysAreDisabled(key1, key2, key3)
                .ok();
    }

    @Test(priority = 1, dependsOnMethods = "readGrantPermissionsToUserAccount")
    @TestCase(value = "3229_2")
    public void wtiteGrantPermissionsToUserAccount() {
        loginAsUser(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login)
                .edit()
                .getUserPermissionsTab()
                .addNewGroup(testGroup2)
                .selectByName(testGroup2)
                .showPermissions()
                .set(WRITE, ALLOW)
                .savePermissions()
                .closeAll();
        setMiscMetadataSensitiveKeys(miscMetadataSensitiveKeysTestValue);
        loginAsUser(user);
        navigationMenu()
                .settings()
                .ensure(USER_MANAGEMENT_TAB, visible)
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login)
                .openEditUserPopUp()
                .ensureNotVisible(IMPERSONATE)
                .ensureDisable(DELETE, BLOCK)
                .isListOfRolesBlocked()
                .isAllowedLaunchOptionsDisable(toolInstanceTypesMask)
                .showMetadata()
                .assertKeyNotPresent(key1)
                .assertKeysArePresent(key2, key3)
                .deleteKeys(key2)
                .selectKey(key3)
                .changeValue(value3 = format("%s_new", value3))
                .changeKey(key3 = format("%s_new", key3))
                .close()
                .addKeyWithValue(key4, value4)
                .ok();
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login)
                .openEditUserPopUp()
                .showMetadata()
                .assertKeysAreNotPresent(key1, key2)
                .assertKeysArePresent(key4, key3)
                .ok();
    }

    @Test(priority = 1, dependsOnMethods = "wtiteGrantPermissionsToUserAccount")
    @TestCase(value = "3229_3")
    public void executeGrantPermissionsToUserAccount() {
        loginAsUser(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers().searchUserEntry(admin.login)
                .edit()
                .getUserPermissionsTab()
                .selectByName(testGroup2)
                .showPermissions()
                .set(EXECUTE, ALLOW)
                .set(READ, INHERIT)
                .set(WRITE, INHERIT)
                .savePermissions()
                .closeAll();
        loginAsUser(user);
        navigationMenu()
                .settings()
                .ensure(USER_MANAGEMENT_TAB, visible)
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login)
                .openEditUserPopUp()
                .ensureVisible(IMPERSONATE)
                .ensureDisable(DELETE, BLOCK)
                .isListOfRolesBlocked()
                .isAllowedLaunchOptionsDisable(toolInstanceTypesMask)
                .showMetadata()
                .assertKeysArePresent(key3, key4)
                .assertKeysAreDisabled(key3, key4)
                .ok()
                .click(IMPERSONATE);
        navigationMenu()
                .settings()
                .switchToMyProfile()
                .validateUserName(admin.login);
    }

    @Test(priority = 2)
    @TestCase(value = "3751_1")
    public void readGrantPermissionsToUserGroup() {
        groupPermissionsPreparations();
        setMiscMetadataSensitiveKeys(initialMiscMetadataSensitiveKeys[0]);
        loginAsUser(user);
        EditGroupPopup editGroupPopup = navigationMenu()
                .settings()
                .ensure(USER_MANAGEMENT_TAB, visible)
                .switchToUserManagement()
                .switchToGroups()
                .openEditGroupPopUp(testGroup1);
        editGroupPopup
                .isListOfUsersBlocked()
                .isAllowedLaunchOptionsDisable(toolInstanceTypesMask)
                .showMetadata()
                .assertKeysArePresent(key1, key2, key3)
                .assertKeysAreDisabled(key1, key2, key3)
                .ok();
        editGroupPopup.ok();
    }

    @Test(priority = 2, dependsOnMethods = "readGrantPermissionsToUserGroup")
    @TestCase(value = "3751_2")
    public void writeGrantPermissionsToUserGroup() {
        loginAsUser(admin);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .searchGroupBySubstring(testGroup1)
                .editGroup(testGroup1)
                .getGroupPermissionsTab()
                .addNewGroup(testGroup2)
                .selectByName(testGroup2)
                .showPermissions()
                .set(WRITE, true)
                .savePermissions()
                .closeAll();
        setMiscMetadataSensitiveKeys(miscMetadataSensitiveKeysTestValue);
        loginAsUser(user);
        navigationMenu()
                .settings()
                .ensure(USER_MANAGEMENT_TAB, visible)
                .switchToUserManagement()
                .switchToGroups()
                .openEditGroupPopUp(testGroup1)
                .addUserIfNonExist(admin.login)
                .isAllowedLaunchOptionsDisable(toolInstanceTypesMask)
                .showMetadata()
                .assertKeyNotPresent(key1)
                .assertKeysArePresent(key2, key3)
                .deleteKeys(key2)
                .selectKey(key3)
                .changeValue(value3 = format("%s_new", value3))
                .changeKey(key3 = format("%s_new", key3))
                .close()
                .addKeyWithValue(key4, value4)
                .ok();
        EditGroupPopup editGroupPopup = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .openEditGroupPopUp(testGroup1);
        editGroupPopup
                .checkUserExistsInGroup(admin.login)
                .showMetadata()
                .assertKeysAreNotPresent(key1, key2)
                .assertKeysArePresent(key4, key3)
                .ok();
        editGroupPopup.ok();
    }

    private void loginAsUser(Account account) {
        logoutIfNeeded();
        loginAs(account)
                .settings()
                .switchToMyProfile()
                .validateUserName(account.login);
    }

    private void userPermissionsPreparations() {
        loginAsUser(admin);
        EditUserPopup editUserPopup = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login)
                .edit();
        editUserPopup
                .getUserPermissionsTab()
                .addNewUser(user.login)
                .selectByName(user.login)
                .showPermissions()
                .set(READ,ALLOW)
                .savePermissions();
        editUserPopup
                .click(PROFILE)
                .addAllowedLaunchOptions(toolInstanceTypesMask, mask)
                .showMetadata()
                .addKeyWithValue(key1, value1)
                .addKeyWithValue(key2, value2)
                .addKeyWithValue(key3, value3)
                .ok();
    }

    private void groupPermissionsPreparations() {
        loginAsUser(admin);
        EditGroupPopup editGroupPopup = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .searchGroupBySubstring(testGroup1)
                .editGroup(testGroup1);
        editGroupPopup
                .getGroupPermissionsTab()
                .addNewUser(user.login)
                .selectByName(user.login)
                .showPermissions()
                .set(READ,true)
                .savePermissions();
        editGroupPopup
                .click(PROFILE)
                .addAllowedLaunchOptions(toolInstanceTypesMask, mask)
                .showMetadata()
                .addKeyWithValue(key1, value1)
                .addKeyWithValue(key2, value2)
                .addKeyWithValue(key3, value3)
                .ok();
    }

    private void createTestGroup(String groupName, Account account) {
        EditGroupPopup editGroupPopup = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .pressCreateGroup()
                .enterGroupName(groupName)
                .create()
                .searchGroupBySubstring(groupName)
                .editGroup(groupName)
                .ensureVisible(SEARCH);
        if(account != null) {
            editGroupPopup.addUserIfNonExist(account.login);
        }
        editGroupPopup.ok();
    }

    private void setMiscMetadataSensitiveKeys(String value) {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(miscMetadataSensitiveKeys, value, true)
                .saveIfNeeded();
    }
}

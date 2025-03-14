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

import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.BLOCK;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.IMPERSONATE;
import static com.epam.pipeline.autotests.ao.Primitive.PROFILE;
import static com.epam.pipeline.autotests.ao.Primitive.USER_MANAGEMENT_TAB;
import com.epam.pipeline.autotests.ao.UserManagementAO.GroupsTabAO.EditGroupPopup;
import com.epam.pipeline.autotests.ao.UserManagementAO.UsersTabAO.UserEntry;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.INHERIT;
import static java.lang.String.format;
import com.epam.pipeline.autotests.ao.UserManagementAO.UsersTabAO;
import com.epam.pipeline.autotests.ao.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.stream.Stream;

public class RBACPermissionTest extends AbstractBfxPipelineTest implements Authorization {

    private String[][] attr = {{"key1", "value1"}, {"key2", "value2"},
                               {"key3", "value3"}, {"key4", "value4"}};
    private final String toolInstanceTypesMask = "Allowed tool instance types mask";
    private final String mask = "m.*";
    private final String testGroup = C.ROLE_USER;
    private String[] initialMiscMetadataSensitiveKeys = {""};
    private String miscMetadataSensitiveKeys = "misc.metadata.sensitive.keys";
    private String miscMetadataSensitiveKeysTestValue = format("[\"%s\"]", attr[0][0]);
    private final String testGroup1 = "TEST_GROUP_1";
    private final String testGroup2 = "TEST_GROUP_2";


    @BeforeClass
    public void prepareTestGroups() {
        Stream.of(testGroup1, testGroup2)
                .forEach(group -> createTestGroup(group));
    }

    @AfterClass
    public void removeTestGroups() {
        loginAsUser(admin);
        Stream.of(testGroup1, testGroup2)
                .forEach(group -> removeTestGroup(group));
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
                .assertKeysArePresent(attr[0][0], attr[1][0], attr[2][0])
                .assertKeysAreDisabled(attr[0][0], attr[1][0], attr[2][0])
                .ok();
    }

    @Test(priority = 1, dependsOnMethods = "readGrantPermissionsToUserAccount")
    @TestCase(value = "3229_2")
    public void wtiteGrantPermissionsToUserAccount() {
            loginAsUser(admin);
            UsersTabAO testUser = navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers();
            testUser
                    .searchUserEntry(user.login)
                    .edit()
                    .addRoleOrGroupIfNonExist(testGroup)
                    .ok();
            testUser
                    .searchUserEntry(admin.login)
                    .edit()
                    .getUserPermissionsTab()
                    .addNewGroup(testGroup)
                    .selectByName(testGroup)
                    .showPermissions()
                    .set(WRITE, ALLOW)
                    .savePermissions()
                    .closeAll();
            initialMiscMetadataSensitiveKeys = navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .getPreference(miscMetadataSensitiveKeys);
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
                    .assertKeyNotPresent(attr[0][0])
                    .assertKeysArePresent(attr[1][0], attr[2][0])
                    .deleteKeys(attr[1][0])
                    .selectKey(attr[2][0])
                    .changeValue(attr[2][1] = format("%s_new", attr[2][1]))
                    .changeKey(attr[2][0] = format("%s_new", attr[2][0]))
                    .close()
                    .addKeyWithValue(attr[3][0], attr[3][1])
                    .ok();
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchUserEntry(admin.login)
                    .openEditUserPopUp()
                    .showMetadata()
                    .assertKeysAreNotPresent(attr[0][0], attr[1][0])
                    .assertKeysArePresent(attr[3][0], attr[2][0])
                    .ok();
    }

    @Test(priority = 1, dependsOnMethods = "wtiteGrantPermissionsToUserAccount")
    @TestCase(value = "3229_3")
    public void executeGrantPermissionsToUserAccount() {
        try {
            loginAsUser(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers().searchUserEntry(admin.login)
                    .edit()
                    .getUserPermissionsTab()
                    .selectByName(testGroup)
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
                    .assertKeysArePresent(attr[2][0], attr[3][0])
                    .assertKeysAreDisabled(attr[2][0], attr[3][0])
                    .ok()
                    .click(IMPERSONATE);
            navigationMenu()
                    .settings()
                    .switchToMyProfile()
                    .validateUserName(admin.login);
        } finally {
            cleanUpUserPermissions();
        }

    }

    @Test(priority = 2)
    @TestCase(value = "3751_1")
    public void readGrantPermissionsToUserGroup() {
        groupPermissionsPreparations();
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
                .assertKeysArePresent(attr[0][0], attr[1][0], attr[2][0])
                .assertKeysAreDisabled(attr[0][0], attr[1][0], attr[2][0])
                .ok();
        editGroupPopup.ok();
    }

    @Test(priority = 2, dependsOnMethods = "readGrantPermissionsToUserGroup")
    @TestCase(value = "3751_2")
    public void writeGrantPermissionsToUserGroup() {
        groupPermissionsPreparations();
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
                .assertKeysArePresent(attr[0][0], attr[1][0], attr[2][0])
                .assertKeysAreDisabled(attr[0][0], attr[1][0], attr[2][0])
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
                .addKeyWithValue(attr[0][0], attr[0][1])
                .addKeyWithValue(attr[1][0], attr[1][1])
                .addKeyWithValue(attr[2][0], attr[2][1])
                .ok();
    }

    private void cleanUpUserPermissions() {
        try {
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
                    .deleteIfPresent(testGroup)
                    .closeAll();
            testUser
                    .edit()
                    .addAllowedLaunchOptions(toolInstanceTypesMask, "")
                    .showMetadata()
                    .deleteKeys(attr[0][0], attr[1][0], attr[2][0], attr[3][0])
                    .ok();
        } finally {
            loginAsUser(admin);
            setMiscMetadataSensitiveKeys(initialMiscMetadataSensitiveKeys[0]);
        }
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
                .addKeyWithValue(attr[0][0], attr[0][1])
                .addKeyWithValue(attr[1][0], attr[1][1])
                .addKeyWithValue(attr[2][0], attr[2][1])
                .ok();
    }

    private void createTestGroup(String groupName) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .pressCreateGroup()
                .enterGroupName(groupName)
                .create()
                .searchGroupBySubstring(groupName)
                .editGroup(groupName)
                .addUserIfNonExist(admin.login)
                .ok();
    }

    private void removeTestGroup(String groupName) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .searchGroupBySubstring(groupName)
                .deleteGroupIfPresent(groupName);
    }

    private void setMiscMetadataSensitiveKeys(String value) {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(miscMetadataSensitiveKeys, value, true)
                .saveIfNeeded();
    }
}

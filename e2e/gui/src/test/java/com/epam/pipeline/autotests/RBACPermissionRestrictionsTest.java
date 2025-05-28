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

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.UPLOAD;
import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.ao.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.FolderPermission;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static com.epam.pipeline.autotests.utils.Utils.entityIDfromURL;
import static java.lang.String.format;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.stream.Stream;

public class RBACPermissionRestrictionsTest extends AbstractBfxPipelineTest implements Authorization {
    private final static String ROLE_STORAGE_ADMIN = "ROLE_STORAGE_ADMIN";
    private String[][] attr = {{"key1", "value1"}, {"key2", "value2"},
            {"key3", "value3"}, {"key4", "value4"}};
    private final String storage1 = format("storage_3389_%s", Utils.randomSuffix());
    private final String storage2 = format("storage_3389_%s", Utils.randomSuffix());
    private final String storage3 = format("storage_3389_%s", Utils.randomSuffix());
    private final String folder1 = format("folder-3389-%s", Utils.randomSuffix());
    private final String folder2 = format("folder-3389-%s", Utils.randomSuffix());
    private final String storage1new = format("new_%s", storage1);
    private String storage1ID = "";
    String[][] file = {{"testfile.txt", "testfile"}};

    @BeforeMethod
    public void relogin() {
        open(C.ROOT_ADDRESS);
        loginAsUser(admin);
    }

    @AfterClass
    public void cleanUpEntities() {
        Stream.of(storage1, storage2, storage3, storage1new)
                .forEach(storage -> library().removeStorageIfExists(storage));
        Stream.of(folder1, folder2).forEach(fold -> library().removeFolder(fold));
    }

    @AfterClass
    public void cleanUpUserRoles() {
        setStorageAdminRole(false);
    }

    @Test(priority = 1)
    @TestCase(value = "3389_1")
    public void roleStorageAdminOperationsWithExistingStorage() {
        navigationMenu()
                .library()
                .createStorage(storage1)
                .selectStorage(storage1)
                .createFileWithContent(file[0][0], file[0][1]);
        storage1ID = entityIDfromURL();
        logoutIfNeeded();
        loginAsUser(user);
        navigationMenu()
                .library()
                .validateStorageIsNotPresent(storage1);
        setStorageAdminRole(true);
        logoutIfNeeded();
        loginAsUser(user);
        navigationMenu()
                .library()
                .validateStorage(storage1)
                .editStorage(storage1ID)
                .setAlias(storage1new)
                .clickSaveButton()
                .validateStorage(storage1new)
                .selectStorage(storage1new)
                .ensureVisible(CREATE, UPLOAD)
                .rmFile(file[0][0])
                .validateCurrentFolderIsEmpty()
                .createFileWithContent(file[0][0], file[0][1])
                .validateElementIsPresent(file[0][0])
                .clickEditStorageButton()
                .clickDeleteStorageButton()
                .clickDelete()
                .validateStorageIsNotPresent(storage1new);
    }

    @Test(priority = 1, dependsOnMethods = "roleStorageAdminOperationsWithExistingStorage")
    @TestCase(value = "3389_2")
    public void roleStorageAdminStorageCreation() {
        navigationMenu()
                .library()
                .createFolder(folder1)
                .createFolder(folder2);
        addAccountToFolderPermissions(user, folder1);
        givePermissions(user, FolderPermission.allow(READ, folder1));
        addAccountToFolderPermissions(user, folder2);
        givePermissions(user, FolderPermission.allow(WRITE, folder2));
        logout();
        loginAs(user);
        navigationMenu()
                .library()
                .cd(folder1)
                .ensureNotVisible(CREATE)
                .cd(folder2)
                .ensureVisible(CREATE)
                .createStorage(storage2)
                .validateStorage(storage2);
    }

    @Test(priority = 1, dependsOnMethods = "roleStorageAdminOperationsWithExistingStorage")
    @TestCase(value = "3389_3")
    public void roleStorageAdminOperationsWithStorageTags() {
        StorageContentAO storageContentAO = navigationMenu()
                .library()
                .createStorage(storage3)
                .selectStorage(storage3);
        storageContentAO
                .showMetadata()
                .addKeyWithValue(attr[0][0], attr[0][1])
                .addKeyWithValue(attr[1][0], attr[1][1]);
        storageContentAO
                .createFileWithContent(file[0][0], file[0][1])
                .fileMetadata(file[0][0])
                .addKeyWithValue(attr[0][0], attr[0][1])
                .addKeyWithValue(attr[1][0], attr[1][1]);
        loginAsUser(user);
        storageContentAO = navigationMenu()
                .library()
                .selectStorage(storage3);
        storageContentAO
                .showMetadata()
                .assertKeysArePresent(attr[0][0], attr[1][0])
                .deleteKeys(attr[1][0])
                .selectKey(attr[0][0])
                .changeValue(attr[3][1])
                .changeKey(attr[3][0])
                .close()
                .addKeyWithValue(attr[2][0], attr[2][1])
                .assertKeysAreNotPresent(attr[1][0])
                .assertKeysArePresent(attr[3][0], attr[2][0]);
        storageContentAO
                .fileMetadata(file[0][0])
                .assertKeysArePresent(attr[0][0], attr[1][0])
                .deleteKeys(attr[1][0])
                .selectKey(attr[0][0])
                .changeValue(attr[3][1])
                .changeKey(attr[3][0])
                .close()
                .addKeyWithValue(attr[2][0], attr[2][1])
                .assertKeysAreNotPresent(attr[1][0])
                .assertKeysArePresent(attr[3][0], attr[2][0]);
    }

    private void setStorageAdminRole(boolean addRole) {
        logoutIfNeeded();
        loginAsUser(admin);
        EditUserPopup editUserPopup = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(user.login)
                .edit();
        if (addRole) {
            editUserPopup.addRoleOrGroupIfNonExist(ROLE_STORAGE_ADMIN).ok();
        } else {
            editUserPopup.deleteRoleOrGroupIfExist(ROLE_STORAGE_ADMIN).ok();
        }
    }
}

/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.utils.BucketPermission;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import java.io.File;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DOWNLOAD;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT;
import static com.epam.pipeline.autotests.ao.Primitive.RELOAD;
import static com.epam.pipeline.autotests.ao.Primitive.UPLOAD;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static java.util.concurrent.TimeUnit.SECONDS;

public class VersionControlTest extends AbstractBfxPipelineTest implements Authorization {

    private File file;
    private File anotherFile;
    private final long suffix = Utils.randomSuffix();
    private final String storageName = "bucket-as-svn" + suffix;
    private final String readPermitsStorageName = "versions-bucket-to-check-read-permits" + suffix;
    private final String editPermitsStorageName = "versions-bucket-to-check-edit-permits" + suffix;
    private final String storage1502 = "storage-1502-" + Utils.randomSuffix();
    private final String storage1540 = "storage-1540-" + Utils.randomSuffix();
    private final String folderName = "test-folder-in-bucket";
    private final String anotherFolderName = "another-test-folder-in-bucket";
    private final String deletionFolderName = "deletion-test-folder-in-bucket";
    private final String editingFolderName = "editing-test-folder-in-bucket";
    private final String backgroundColorOfDeletedFile = "#fff2ef";
    private final String backgroundColorOfRestoredFile = "#000000";

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        logoutIfNeededAndPerform(() -> {
            loginAs(admin);
            Utils.removeStorages(this,
                    storageName,
                    readPermitsStorageName,
                    editPermitsStorageName,
                    storage1502,
                    storage1540
            );
        });
    }

    @BeforeClass(alwaysRun = true)
    public void createStorageAndUser() {
        navigationMenu()
                .library()
                .clickOnCreateStorageButton()
                .setStoragePath(storageName)
                .setVersions(true)
                .clickCreateButton()
                .selectStorage(storageName)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
    }

    @Test
    @TestCase({"EPMCMBIBPC-813"})
    public void validateShowFilesVersions() {
        file = Utils.createTempFile();
        logout();
        loginAs(admin)
                .library()
                .selectStorage(storageName)
                .createFolder(folderName)
                .uploadFile(file)
                .showFilesVersions(true)
                .assertShowFilesVersionsIsChecked()
                .assertFilesHaveVersions();
    }

    @Test(dependsOnMethods = {"validateShowFilesVersions"})
    @TestCase({"EPMCMBIBPC-815"})
    public void deleteFileByNonAdminUser() {
        logout();
        loginAs(admin);
        givePermissions(user,
                BucketPermission.allow(READ, storageName),
                BucketPermission.allow(WRITE, storageName),
                BucketPermission.deny(EXECUTE, storageName)
        );
        logout();

        loginAs(user)
                .library()
                .selectStorage(storageName)
                .selectFile(file.getName())
                .delete()
                .validateElementNotPresent(file.getName());
        logout();

        loginAs(admin)
                .library()
                .selectStorage(storageName)
                .showFilesVersions(true)
                .validateElementIsPresent(file.getName())
                .selectFile(file.getName())
                .showVersions()
                .selectFile(file.getName() + " (latest)")
                .validateFileHasBackgroundColor(backgroundColorOfDeletedFile)
                .selectNthFileWithName(1, file.getName())
                .ensureVisible(DOWNLOAD, RELOAD);
    }

    @Test(dependsOnMethods = {"deleteFileByNonAdminUser"})
    @TestCase({"EPMCMBIBPC-816"})
    public void validateRestoreFile() {
        navigationMenu()
                .library()
                .selectStorage(storageName)
                .showFilesVersions(true)
                .selectFile(file.getName())
                .showVersions()
                .selectFile(file.getName() + " (latest)")
                .validateFileHasBackgroundColor(backgroundColorOfDeletedFile)
                .selectNthFileWithName(1, file.getName())
                .reload()
                .selectFile(file.getName() + " (latest)")
                .validateFileHasBackgroundColor(backgroundColorOfRestoredFile)
                .showFilesVersions(false)
                .validateElementIsPresent(file.getName());
    }

    @Test(dependsOnMethods = {"validateRestoreFile"})
    @TestCase({"EPMCMBIBPC-820"})
    public void checkFilesVersionsAfterUpdate() {
        anotherFile = Utils.createTempFileWithNameAndSize(file.getName());
        logout();
        loginAs(user)
                .library()
                .selectStorage(storageName)
                .uploadFile(anotherFile);

        logout();

        loginAs(admin)
                .library()
                .selectStorage(storageName)
                .showFilesVersions(true)
                .selectFile(anotherFile.getName())
                .showVersions()
                .selectFile(anotherFile.getName() + " (latest)")
                .validateHasSize((int) anotherFile.length())
                .validateHasDateTime()
                .selectNthFileWithName(1, file.getName())
                .validateHasSize(0)
                .validateHasDateTime()
                .selectNthFileWithName(1, file.getName())
                .validateFileHasBackgroundColor(backgroundColorOfRestoredFile)
                .selectFile(file.getName())
                .ensure(EDIT, visible);
    }

    @Test(dependsOnMethods = {"checkFilesVersionsAfterUpdate"})
    @TestCase({"EPMCMBIBPC-888"})
    public void validateRestoreSpecifiedFileVersion() {
        navigationMenu()
                .library()
                .selectStorage(storageName)
                .showFilesVersions(true)
                .selectFile(file.getName())
                .showVersions()
                .selectNthFileWithName(1, file.getName())
                .reload()
                .showFilesVersions(false)
                .selectFile(file.getName())
                .validateHasSize(0);
    }

    @Test(dependsOnMethods = {"validateRestoreSpecifiedFileVersion"})
    @TestCase({"EPMCMBIBPC-841"})
    public void deleteFileThatHasDeleteMarker() {
        logout();
        loginAs(user)
                .library()
                .selectStorage(storageName)
                .selectFile(file.getName())
                .delete()
                .validateElementNotPresent(file.getName());
        logout();
        loginAs(admin)
                .library()
                .selectStorage(storageName)
                .showFilesVersions(true)
                .selectFile(file.getName())
                .showVersions()
                .validateElementIsPresent(file.getName() + " (latest)")
                .selectNthFileWithName(0, file.getName())
                .clickDeleteButton()
                .cancel()
                .validateElementIsPresent(file.getName() + " (latest)")
                .selectNthFileWithName(0, file.getName())
                .delete()
                .validateElementNotPresent(file.getName());
    }

    @Test(dependsOnMethods = {"deleteFileThatHasDeleteMarker"})
    @TestCase({"EPMCMBIBPC-842"})
    public void validateDeleteEmptyFolder() {
        logout();

        loginAs(user)
                .library()
                .selectStorage(storageName)
                .createFolder(anotherFolderName)
                .selectFolder(anotherFolderName)
                .clickDeleteButton()
                .ok();
        logout();

        loginAs(admin)
                .library()
                .selectStorage(storageName)
                .showFilesVersions(true)
                .validateElementNotPresent(anotherFolderName);
    }

    @Test(dependsOnMethods = {"validateDeleteEmptyFolder"})
    @TestCase({"EPMCMBIBPC-844"})
    public void markToDeleteNotEmptyFolder() {
        navigationMenu()
                .library()
                .selectStorage(storageName)
                .createFolder(anotherFolderName)
                .cd(anotherFolderName)
                .uploadFile(file)
                .cd("..")
                .selectFolder(anotherFolderName)
                .clickDeleteButton()
                .ok()
                .validateElementNotPresent(anotherFolderName)
                .showFilesVersions(true)
                .cd(anotherFolderName)
                .validateElementIsPresent(file.getName());
    }

    @Test(dependsOnMethods = {"markToDeleteNotEmptyFolder"})
    @TestCase({"EPMCMBIBPC-845"})
    public void validateDeleteNotEmptyFolder() {
        navigationMenu()
                .library()
                .selectStorage(storageName)
                .createFolder(deletionFolderName)
                .cd(deletionFolderName)
                .uploadFile(file)
                .cd("..")
                .showFilesVersions(true)
                .selectFolder(deletionFolderName)
                .deleteFromBucket()
                .validateElementNotPresent(deletionFolderName);
    }

    @Test(dependsOnMethods = {"validateDeleteNotEmptyFolder"})
    @TestCase({"EPMCMBIBPC-854"})
    public void readPermissionTestForBucketWithVersions() {
        navigationMenu()
                .library()
                .clickOnCreateStorageButton()
                .setStoragePath(readPermitsStorageName)
                .setVersions(true)
                .clickCreateButton()
                .selectStorage(readPermitsStorageName)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user,
                BucketPermission.deny(READ, readPermitsStorageName),
                BucketPermission.deny(WRITE, readPermitsStorageName),
                BucketPermission.deny(EXECUTE, readPermitsStorageName)
        );
        logout();

        loginAs(user)
                .library()
                .validateStorageIsNotPresent(readPermitsStorageName);
    }

    @Test(dependsOnMethods = {"readPermissionTestForBucketWithVersions"})
    @TestCase({"EPMCMBIBPC-855"})
    public void editPermissionTestForBucketWithVersions() {
        logout();

        loginAs(admin)
                .library()
                .clickOnCreateStorageButton()
                .setStoragePath(editPermitsStorageName)
                .setVersions(true)
                .clickCreateButton()
                .selectStorage(editPermitsStorageName)
                .createFolder(editingFolderName)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user,
                BucketPermission.allow(READ, editPermitsStorageName),
                BucketPermission.deny(WRITE, editPermitsStorageName),
                BucketPermission.deny(EXECUTE, editPermitsStorageName)
        );
        logout();

        loginAs(user)
                .library()
                .validateStorage(editPermitsStorageName)
                .selectStorage(editPermitsStorageName)
                .ensure(UPLOAD, not(visible))
                .selectFolder(editingFolderName)
                .ensureNotVisible(EDIT, DELETE);
    }

    @Test(priority = 10)
    @TestCase({"EPMCMBIBPC-1502"})
    public void deletingBucketAfterDisablingOfVersioning() {
        final File file = Utils.createTempFile();

        logout();
        loginAs(admin)
                .library()
                .clickOnCreateStorageButton()
                .setStoragePath(storage1502)
                .setVersions(true)
                .clickCreateButton()
                .selectStorage(storage1502)
                .uploadFile(file)
                .selectElementsUsingCheckboxes(file.getName())
                .removeAllSelected()
                .clickEditStorageButton()
                .sleep(1, SECONDS)
                .setVersions(false)
                .ok()
                .selectStorage(storage1502)
                .clickEditStorageButton()
                .clickDeleteStorageButton()
                .clickDelete()
                .validateStorageIsNotPresent(storage1502);
    }

    @Test(priority = 10)
    @TestCase({"EPMCMBIBPC-1540"})
    public void deleteOneOfSiblingsFiles() {
        final File file = Utils.createTempFileWithName("file_name");
        final File fileWithTheSameName = Utils.createTempFileWithName("file_name.csv");

        logout();
        loginAs(admin)
                .library()
                .clickOnCreateStorageButton()
                .setStoragePath(storage1540)
                .setVersions(true)
                .clickCreateButton()
                .selectStorage(storage1540)
                .showFilesVersions(false)
                .uploadFile(file)
                .sleep(1, SECONDS)
                .uploadFile(fileWithTheSameName)
                .selectFile(file.getName())
                .delete()
                .showFilesVersions(true)
                .selectFile(file.getName())
                .delete()
                .validateElementIsPresent(fileWithTheSameName.getName());
    }
}

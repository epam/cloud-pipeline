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

import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NfsDataStorageTest extends AbstractBfxPipelineTest implements Navigation {

    private String nfsPrefix = C.NFS_PREFIX;
    private String storage = "epmcmbi-test-nfs-" + Utils.randomSuffix();

    private final String folder = "epmcmbi-test-folder-" + Utils.randomSuffix();
    private File file;
    private final String folderTempName = "epmcmbi-test-folder-temp-name-" + Utils.randomSuffix();
    private final String fileTempName = String.format("epmcmbi-file-temp-name-%d.file", Utils.randomSuffix());
    private final String nfsMountDescription = "Some test description";
    private final String subfolder = "epmcmbi-test-subfolder-" + Utils.randomSuffix();
    private final String deletableStorage = "epmcmbi-test-deletable-nfs-" + Utils.randomSuffix();
    private final String mountPointStorage = "epmcmbi-mount-point-test-" + Utils.randomSuffix();
    private final String tempAlias = "epmcmbi-nfs-test-temp-alias-" + Utils.randomSuffix();
    private final String NfsMountNameSpaces = "nfs mount name with spaces";
    private final String folderNameWithSpaces = "epmcmbi test folder with spaces" + Utils.randomSuffix();

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        reloadPageAndWait();
        Utils.removeStorages(this, deletableStorage, storage, mountPointStorage, NfsMountNameSpaces);
    }

    @Test(priority = 1)
    @TestCase(value = {"EPMCMBIBPC-2274"})
    public void createNfsMountAndValidate() {
        file = Utils.createTempFile();
        navigateToLibrary()
                .createNfsMountWithDescription("/" + storage, storage, nfsMountDescription, nfsPrefix)
                .validateStoragePictogram(storage)
                .selectStorage(storage)
                .uploadFile(file)
                .validateHeader(storage)
                .ensureVisible(REFRESH, EDIT_STORAGE, UPLOAD, CREATE, SELECT_ALL)
                .validateDescriptionIsNotEmpty()
                .validateNfsPath()
                .validateListOfFoldersIsDisplayed();
    }

    @Test(priority = 2, dependsOnMethods = {"createNfsMountAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-2593"})
    public void validateEditForm() {
        navigateToLibrary()
                .selectStorage(storage)
                .clickEditStorageButton()
                .validateEditFormElementsNfsMount()
                .clickCancel();
    }

    @Test(priority = 3, dependsOnMethods = {"validateEditForm"})
    @TestCase(value = {"EPMCMBIBPC-2595"})
    public void NfsMountChangeAlias() {
        navigateToLibrary()
                .selectStorage(storage)
                .clickEditStorageButton()
                .setAlias(tempAlias)
                .clickSaveButton()
                .validateStorage(tempAlias);
        refresh();
        navigateToLibrary()
                .selectStorage(tempAlias)
                .clickEditStorageButton()
                .setAlias(storage)
                .clickSaveButton()
                .validateStorage(storage);
    }

    @Test(priority = 4,dependsOnMethods = {"createNfsMountAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-2276"})
    public void createSubfolderInDataStorage() {
        file = Utils.createTempFile();
        navigateToLibrary()
                .selectStorage(storage)
                .createFolder(folder)
                .uploadFile(file)
                .cd(folder)
                .createFolder(subfolder)
                .uploadFile(file)
                .validateElementIsPresent(subfolder);
    }

    @Test(priority = 5,dependsOnMethods = {"createSubfolderInDataStorage"})
    @TestCase(value = {"EPMCMBIBPC-2597"})
    public void navigateToSubfolderAndBackToTheRoot() {
        navigateToLibrary()
                .selectStorage(storage)
                .cd(folder)
                .validateElementIsPresent(subfolder)
                .cd(subfolder)
                .validateCurrentFolderIsEmpty()
                .cd("..")
                .validateElementIsPresent(subfolder)
                .cd("..")
                .validateElementIsPresent(folder);
    }

    @Test(priority = 6,dependsOnMethods = "createSubfolderInDataStorage")
    @TestCase(value = {"EPMCMBIBPC-2598"})
    public void createFolderInDataStorageWithNameThatAlreadyExists() {
        navigateToLibrary()
                .selectStorage(storage)
                .createFolder(folder)
                .messageShouldAppear(String.format("Could not create a folder in nfs: %s", nfsPrefix + storage));
        clickCanceButtonlIfItIsDisplayed();
        refresh();
    }

    @Test(priority = 7,dependsOnMethods = "createSubfolderInDataStorage")
    @TestCase(value = {"EPMCMBIBPC-2295"})
    public void deleteFolderWithContent() {
        navigateToLibrary()
                .selectStorage(storage)
                .selectFolder(folder)
                .delete()
                .validateElementNotPresent(folder)
                .createFolder(folder)
                .cd(folder)
                .createFolder(subfolder);
    }

    @Test(priority = 8)
    @TestCase(value = {"EPMCMBIBPC-2599"})
    public void editFolderName() {
        navigateToLibrary()
                .selectStorage(storage)
                .selectFolder(folder)
                .renameTo(folderTempName);

        navigateToLibrary()
                .selectStorage(storage)
                .validateElementIsPresent(folderTempName)
                .selectFolder(folderTempName)
                .renameTo(folder);
    }

    @Test(priority = 9)
    @TestCase(value = {"EPMCMBIBPC-2600"})
    public void editFileName() {
        file = Utils.createTempFile();
        navigateToLibrary()
                .selectStorage(storage)
                .uploadFile(file)
                .selectFile(file.getName())
                .renameTo(fileTempName);

        navigateToLibrary()
                .selectStorage(storage)
                .validateElementIsPresent(fileTempName)
                .selectFile(fileTempName)
                .renameTo(file.getName());
    }

    @Test(priority = 10, dependsOnMethods = {"createNfsMountAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-2275"})
    public void createDataStorageWithNameThatAlreadyExists() {
        navigateToLibrary()
                .createNfsMount("/" + storage, storage, nfsPrefix)
                .messageShouldAppear(String.format("Error: data storage with name: '%s' or path: '%s' already exists.",
                        storage, nfsPrefix + storage));
        clickCanceButtonlIfItIsDisplayed();
        refresh();
    }

    @Test(priority = 11,dependsOnMethods = {"createNfsMountAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-2594"})
    public void downloadFileAndValidateNfsMount() {
        file = Utils.createTempFileWithNameAndSize(file.getName());

        final File destinationFile = Paths.get(C.DOWNLOAD_FOLDER).resolve(file.getName()).toFile();

        destinationFile.deleteOnExit();

        final int expectedFileSize =
                navigateToLibrary()
                        .selectStorage(storage)
                        .uploadFile(file)
                        .selectFile(file.getName())
                        .download()
                        .size();

        assertFileSize(destinationFile, expectedFileSize);
    }

    @Test(priority = 12, dependsOnMethods = {"createNfsMountAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-2601"})
    public void deleteSeveralFilesNfsMount() {
        File newFile1 = Utils.createTempFile("1");
        File newFile2 = Utils.createTempFile("2");
        String newFolder1 = "deletable_1" + Utils.randomSuffix();
        String newFolder2 = "deletable_2" + Utils.randomSuffix();

                navigateToLibrary()
                .selectStorage(storage)
                .createFolder(newFolder1)
                .createFolder(newFolder2)
                .uploadFile(newFile1)
                .uploadFile(newFile2)
                .selectElementsUsingCheckboxes(newFile1.getName(), newFile2.getName(), newFolder1, newFolder2)
                .removeAllSelected()
                .sleep(1, SECONDS)
                .validateElementNotPresent(newFile1.getName())
                .validateElementNotPresent(newFile2.getName())
                .validateElementNotPresent(newFolder1)
                .validateElementNotPresent(newFolder2);
    }

    @Test(priority = 13, dependsOnMethods = {"createNfsMountAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-2602"})
    public void validateSelectPageButton() {
        File newFile1 = Utils.createTempFile("1");
        File newFile2 = Utils.createTempFile("2");

        navigateToLibrary()
                .selectStorage(storage)
                .createFolder("selectable_1" + Utils.randomSuffix())
                .createFolder("selectable_2" + Utils.randomSuffix())
                .uploadFile(newFile1)
                .uploadFile(newFile2)
                .selectPage()
                .validateAllFilesAreSelected()
                .ensureVisible(REMOVE_ALL, CLEAR_SELECTION)
                .ensure(SELECT_ALL, not(visible));
    }

    @Test(priority = 14)
    @TestCase(value = {"EPMCMBIBPC-2307"})
    public void validateOfNfsMountUnregister() {
        file = Utils.createTempFile();
        navigateToLibrary()
                .createNfsMountWithDescription("/" + deletableStorage, deletableStorage,
                        nfsMountDescription, nfsPrefix)
                .selectStorage(deletableStorage)
                .createFolder(folder)
                .uploadFile(file)
                .clickEditStorageButton()
                .clickDeleteStorageButton()
                .clickUnregister()
                .validateStorageIsNotPresent(deletableStorage)
                .createNfsMount("/" + deletableStorage, deletableStorage, nfsPrefix)
                .validateStorage(deletableStorage)
                .selectStorage(deletableStorage)
                .validateElementIsPresent(folder)
                .validateElementIsPresent(file.getName())
                .validateHeader(deletableStorage);
        refresh();
    }

    @Test(priority = 15)
    @TestCase(value = {"EPMCMBIBPC-2300"})
    public void validateProhibitedMountPoint() {
        navigateToLibrary()
                .createNfsMount("/" + mountPointStorage, mountPointStorage, nfsPrefix);
        Stream<String> mountPoints = Stream.of("/", "/etc", "/runs", "/common", "/bin", "/opt", "/var", "/home",
                "/root", "/sbin", "/sys", "/usr", "/boot", "/dev", "/lib", "/proc", "/tmp");
        mountPoints.forEach((value) ->
                navigateToLibrary()
                        .selectStorage(mountPointStorage)
                        .clickEditStorageButton()
                        .setMountPoint(value)
                        .clickSaveButton()
                        .messageShouldAppear(String.format(
                                "Could not create nfs datastorage '%s', mount point '%s' is in black list!", nfsPrefix +
                                        mountPointStorage, value))
                        .also(this::clickCanceButtonlIfItIsDisplayed)
        );
}

    @Test(priority = 16)
    @TestCase(value = {"EPMCMBIBPC-2304"})
    public void validateProhibitedNfsMountPath() {
        navigateToLibrary()
                .createNfsMount("/", nfsPrefix, nfsPrefix)
                .messageShouldAppear("Invalid path")
                .also(this::clickCanceButtonlIfItIsDisplayed);

    }

    @Test(priority = 17)
    @TestCase(value = {"EPMCMBIBPC-2603"})
    public void createNfsMountWithSpacesAndValidate() {
        navigateToLibrary()
                .createNfsMountWithDescription("/" + NfsMountNameSpaces, NfsMountNameSpaces,
                        nfsMountDescription, nfsPrefix)
                .selectStorage(NfsMountNameSpaces)
                .validateHeader(NfsMountNameSpaces)
                .createFolder(folder)
                .ensureVisible(REFRESH, EDIT_STORAGE, UPLOAD, CREATE, SELECT_ALL)
                .validateDescriptionIsNotEmpty()
                .validateNfsPath();
    }

    @Test(priority = 18, dependsOnMethods = {"createNfsMountWithSpacesAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-2604"})
    public void uploadInNfsMountFilesAndFoldersWithSpacesAndValidate() {
        file = Utils.createTempFile("file with spaces ");
        navigateToLibrary()
                .selectStorage(NfsMountNameSpaces)
                .createFolder(folderNameWithSpaces)
                .uploadFile(file)
                .validateElementIsPresent(folderNameWithSpaces)
                .validateElementIsPresent(file.getName())
                .cd(folderNameWithSpaces)
                .uploadFile(file)
                .validateElementIsPresent(file.getName())
                .createFolder(folderNameWithSpaces)
                .validateElementIsPresent(folderNameWithSpaces);

    }

    @Test(priority = 19, dependsOnMethods = {"uploadInNfsMountFilesAndFoldersWithSpacesAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-2605"})
    public void deleteFromNfsMountFilesAndFoldersWithSpacesAndValidate() {
        file = Utils.createTempFile("file with spaces ");
        navigateToLibrary()
                .selectStorage(NfsMountNameSpaces)
                .uploadFile(file)
                .selectFolder(folderNameWithSpaces)
                .delete()
                .validateElementNotPresent(folderNameWithSpaces)
                .selectFile(file.getName())
                .delete()
                .validateElementNotPresent(file.getName());
    }



    private void assertFileSize(File file, int expectedFileSize) {
        long actualFileSize = file.length();

        Assert.assertTrue(file.exists());
        Assert.assertEquals(actualFileSize, expectedFileSize);
    }

    private void reloadPageAndWait() {
        refresh();
        $(byClassName("pipelines-library-tree-node-folder_root")).should(appear);
    }

    private void clickCanceButtonlIfItIsDisplayed() {
        if($(button("Cancel")).isDisplayed()){
            $(button("Cancel")).click();
        }
    }

    private PipelinesLibraryAO navigateToLibrary() {
        return navigationMenu().library();
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

}

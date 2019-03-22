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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.StorageContentAO.folderWithName;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DataStoragesTest extends AbstractBfxPipelineTest implements Navigation {

    private String storage = "epmcmbi-test-storage-" + Utils.randomSuffix();
    private final String deletableStorage = "epmcmbi-test-deletable-storage-" + Utils.randomSuffix();
    private final String editableStorage = "epmcmbi-test-editable-storage-" + Utils.randomSuffix();
    private final String tempAlias = "epmcmbi-test-temp-alias-" + Utils.randomSuffix();

    private final String refreshingTestStorage = "refreshing-test-storage-" + Utils.randomSuffix();
    private String presetStorage = "preset-test-storage" + Utils.randomSuffix();

    private final String folder = "epmcmbi-test-folder-" + Utils.randomSuffix();
    private final String folderTempName = "epmcmbi-test-folder-temp-name-" + Utils.randomSuffix();
    private final String subfolder = "epmcmbi-test-subfolder-" + Utils.randomSuffix();
    private final String fileTempName = String.format("epmcmbi-file-temp-name-%d.file", Utils.randomSuffix());
    private final String prefixStoragePath = String.format("%s://", C.STORAGE_PREFIX);
    private File file;

    @BeforeClass
    public void createPresetStorage() {
        file = Utils.createTempFile();
        navigateToLibrary()
            .createStorage(presetStorage)
            .selectStorage(presetStorage)
            .createFolder(folder)
            .uploadFile(file)
            .cd(folder)
            .createFolder("subfolder-1-" + Utils.randomSuffix())
            .createFolder("subfolder-2-" + Utils.randomSuffix())
            .uploadFile(Utils.createTempFile("1"))
            .uploadFile(Utils.createTempFile("2"));
    }

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        reloadPageAndWait();
        clickCanceButtonlIfItIsDisplayed();

        Utils.removeStorages(this, storage, deletableStorage, editableStorage, tempAlias, refreshingTestStorage,
                presetStorage);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-475"})
    public void clickOnRefreshButton() {
        final String folder = "tempFolder";

        navigateToLibrary()
            .createStorage(refreshingTestStorage)
            .selectStorage(refreshingTestStorage)
            .inAnotherTab(library -> library.createFolder(folder))
            .ensure(folderWithName(folder), not(exist).because(String.format(
                "Folder with name %s is not supposed to appear until the page will be refreshed.", folder)
            ))
            .clickRefreshButton()
            .ensure(folderWithName(folder), visible.because(String.format(
                "Folder with name %s should appear after the page has been refreshed.", folder)
            ))
            .validateElementIsPresent(folder)
            .selectFolder(folder)
            .delete();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-448"})
    public void createDataStorageAndValidate() {
        navigateToLibrary()
            .createStorage(storage)
            .validateStorage(storage)
            .validateStoragePictogram(storage)
            .selectStorage(storage)
            .createFolder(folder)
            .validateHeader(storage)
            .ensureVisible(REFRESH, EDIT_STORAGE, UPLOAD, CREATE, SELECT_ALL)
            .validateStoragePath()
            .validateListOfFoldersIsDisplayed();
    }

    @Test(dependsOnMethods = {"createDataStorageAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-491"})
    public void createDataStorageWithNameThatAlreadyExists() {
        navigateToLibrary()
            .createStorage(storage)
            .messageShouldAppear(String.format("'%s' already exist", storage));
        clickCanceButtonlIfItIsDisplayed();
        refresh();
    }

    @Test(dependsOnMethods = {"createDataStorageAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-1017"})
    public void clickCrossButtonInDataStorageDeletionDialog() {
        navigateToLibrary()
            .selectStorage(storage)
            .clickEditStorageButton()
            .clickDeleteStorageButton()
            .clickCrossButton()
            .cancel()
            .validateStorage(storage);
    }

    @Test(dependsOnMethods = {"createDataStorageAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-492"})
    public void createFolderInDataStorageWithNameThatAlreadyExists() {
        navigateToLibrary()
            .selectStorage(storage)
            .createFolder(folder)
            .messageShouldAppear("Folder already exists");

        refresh();
    }

    @Test(dependsOnMethods = {"createDataStorageAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-454"})
    public void createSubfolderInDataStorage() {
        navigateToLibrary()
            .selectStorage(storage)
            .cd(folder)
            .createFolder(subfolder)
            .validateElementIsPresent(subfolder);
    }

    @Test(dependsOnMethods = {"createSubfolderInDataStorage"})
    @TestCase(value = {"EPMCMBIBPC-455"})
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

    @Test(dependsOnMethods = {"createSubfolderInDataStorage"})
    @TestCase(value = {"EPMCMBIBPC-469"})
    public void navigateToFolderUsingAddressRow() {
        navigateToLibrary()
            .selectStorage(storage)
            .navigateUsingAddressBar(folder)
            .validateElementIsPresent(subfolder);
    }

    @Test(dependsOnMethods = {"createDataStorageAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-456"})
    public void uploadFile() {
        final File uploadedFile = Utils.createTempFile();

        navigateToLibrary()
            .selectStorage(storage)
            .uploadFile(uploadedFile)
            .validateElementIsPresent(uploadedFile.getName())
            .selectFile(uploadedFile.getName())
            .delete();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-457"})
    public void downloadFileAndValidate() {
        final File destinationFile = Paths.get(C.DOWNLOAD_FOLDER).resolve(file.getName()).toFile();
        destinationFile.deleteOnExit();

        final int expectedFileSize =
            navigateToLibrary()
                .selectStorage(presetStorage)
                .selectFile(file.getName())
                .download()
                .size();

        assertFileSize(destinationFile, expectedFileSize);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-458"})
    public void editFolderName() {
        navigateToLibrary()
            .selectStorage(presetStorage)
            .selectFolder(folder)
            .renameTo(folderTempName);

        navigateToLibrary()
            .selectStorage(presetStorage)
            .validateElementIsPresent(folderTempName)
            .selectFolder(folderTempName)
            .renameTo(folder);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-468"})
    public void editFileName() {
        navigateToLibrary()
            .selectStorage(presetStorage)
            .selectFile(file.getName())
            .renameTo(fileTempName);

        navigateToLibrary()
            .selectStorage(presetStorage)
            .validateElementIsPresent(fileTempName)
            .selectFile(fileTempName)
            .renameTo(file.getName());
    }

    @Test(dependsOnMethods = "createSubfolderInDataStorage")
    @TestCase(value = {"EPMCMBIBPC-459"})
    public void deleteFolder() {
        navigateToLibrary()
            .selectStorage(storage)
            .cd(folder)
            .selectFolder(subfolder)
            .delete()
            .validateCurrentFolderIsEmpty()
            .createFolder(subfolder);
    }

    @Test(dependsOnMethods = "createSubfolderInDataStorage")
    @TestCase(value = {"EPMCMBIBPC-460"})
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

    @Test
    @TestCase(value = {"EPMCMBIBPC-461"})
    public void deleteSeveralFolders() {
        final String newFolder1 = "new-folder-1-" + Utils.randomSuffix();
        final String newFolder2 = "new-folder-2-" + Utils.randomSuffix();

        navigateToLibrary()
            .selectStorage(presetStorage)
            .createFolder(newFolder1)
            .createFolder(newFolder2)
            .selectElementsUsingCheckboxes(newFolder1, newFolder2)
            .removeAllSelected()
            .sleep(30, SECONDS)
            .validateElementNotPresent(newFolder1)
            .validateElementNotPresent(newFolder2);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-462"})
    public void deleteSeveralFiles() {
        final File newFile1 = Utils.createTempFile("1");
        final File newFile2 = Utils.createTempFile("2");

        navigateToLibrary()
            .selectStorage(presetStorage)
            .uploadFile(newFile1)
            .uploadFile(newFile2)
            .selectElementsUsingCheckboxes(newFile1.getName(), newFile2.getName())
            .removeAllSelected()
            .validateElementNotPresent(newFile1.getName())
            .validateElementNotPresent(newFile2.getName());
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-467"})
    public void validateSelectPageButton() {
        navigateToLibrary()
            .selectStorage(presetStorage)
            .cd(folder)
            .selectPage()
            .validateAllFilesAreSelected()
            .ensureVisible(REMOVE_ALL, CLEAR_SELECTION)
            .ensure(SELECT_ALL, not(visible));
    }

    @Test(dependsOnMethods = "validateSelectPageButton")
    @TestCase(value = {"EPMCMBIBPC-464"})
    public void clearSelection() {
        navigateToLibrary()
            .selectStorage(presetStorage)
            .cd(folder)
            .selectPage()
            .clearSelection()
            .validateNoElementsAreSelected()
            .ensure(SELECT_ALL, visible)
            .ensureNotVisible(REMOVE_ALL, CLEAR_SELECTION);
    }

    @Test(dependsOnMethods = {"createDataStorageAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-470"})
    public void validateEditForm() {
        navigateToLibrary()
            .selectStorage(storage)
            .clickEditStorageButton()
            .validateEditFormElements()
            .clickCancel();
    }

    @Test(dependsOnMethods = {"validateEditForm"})
    @TestCase(value = {"EPMCMBIBPC-471"})
    public void createStorageAndChangeAlias() {
        navigateToLibrary()
            .createStorage(editableStorage)
            .selectStorage(editableStorage)
            .clickEditStorageButton()
            .setAlias(tempAlias)
            .clickSaveButton()
            .validateStorage(tempAlias);
        navigateToLibrary()
            .selectStorage(tempAlias)
            .clickEditStorageButton()
            .setAlias(editableStorage)
            .clickSaveButton();
    }

    @Test(dependsOnMethods = {"createStorageAndChangeAlias"})
    @TestCase(value = {"EPMCMBIBPC-472"})
    public void changeDescriptionAndValidate() {
        navigateToLibrary()
            .selectStorage(editableStorage)
            .clickEditStorageButton()
            .setDescription("new description")
            .clickSaveButton()
            .selectStorage(editableStorage)
            .validateDescription("new description");
    }

    @Test(dependsOnMethods = {"changeDescriptionAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-474"})
    public void changeLtsDurationAndValidate() {
        navigateToLibrary()
            .selectStorage(editableStorage)
            .clickEditStorageButton()
            .setDurations("", "5")
            .clickSaveButton()
            .selectStorage(editableStorage)
            .validateLtsDuration("5");
    }

    @Test(dependsOnMethods = {"changeLtsDurationAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-473"})
    public void changeStsDurationAndValidate() {
        navigateToLibrary()
            .selectStorage(editableStorage)
            .clickEditStorageButton()
            .setDurations("3","5")
            .clickSaveButton()
            .selectStorage(editableStorage)
            .validateStsDuration("3");
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-476"})
    public void createAndUnregisterBucket() {
        navigateToLibrary()
            .createStorage(deletableStorage)
            .selectStorage(deletableStorage)
            .clickEditStorageButton()
            .clickDeleteStorageButton()
            .clickUnregister()
            .validateStorageIsNotPresent(deletableStorage);
    }

    @Test(dependsOnMethods = {"validateCancelButtonForCreateExistingBucketPopUp"})
    @TestCase(value = {"EPMCMBIBPC-477"})
    public void addExistingBucket() {
        navigateToLibrary()
            .clickOnCreateExistingStorageButton()
            .setPath(deletableStorage)
            .setAlias(deletableStorage)
            .clickCreateButton()
            .validateStorage(deletableStorage);
    }

    @Test(dependsOnMethods = {"addExistingBucket"})
    @TestCase(value = {"EPMCMBIBPC-478"})
    public void tryToCreateDeletedBucket() {
        navigateToLibrary()
            .selectStorage(deletableStorage)
            .clickEditStorageButton()
            .clickDeleteStorageButton()
            .clickDelete()
            .validateStorageIsNotPresent(deletableStorage)
            .sleep(30, SECONDS);

        navigateToLibrary()
            .clickOnCreateExistingStorageButton()
            .setPath(deletableStorage)
            .setAlias(deletableStorage)
            .clickCreateAndCancel()
            .messageShouldAppear(String.format("Error: data storage with name: '%s' or path: '%s' was not found.",
                    deletableStorage, deletableStorage))
            .validateStorageIsNotPresent(deletableStorage);
    }

    @Test(dependsOnMethods = {"changeLtsDurationAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-481"})
    public void validateCancelButtonForEditStorageMenu() {
        navigateToLibrary()
            .selectStorage(editableStorage)
            .clickEditStorageButton()
            .setAlias("abcdef")
            .setDescription("asdfas")
            .setDurations("31", "13")
            .clickCancel()
            .selectStorage(editableStorage)
            .validateStsDuration("3")
            .validateLtsDuration("5")
            .validateDescription("new description");
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-482"})
    public void validateCancelButtonForEditFilePopUp() {
        navigateToLibrary()
            .selectStorage(presetStorage)
            .selectFile(file.getName())
            .clickEditButton()
            .typeInField("new name")
            .cancel()
            .validateElementIsPresent(file.getName());
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-483"})
    public void validateCancelButtonForEditFolderPopUp() {
        navigateToLibrary()
            .selectStorage(presetStorage)
            .selectFolder(folder)
            .clickEditButton()
            .typeInField("new name")
            .cancel()
            .validateElementIsPresent(folder);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-484"})
    public void validateCancelButtonForDeleteFolderPopUp() {
        navigateToLibrary()
            .selectStorage(presetStorage)
            .selectFolder(folder)
            .clickDeleteButton()
            .cancel()
            .validateElementIsPresent(folder);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-485"})
    public void validateCancelButtonForDeleteFilePopUp() {
        navigateToLibrary()
            .selectStorage(presetStorage)
            .selectFile(file.getName())
            .clickDeleteButton()
            .cancel()
            .validateElementIsPresent(file.getName());
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-486"})
    public void validateCancelButtonForCreateFolderPopUp() {
        final String folderName = "aaaa";
        navigateToLibrary()
            .selectStorage(presetStorage)
            .clickOnCreateFolderButton()
            .typeInField(folderName)
            .cancel()
            .validateElementNotPresent(folderName);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-487"})
    public void validateCancelButtonForCreateBucketPopUp() {
        final String storageName = "abcdef";
        navigateToLibrary()
            .clickOnCreateStorageButton()
            .setPath(prefixStoragePath + storageName)
            .clickCancel()
            .validateStorageIsNotPresent(storageName);
    }

    @Test(dependsOnMethods = "createAndUnregisterBucket")
    @TestCase(value = {"EPMCMBIBPC-488"})
    public void validateCancelButtonForCreateExistingBucketPopUp() {
        navigateToLibrary()
            .clickOnCreateExistingStorageButton()
            .setPath(prefixStoragePath + deletableStorage)
            .clickCancel()
            .validateStorageIsNotPresent(deletableStorage);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-489"})
    public void validateCancelButtonForDeleteBucketPopUp() {
        navigateToLibrary()
            .selectStorage(storage)
            .clickEditStorageButton()
            .clickDeleteStorageButton()
            .clickCancel()
            .clickCancel()
            .validateStorage(storage);
    }

    @Test(dependsOnMethods = {"createDataStorageAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-490"})
    public void validateCancelDeletingSeveralFolders() {
        final String folder1 = "folder1";
        final String folder2 = "folder2";
        navigateToLibrary()
            .selectStorage(storage)
            .createFolder(folder1)
            .createFolder(folder2)
            .selectElementsUsingCheckboxes(folder1, folder2)
            .clickOnRemoveAllSelectedButton()
            .clickCancelInRemoveAllSelectedDialog()
            .validateElementIsPresent(folder1)
            .validateElementIsPresent(folder2);

        navigateToLibrary()
            .selectStorage(storage)
            .selectElementsUsingCheckboxes(folder1, folder2)
            .removeAllSelected();
    }

    private void assertFileSize(File file, int expectedFileSize) {
        final long actualFileSize = file.length();

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

    public void setPresetStorage(String presetStorage) {
        this.presetStorage = presetStorage;
    }
}

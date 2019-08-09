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

import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.mixins.StorageHandling;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import com.epam.pipeline.autotests.utils.listener.ConditionalTestAnalyzer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.NEXT_PAGE;
import static java.util.stream.Collectors.toList;

@Listeners(value = ConditionalTestAnalyzer.class)
public class StoragePaginationTest extends AbstractBfxPipelineTest
        implements StorageHandling {

    private final String foldersStorage = "folders-pagination-storage-" + Utils.randomSuffix();
    private final String filesStorage = "files-pagination-storage-" + Utils.randomSuffix();
    private final String folderPrefix = "folder-";
    private final String fileSuffix = "file-";
    private final int pageSize = 40;
    private final String nextPageAppearingReason =
            String.format("There are more than %s elements, so the second page should appear", pageSize);

    @BeforeClass
    public void initStorages() {
        createStorage(foldersStorage);
        createStorage(filesStorage);
    }

    @AfterClass(alwaysRun = true)
    public void deleteStorages() {
        removeStorage(foldersStorage);
        removeStorage(filesStorage);
    }

    @Test
    @TestCase({"EPMCMBIBPC-1035"})
    public void storagePaginationButtonsShouldWorkCorrectly() {
        final int foldersNumber = 42;
        final List<String> foldersNames = generateNames(foldersNumber, folderPrefix);
        final List<String> firstPageFolders = foldersNames.subList(0, pageSize);
        final String[] secondPageFolders = foldersNames.subList(pageSize, foldersNames.size()).toArray(new String[0]);

        library().selectStorage(foldersStorage);

        foldersNames
                .forEach(folderName -> createFolderAndValidateItsAppearanceOnFirstPage(firstPageFolders, folderName));

        storageContent()
                .validateFoldersOrderIsAlphabetical()
                .nextPage()
                .also(pageContains(secondPageFolders))
                .selectPage()
                .elementsShouldBeSelected(secondPageFolders)
                .previousPage()
                .shouldContainNumberOfElements(pageSize)
                .validateNoElementsAreSelected()
                .nextPage()
                .elementsShouldBeSelected(secondPageFolders);
    }

    @Test
    @TestCase({"EPMCMBIBPC-1068"})
    @CloudProviderOnly(values = {Cloud.AWS, Cloud.GCP})
    public void filesVersionModeOnShouldEnablePaginationOnRemovedFiles() {
        final int filesNumber = 51;
        final List<String> filesSuffixes = generateNames(filesNumber, fileSuffix);
        final List<String> firstPageFiles = filesSuffixes.subList(0, pageSize);
        final String[] secondPageFiles = filesSuffixes.subList(pageSize, filesSuffixes.size()).toArray(new String[0]);

        library().selectStorage(filesStorage);

        filesSuffixes.stream()
                .map(suffix -> Utils.createTempFile("%s", suffix))
                .forEach(file -> uploadFileAndValidateItsAppearanceOnFirstPage(firstPageFiles, file));

        storageContent()
                .ensure(NEXT_PAGE, enabled.because(nextPageAppearingReason))
                .selectPage()
                .removeAllSelectedElements()
                .selectPage()
                .removeAllSelectedElements()
                .showFilesVersions(true)
                .ensure(NEXT_PAGE, enabled.because(nextPageAppearingReason))
                .nextPage()
                .also(pageContains(secondPageFiles));
    }

    private List<String> generateNames(final int elementsNumber, final String prefix) {
        return IntStream.range(1, elementsNumber + 1)
                .mapToObj(index -> prefix + index)
                .sorted()
                .collect(toList());
    }

    private void createFolderAndValidateItsAppearanceOnFirstPage(final List<String> firstPageFolders, final String folderName) {
        storageContent().createFolder(folderName);

        if (firstPageFolders.contains(folderName)) {
            storageContent().validateElementIsPresent(folderName);
        } else {
            storageContent().validateElementNotPresent(folderName);
        }
    }

    private void uploadFileAndValidateItsAppearanceOnFirstPage(final List<String> firstPageFiles, final File file) {
        storageContent().uploadFileWithoutValidation(file);

        if (firstPageFiles.contains(file.getName())) {
            storageContent().validateElementIsPresent(file.getName());
        } else {
            storageContent().validateElementNotPresent(file.getName());
        }
    }

    private Consumer<StorageContentAO> pageContains(final String[] folders) {
        return page -> Arrays.stream(folders).forEach(folder -> page.ensure(byText(folder), visible));
    }

    private StorageContentAO storageContent() {
        return new StorageContentAO();
    }
}

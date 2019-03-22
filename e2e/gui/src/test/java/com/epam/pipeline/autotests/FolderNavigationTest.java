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

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.utils.TestCase;
import org.openqa.selenium.support.Color;

import org.openqa.selenium.By;
import org.openqa.selenium.support.Colors;
import org.testng.annotations.*;

import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.have;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.browserItem;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.collapsedItem;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.expandedItem;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.searchInput;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.searchResult;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.searchResultOf;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.selectedItem;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.switcherOf;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.titleOfTreeItem;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.treeItem;
import static com.epam.pipeline.autotests.ao.StorageContentAO.folderWithName;
import static com.epam.pipeline.autotests.utils.Conditions.backgroundColor;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public class FolderNavigationTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final Color SEARCH_COLOR = Colors.YELLOW.getColorValue();
    private static final Condition backgroundColorNotBlackOrWhite = Condition.and(
        "background color neither white nor black",
        not(backgroundColor(Colors.WHITE.getColorValue())),
        not(backgroundColor(Colors.BLACK.getColorValue()))
    );
    private final String folder315 = resourceName("epmcmbibpc-315");
    // Actual folder1072 is never created only lower and upper cased versions
    private final String folder1072 = resourceName("epmcmbibpc-1072");
    private final String lowerCased1072 = folder1072.toLowerCase();
    private final String upperCased1072 = folder1072.toUpperCase();
    private final String storage1072 = resourceName("epmcmbibpc-1072");

    private final String parentFolder = resourceName("folder-navigation-test");
    private final String childFolder = resourceName("folder-navigation-test");
    private final String pipeline = resourceName("folder-navigation-test");

    private final By rootFolder = treeItem;
    private final By parentItem = treeItem(parentFolder);
    private final By childItem = treeItem(childFolder);
    private final By pipelineItem = treeItem(pipeline);

    @BeforeClass
    public void createTestingStorage() {
        pipelinesLibrary()
            .createStorage(storage1072);
    }

    @BeforeClass
    public void createTestingStructure() {
        pipelinesLibrary()
            .createFolder(parentFolder)
            .cd(parentFolder)
            .createFolder(childFolder)
            .cd(childFolder)
            .createPipeline(pipeline)
            .sleep(1, SECONDS)
            .collapseItem(childFolder)
            .collapseItem(parentFolder);
    }

    @AfterClass(alwaysRun = true)
    public void removeTestingStructure() {
        sleep(5, SECONDS);
        pipelinesLibrary()
            .performIf(rootFolder, collapsedItem, tree -> tree.click(rootFolder))
            .performIf(treeItem(parentFolder), exist, page -> page.click(treeItem(parentFolder))
                    .click(treeItem(childFolder)))
            .removePipelineIfExists(pipeline)
            .removeFolderIfExists(childFolder)
            .removeFolderIfExists(parentFolder);
        pipelinesLibrary()
            .performIf(rootFolder, collapsedItem, tree -> tree.click(rootFolder))
            .removeFolder(folder315)
            .removeFolderIfExists(lowerCased1072)
            .removeFolderIfExists(upperCased1072)
            .removeStorage(storage1072);
    }

    @Test
    @TestCase("EPMCMBIBPC-1072")
    public void caseSensitiveValidationInTheStorage() {
        pipelinesLibrary()
            .selectStorage(storage1072)
            .createFolder(lowerCased1072)
            .ensure(folderWithName(lowerCased1072), exist.because("folder has been created"))
            .createFolder(upperCased1072)
            .ensure(folderWithName(upperCased1072), exist.because("folder has been created"))
            .rm(lowerCased1072)
            .ensure(folderWithName(lowerCased1072), not(exist).because("folder has been removed"))
            .ensure(folderWithName(upperCased1072), exist.because(
                "folder shouldn't be affected by removing a folder with the same name in another register"
            ))
            .rm(upperCased1072)
            .ensure(folderWithName(upperCased1072), not(exist).because("folder has been removed"));
    }

    @Test
    @TestCase("EPMCMBIBPC-315")
    public void folderNavigationValidationTest() {
        pipelinesLibrary()
            .createFolder(folder315)
            .ensure(treeItem(folder315), appears)
            .cd(folder315)
            .ensure(StorageContentAO.browserItem(".."), appears);
    }

    @Test
    @TestCase("EPMCMBIBPC-400")
    public void clearSearchFieldTest() {
        final By rootFolder = treeItem;
        pipelinesLibrary()
            .setValue(searchInput, $(titleOfTreeItem(rootFolder)).shouldBe(visible).text())
            .enter()
            .ensure(rootFolder, visible);
    }

    @Test
    @TestCase("EPMCMBIBPC-333")
    public void successfulFolderSearchTest() {
        final By titleOfTreeItem = titleOfTreeItem(parentItem);
        pipelinesLibrary()
            .setValue(searchInput, parentFolder)
            .enter()
            .ensure(titleOfTreeItem, contains(searchResult))
            .ensure(searchResultOf(titleOfTreeItem), have(backgroundColor(SEARCH_COLOR)))
            .ensure(childItem, visible);
    }

    @Test
    @TestCase("EPMCMBIBPC-334")
    public void subFolderSearchTest() {
        final By titleOfTreeItem = titleOfTreeItem(childItem);
        pipelinesLibrary()
            .setValue(searchInput, childFolder)
            .enter()
            .ensure(titleOfTreeItem, contains(searchResult))
            .ensure(searchResultOf(titleOfTreeItem), have(backgroundColor(SEARCH_COLOR)))
            .ensure(pipelineItem, visible);
    }

    @Test
    @TestCase("EPMCMBIBPC-335")
    public void negativeFolderSearchTest() {
        final String nonExistentFolder = resourceName("epmcmbibpc-335");
        pipelinesLibrary()
            .setValue(searchInput, nonExistentFolder)
            .enter()
            .ensure(rootFolder, collapsedItem)
            .ensure(searchResult, not(exist))
            .click(switcherOf(rootFolder))
            .sleep(1, SECONDS);
    }

    @Test
    @TestCase("EPMCMBIBPC-337")
    public void expandFolderTest() {
        pipelinesLibrary()
            .ensure(parentItem, collapsedItem)
            .ensure(childItem, not(visible))
            .click(switcherOf(parentItem))
            .ensure(parentItem, expandedItem)
            .ensure(childItem, visible);
    }

    @Test
    @TestCase("EPMCMBIBPC-336")
    public void collapseFolderTest() {
        pipelinesLibrary()
            .performIf(parentItem, collapsedItem, tree -> tree.click(parentItem))
            .ensure(parentItem, expandedItem)
            .ensure(childItem, visible)
            .click(switcherOf(parentItem))
            .ensure(parentItem, collapsedItem)
            .ensure(childItem, not(visible));
    }

    @Test
    @TestCase("EPMCMBIBPC-338")
    public void selectingFolderTest() {
        pipelinesLibrary()
            .click(parentItem)
            .ensure(parentItem, expandedItem, selectedItem)
            .ensure(childItem, visible)
            .collapseItem(parentFolder);
    }

    @Test
    @TestCase("EPMCMBIBPC-390")
    public void selectingPipelineTest() {
        pipelinesLibrary()
            .click(parentItem)
            .click(childItem)
            .click(pipelineItem)
            .ensure(pipelineItem, expandedItem, selectedItem, backgroundColorNotBlackOrWhite)
            .ensure(visible(browserItem), have(text("draft")))
            .collapseItem(pipeline)
            .collapseItem(childFolder)
            .collapseItem(parentFolder);
    }

    @Test
    @TestCase("EPMCMBIBPC-394")
    public void collapsePipelineTest() {
        pipelinesLibrary()
            .click(parentItem)
            .click(childItem)
            .click(pipelineItem)
            .ensure(pipelineItem, expandedItem, selectedItem, backgroundColorNotBlackOrWhite)
            .click(switcherOf(pipelineItem))
            .ensure(pipelineItem, collapsedItem, selectedItem, backgroundColorNotBlackOrWhite)
            .ensure(visible(browserItem), have(text("draft")))
            .collapseItem(childFolder)
            .collapseItem(parentFolder);
    }

    @Test
    @TestCase("EPMCMBIBPC-395")
    public void selectingPipelineVersionTest() {
        pipelinesLibrary()
            .click(parentItem)
            .click(childItem)
            .click(pipelineItem)
            .ensure(pipelineItem, expandedItem, selectedItem, backgroundColorNotBlackOrWhite)
            .collapseItem(pipeline)
            .collapseItem(childFolder)
            .collapseItem(parentFolder);
    }

    @Test(priority = 100)
    @TestCase("EPMCMBIBPC-316")
    public void deleteFolderWithSubitemTest() {
        pipelinesLibrary()
                .removeNotEmptyFolder(parentFolder)
                .setValue(searchInput, parentFolder)
                .enter()
                .ensure(parentItem, not(visible));
    }

    private PipelinesLibraryAO pipelinesLibrary() {
        return library().resetMouse().clickRoot();
    }
}

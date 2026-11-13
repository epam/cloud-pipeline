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
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.DocumentTabAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners;
import com.epam.pipeline.autotests.utils.TestCase;
import org.openqa.selenium.support.Color;

import org.openqa.selenium.By;
import org.openqa.selenium.support.Colors;
import org.testng.annotations.*;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.have;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.browser;
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
import static com.epam.pipeline.autotests.ao.PipelinesLibraryAO.treeItemInFolder;
import static com.epam.pipeline.autotests.ao.Primitive.CODE_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURATION_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.DOCUMENTS_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.HISTORY_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.STORAGE_RULES_TAB;
import static com.epam.pipeline.autotests.ao.StorageContentAO.folderWithName;
import static com.epam.pipeline.autotests.utils.Conditions.backgroundColor;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static com.codeborne.selenide.Selectors.byXpath;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class FolderNavigationTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final Color SEARCH_COLOR = Colors.YELLOW.getColorValue();
    private static final Condition backgroundColorNotBlackOrWhite = Condition.and(
        "background color neither white nor black",
        not(backgroundColor(Colors.WHITE.getColorValue())),
        not(backgroundColor(Colors.BLACK.getColorValue()))
    );
    private static final Color SELECTED_COLOR = Color.fromString("#d2eafb");
    private final String folder315 = resourceName("epmcmbibpc-315");
    private final String lowerCased1072 = "test";
    private final String upperCased1072 = "TEST";
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
            .removeFolderIfExists(folder315)
            .removeStorage(storage1072);
    }

    @Test
    @TestCase("EPMCMBIBPC-1072")
    public void caseSensitiveValidationInTheStorage() {
        pipelinesLibrary()
            .selectStorage(storage1072)
            .createFolder(lowerCased1072)
            .ensure(folderWithName(lowerCased1072), visible.because("folder has been created"))
            .createFolder(upperCased1072)
            .ensure(folderWithName(upperCased1072), visible.because("folder has been created"))
            .rm(lowerCased1072)
            .ensure(folderWithName(lowerCased1072), not(exist).because("folder has been removed"))
            .ensure(folderWithName(upperCased1072), visible.because(
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
            // an earlier search can leave the folder expanded, so collapse it to see that this search expands it
            .performIf(parentItem, expandedItem, tree -> tree.click(switcherOf(parentItem)))
            .ensure(parentItem, collapsedItem)
            .setValue(searchInput, parentFolder)
            .enter()
            .ensure(titleOfTreeItem, contains(searchResult))
            .ensure(searchResultOf(titleOfTreeItem), have(backgroundColor(SEARCH_COLOR)))
            .ensure(parentItem, expandedItem)
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
            .ensure(parentItem, expandedItem)
            .ensure(childItem, expandedItem)
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
            // Prerequisite of the case: the folder and its sub-folder are collapsed. A previous test may have left
            // them expanded, and collapsing the folder keeps its sub-folder expanded, so the sub-folder is closed first
            .performIf(parentItem, collapsedItem, tree -> tree.click(switcherOf(parentItem)))
            .performIf(childItem, expandedItem, tree -> tree.click(switcherOf(childItem)))
            .click(switcherOf(parentItem))
            .ensure(parentItem, collapsedItem)
            .ensure(childItem, not(visible))
            .click(switcherOf(parentItem))
            .ensure(parentItem, expandedItem)
            .ensure(childItem, visible)
            .ensure(childItem, collapsedItem);
    }

    @Test
    @TestCase("EPMCMBIBPC-336")
    public void collapseFolderTest() {
        pipelinesLibrary()
            .performIf(parentItem, collapsedItem, tree -> tree.click(switcherOf(parentItem)))
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
            // an earlier test can leave the folder expanded, and a click does not collapse it
            .performIf(parentItem, expandedItem, tree -> tree.click(switcherOf(parentItem)))
            .ensure(parentItem, collapsedItem)
            .click(parentItem)
            .ensure(parentItem, expandedItem, selectedItem)
            .ensure(contentWrapperOf(parentItem), backgroundColor(SELECTED_COLOR))
            .ensure(childItem, visible)
            .ensure(browserItem(childFolder), browser, visible)
            .collapseItem(parentFolder);
    }

    @Test
    @TestCase("EPMCMBIBPC-390")
    public void selectingPipelineTest() {
        pipelinesLibrary()
            .click(parentItem)
            .click(childItem)
            .ensure(pipelineItem, collapsedItem)
            .click(pipelineItem)
            .ensure(pipelineItem, expandedItem, selectedItem)
            .ensure(contentWrapperOf(pipelineItem), backgroundColor(SELECTED_COLOR))
            .ensure(visible(browserItem), have(text("draft")))
            .also(() -> $(browser).findAll(browserItem).shouldHave(size(1)))
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
            // Selecting the pipeline opens its page and reloads the library tree, which expands the selected pipeline
            // again: the triangle is clicked once the page and the pipeline versions in the tree are loaded
            .ensure(visible(browserItem), have(text("draft")))
            .ensure(versionsOf(pipeline), visible)
            .click(switcherOf(pipelineItem))
            .ensure(pipelineItem, collapsedItem, selectedItem, backgroundColorNotBlackOrWhite)
            .ensure(visible(browserItem), have(text("draft")))
            .collapseItem(childFolder)
            .collapseItem(parentFolder);
    }

    @Test
    @TestCase("EPMCMBIBPC-395")
    public void selectingPipelineVersionTest() {
        final By draftItem = Combiners.confine(treeItem("draft"), pipelineItem, "draft version of " + pipeline);
        pipelinesLibrary()
            .click(parentItem)
            .click(childItem)
            .click(pipelineItem)
            .ensure(pipelineItem, expandedItem, selectedItem, backgroundColorNotBlackOrWhite)
            .click(treeItemInFolder(pipeline, "draft"))
            .ensure(draftItem, selectedItem)
            .ensure(contentWrapperOf(draftItem), backgroundColor(SELECTED_COLOR));
        new DocumentTabAO(pipeline)
            .ensureVisible(CODE_TAB, CONFIGURATION_TAB, DOCUMENTS_TAB, HISTORY_TAB, STORAGE_RULES_TAB);
        pipelinesLibrary()
            .collapseItem(childFolder)
            .collapseItem(parentFolder);
    }

    @Test(priority = 100)
    @TestCase("EPMCMBIBPC-316")
    public void deleteFolderWithSubitemTest() {
        final PipelinesLibraryAO library = pipelinesLibrary()
                .ensure(parentItem, visible)
                .ensure(browserItem(parentFolder), browser, visible);
        $(browser).find(browserItem(parentFolder))
                .find(byCssSelector("button[id$='-delete-button']")).shouldBe(visible).click();
        final SelenideElement dialog = $$(byClassName("ant-modal-content"))
                .findBy(text(format("Folder '%s' contains sub-items", parentFolder))).shouldBe(visible);
        dialog.find(byText("Delete sub-items")).shouldBe(visible);
        dialog.find(button("Cancel")).shouldBe(visible);
        dialog.find(button("OK")).shouldBe(visible);
        dialog.find(byText("Delete sub-items")).click();
        dialog.find(button("OK")).shouldBe(enabled).click();
        dialog.should(disappear);
        library
                .ensure(parentItem, not(visible))
                .ensure(browserItem(parentFolder), browser, not(visible))
                .setValue(searchInput, parentFolder)
                .enter()
                .ensure(searchResult, not(exist))
                // a search without a match leaves no tree item expanded
                .ensure(rootFolder, collapsedItem);
    }

    private PipelinesLibraryAO pipelinesLibrary() {
        return library().resetMouse().clickRoot();
    }

    private By contentWrapperOf(final By item) {
        return Combiners.confine(byClassName("ant-tree-node-content-wrapper"), item,
                "content wrapper of " + item);
    }

    // Child nodes (versions) of the pipeline node in the library tree
    private By versionsOf(final String pipelineName) {
        return byXpath(format(".//li[contains(@class, 'pipelines-library-tree-node') and ./span[contains(., '%s')]]"
                + "//li[contains(@class, 'pipelines-library-tree-node')]", pipelineName));
    }
}

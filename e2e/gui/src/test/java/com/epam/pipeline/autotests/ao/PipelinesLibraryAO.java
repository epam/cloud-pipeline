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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.configurationWithName;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.pipelineWithName;
import static java.util.concurrent.TimeUnit.SECONDS;

public class PipelinesLibraryAO implements AccessObject<PipelinesLibraryAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CREATE, context().find(byId("create-button"))),
            entry(CREATE_PIPELINE, $(byClassName("create-pipeline-sub-menu-button"))),
            entry(CREATE_FOLDER, $(byClassName("ant-dropdown-placement-bottomRight"))
                    .find(byText("Folder"))),
            entry(CREATE_STORAGE, $(byClassName("create-storage-sub-menu"))),
            entry(CREATE_CONFIGURATION, $(byClassName("create-configuration-button"))),
            entry(ADD_EXISTING_STORAGE, $(byClassName("add-existing-storage-button"))),
            entry(CREATE_NFS_MOUNT, $(byClassName("create-new-nfs-mount")))
    );

    public static final By tree = byId("pipelines-library-tree");

    /**
     * Selects any item in the tree of a library.
     *
     * Useful when you need to describe any tree item, for instance, when you need all of them, get first one
     * or get one by index.
     */
    public static final By treeItem = byXpath(".//li[contains(@class, 'pipelines-library-tree-node')]");

    /**
     * Selects search input in tree view.
     */
    public static final By searchInput = byId("pipelines-library-search-input");

    /**
     * Selects any search result. Can be used with combiner as for instance.
     * @see com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners
     */
    public static final By searchResult = byClassName("pipelines-library__search-result");

    /**
     * Selects any switcher on a page.
     * @see com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners
     */
    public static final By switcher = byClassName("ant-tree-switcher");

    /**
     * Selects any draft version in tree view.
     */
    public static final By draftVersion = treeItem("draft");

    public static final By browser = byId("pipelines-library-split-pane-right");

    public static final By browserItem = byXpath(".//tr[contains(@class, 'ant-table-row')]");

    /**
     * Checks if tree item is collapsed.
     */
    public static final Condition collapsedItem = new Condition("is collapsed item") {
        @Override
        public boolean apply(final WebElement treeItem) {
            return $(treeItem).find(switcher).has(cssClass("ant-tree-switcher_close"));
        }
    };

    /**
     * Checks if tree item is expanded.
     */
    public static final Condition expandedItem = new Condition("is expanded item") {
        @Override
        public boolean apply(final WebElement treeItem) {
            return $(treeItem).find(switcher).has(cssClass("ant-tree-switcher_open"));
        }
    };

    /**
     * Checks if tree item is selected.
     */
    public static final Condition selectedItem = new Condition("be selected") {
        private final By wrapper = byClassName("ant-tree-node-content-wrapper");
        @Override
        public boolean apply(final WebElement treeItem) {
            return $(treeItem).find(wrapper).has(cssClass("ant-tree-node-selected"));
        }
    };

    public static By treeItem(final String name) {
        final String treeItemClass = "pipelines-library-tree-node";
        return byXpath(String.format(
            ".//li[contains(@class, '%s') and ./span[contains(., '%s')]]", treeItemClass, name
        ));
    }

    public static By titleOfTreeItem(final By treeItem) {
        final By titleQualifier = byClassName("pipelines-library__tree-item-title");
        return Combiners.confine(titleQualifier, treeItem, "title of a " + treeItem);
    }

    public static By searchResultOf(final By titleOfTreeItem) {
        return Combiners.confine(searchResult, titleOfTreeItem, "search result of " + titleOfTreeItem);
    }

    public static By switcherOf(final By treeItem) {
        return Combiners.confine(switcher, treeItem, "switcher of " + treeItem);
    }

    public static By browserItem(final String name) {
        final String browserRowClass = "ant-table-row";
        final String nameColumnClass = "browser__tree-item-name";
        return byXpath(String.format(
            ".//tr[contains(@class, '%s') and ./td[@class = '%s' and .//text() = '%s']]",
            browserRowClass, nameColumnClass, name
        ));
    }

    public PipelinesLibraryAO cd(String folderName) {
        $(byId("pipelines-library-tree-container")).shouldBe(visible)
                .find(byText(folderName)).shouldBe(visible).click();
        return this;
    }

    public MetadataSamplesAO metadataSamples(String metadataFolder) {
        $(byId("pipelines-library-tree-container")).shouldBe(visible)
                .find(withText(metadataFolder)).shouldBe(visible).click();
        sleep(1, SECONDS);
        return new MetadataSamplesAO();
    }

    public PipelinesLibraryAO metadataSamples(String metadataFolder, Consumer<MetadataSamplesAO> action) {
        metadataSamples(metadataFolder);
        action.accept(new MetadataSamplesAO());
        return this;
    }

    public PipelineLibraryContentAO clickOnPipeline(String pipelineName) {
        cd(pipelineName);
        return new PipelineLibraryContentAO(pipelineName);
    }

    public LibraryFolderAO clickOnFolder(String folderName) {
        cd(folderName);
        return new LibraryFolderAO(folderName);
    }

    public PipelinesLibraryAO createStorage(String storageName) {
        return clickOnCreateStorageButton()
                .setStoragePath(storageName)
                .ok();
    }

    public CreateStoragePopupAO clickOnCreateStorageButton() {
        resetMouse().hover(CREATE).click(CREATE_STORAGE);
        return new CreateStoragePopupAO();
    }

    public PipelinesLibraryAO createNfsMount(String nfsMountPath, String nfsMountName) {
        return clickOnCreateNfsMountButton()
                .setNfsMountPath(nfsMountPath)
                .setNfsMountAlias(nfsMountName)
                .ok();
    }

    public PipelinesLibraryAO createNfsMountWithDescription(String nfsMountPath,
                                                            String nfsMountName,
                                                            String nfsMountDescription) {
        return clickOnCreateNfsMountButton()
                .setNfsMountPath(nfsMountPath)
                .setNfsMountAlias(nfsMountName)
                .setNfsMountDescription(nfsMountDescription)
                .ok();
    }


    public CreateNfsMountPopupAO clickOnCreateNfsMountButton() {
        resetMouse().hover(CREATE).hover(CREATE_STORAGE).click(CREATE_NFS_MOUNT);
        return new CreateNfsMountPopupAO();
    }

    public CreateStoragePopupAO clickOnCreateExistingStorageButton() {
        resetMouse().hover(CREATE).hover(CREATE_STORAGE).click(ADD_EXISTING_STORAGE);
        return new CreateStoragePopupAO();
    }

    public PipelinesLibraryAO clickRoot() {
        clickOnPipeline("Library");
        return this;
    }

    public PipelinesLibraryAO removeStorage(String storageName) {
        selectStorage(storageName);
        $(byId("edit-storage-button")).shouldBe(visible).click();
        sleep(1, SECONDS);
        $(byId("edit-storage-dialog-delete-button")).shouldBe(visible).click();
        $(byId("edit-storage-delete-dialog-delete-button")).shouldBe(visible).click();
        return this;
    }

    public StorageContentAO selectStorage(String storageName) {
        cd(storageName);
        return new StorageContentAO();
    }

    public PipelinesLibraryAO validateStorage(String storageName) {
        return ensure(byText(storageName), visible);
    }

    public PipelinesLibraryAO validateStorageIsNotPresent(String storageName) {
        return sleep(2, SECONDS)
                .ensure(byText(storageName), not(visible));
    }

    public PipelinesLibraryAO validatePipeline(String pipelineName) {
        return ensure(byText(pipelineName), visible);
    }

    public PipelinesLibraryAO validatePipelineIsNotPresent(final String pipelineName) {
        return ensure(byText(pipelineName), not(visible));
    }

    public DocumentTabAO clickOnDraftVersion(final String pipelineName) {
        cd(pipelineName);
        return new PipelineLibraryContentAO(pipelineName).draft();
    }

    public PipelinesLibraryAO expandAllFolders() {
        final String closedFolder =
                "[class^=pipelines-library-tree-node-folder] > .ant-tree-switcher.ant-tree-switcher_close";

        while ($(closedFolder).isDisplayed()) {
            $(closedFolder).click();
        }
        return this;
    }

    public PipelinesLibraryAO collapseItem(String item) {
        $(byText(item)).closest("li")
                .find(byCssSelector(".ant-tree-switcher.ant-tree-switcher_open")).shouldBe(visible).click();
        return this;
    }

    public PipelinesLibraryAO removeFolder(String folderName) {
        cd(folderName)
                .resetMouse()
                .hover(byId("edit-folder-menu-button"))
                .click(byText("Delete"))
                .click(button("OK"));
        return this;
    }

    public PipelinesLibraryAO removeNotEmptyFolder(String folderName) {
        cd(folderName)
                .resetMouse()
                .hover(byId("edit-folder-menu-button"))
                .click(byText("Delete"))
                .click(byText("Delete sub-items"))
                .click(button("OK"));
        return this;
    }

    public ElementsCollection getStorages() {
        return $$("[class^=pipelines-library-tree-node-storage]");
    }

    public PipelinesLibraryAO validateStoragePictogram(final String storage) {
        $(treeItem(storage)).$(byClassName("ant-tree-switcher")).shouldHave(cssClass("ant-tree-switcher-noop"));
        return this;
    }

    public PipelinesLibraryAO assertNoPipelinesAreDisplayed() {
        $$("[class^=pipelines-library-tree-node-pipeline]").shouldHaveSize(0);
        return this;
    }

    public PipelinesLibraryAO createFolder(String folderName) {
        resetMouse().hover(CREATE).click(CREATE_FOLDER);
        Utils.getPopupByTitle("Create folder")
                .find(byId("name")).shouldBe(visible).setValue(folderName);
        $(byId("folder-edit-form-ok-button")).shouldBe(visible).click();
        return this;
    }

    public PipelinesLibraryAO createPipeline(Template template, String pipelineName) {
        return template.createPipeline(pipelineName);
    }

    public PipelinesLibraryAO createPipeline(String pipelineName) {
        return Template.DEFAULT.createPipeline(pipelineName);
    }

    public PipelinesLibraryAO removePipeline(String pipelineName) {
        sleep(5, SECONDS);
        clickOnPipeline(pipelineName)
                .delete();
        return this;
    }

    public PipelinesLibraryAO removePipelineIfExists(final String pipelineName) {
        return sleep(5, TimeUnit.SECONDS)
                .performIf(pipelineWithName(pipelineName), visible,
                        library -> library.removePipeline(pipelineName)
                );
    }

    public CreatePipelinePopupAO clickCreatePipelineButton() {
        sleep(2, SECONDS);
        hover(CREATE).hover(CREATE_PIPELINE).click(CREATE_PIPELINE);
        return new CreatePipelinePopupAO();
    }

    public CreatePipelinePopupAO clickCreatePipelineFromTemplate(Template template) {
        template.clickOnTemplate();

        return new CreatePipelinePopupAO();
    }

    public PipelinesLibraryAO validatePopupClosed() {
        $(byClassName("ant-modal-content")).shouldNot(be(visible));
        return this;
    }

    public PipelinesLibraryAO validateIsLoading() {
        $(byText("Checking repository existence...")).should(appear);
        $(withText("Creating pipeline")).should(appear);
        return this;
    }

    public PipelinesLibraryAO createConfiguration(final String configurationName) {
        return createConfiguration(configuration -> configuration.setName(configurationName).ok());
    }

    public PipelinesLibraryAO createConfiguration(final Consumer<DetachedConfigurationCreationPopup> configuration) {
        resetMouse().hover(CREATE).click(CREATE_CONFIGURATION);
        configuration.accept(new DetachedConfigurationCreationPopup(new Configuration()));
        return this;
    }

    private Configuration selectConfiguration(final String configurationName) {
        context().find(configurationWithName(configurationName)).shouldBe(visible).click();
        return new Configuration();
    }

    public <DESTINATION extends AccessObject<DESTINATION>> DESTINATION configuration(
            final String configurationName,
            final Function<Configuration, DESTINATION> configuration
    ) {
        return configuration.apply(selectConfiguration(configurationName));
    }

    public PipelinesLibraryAO configurationWithin(
            final String configurationName,
            final Consumer<Configuration> configuration
    ) {
        configuration.accept(selectConfiguration(configurationName));
        clickRoot();
        return this;
    }

    public PipelinesLibraryAO removeConfiguration(final String configurationName) {
        return configuration(configurationName, Configuration::delete);
    }

    public PipelinesLibraryAO removeConfigurationIfExists(final String configurationName) {
        return sleep(3, SECONDS)
                .performIf(configurationWithName(configurationName), visible,
                        library -> library.removeConfiguration(configurationName)
                );
    }

    public PipelinesLibraryAO removeFolderIfExists(final String folderName) {
        return sleep(5, SECONDS)
                .performIf(treeItem(folderName), visible, page -> page.removeFolder(folderName));
    }

    public PipelinesLibraryAO removeStorageIfExists(final String storageName) {
        return sleep(5, SECONDS)
                .performIf(treeItem(storageName), visible, page -> page.removeStorage(storageName));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public void ensurePopupIsClosed() {
        sleep(2, SECONDS);
        $(byClassName("ant-modal-body")).shouldNotBe(visible);
    }
}
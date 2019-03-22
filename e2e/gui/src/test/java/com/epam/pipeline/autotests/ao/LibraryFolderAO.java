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

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.Utils;

import java.io.File;
import java.util.Map;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_NEW_RULE;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT_FOLDER;
import static com.epam.pipeline.autotests.ao.Primitive.NEW_FILE;
import static com.epam.pipeline.autotests.ao.Primitive.RENAME;
import static com.epam.pipeline.autotests.ao.Primitive.RUN;
import static com.epam.pipeline.autotests.ao.Primitive.UPLOAD;
import static com.epam.pipeline.autotests.ao.Primitive.UPLOAD_METADATA;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.attributesMenu;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.displayAttributes;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.menuitem;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.showAttributes;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.tagName;

public class LibraryFolderAO implements AccessObject<LibraryFolderAO> {

    private final String folderName;

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(SETTINGS, $(byId("edit-folder-menu-button"))),
            entry(EDIT_FOLDER, context().find(menuitem("ant-dropdown-menu-item", "Edit folder"))),
            entry(UPLOAD_METADATA,$(byId("upload-button")))
    );

    public LibraryFolderAO(String folderName) {
        this.folderName = folderName;
    }

    public LibraryFolderEditPopupAO clickEditButton() {
        hover(SETTINGS);
        click(EDIT_FOLDER);
        return new LibraryFolderEditPopupAO(this);
    }

    public PipelineLibraryContentAO clickOnPipeline(String pipelineName) {
        return new PipelinesLibraryAO().clickOnPipeline(pipelineName);
    }

    public LibraryFolderAO assertThereIsNoPipeline(String pipelineName) {
        new PipelinesLibraryAO()
                .validatePipelineIsNotPresent(pipelineName);
        return this;
    }

    public LibraryFolderAO assertThereIsPipeline(String pipelineName) {
        new PipelinesLibraryAO()
                .validatePipeline(pipelineName);
        return this;
    }

    public MetadataSectionAO showMetadata() {
        final SelenideElement displayButton = $(displayAttributes);
        displayButton.shouldBe(visible).hover();
        $(attributesMenu).should(appear);
        performIf(showAttributes, visible,
                page -> click(showAttributes),
                page -> resetMouse()
        );
        return new MetadataSectionAO(this);
    }

    public LibraryFolderAO assertPipelineIsNotEditable(String pipelineName) {
        clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab()
                .ensureNotVisible(RENAME, DELETE, NEW_FILE, UPLOAD, RUN)
                .documentsTab()
                .ensureNotVisible(DELETE, RENAME, UPLOAD)
                .storageRulesTab()
                .ensureNotVisible(DELETE, ADD_NEW_RULE);

        $(byText(folderName)).shouldBe(visible).click();
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return this.elements;
    }

    public LibraryFolderAO uploadMetadata(File file) {
        sleep(5, SECONDS);
        ensure(UPLOAD_METADATA, visible);
        $(byClassName("ant-upload-select")).find(tagName("input")).should(exist).uploadFile(file);
        return this;
    }

    public static class LibraryFolderEditPopupAO extends PopupWithStringFieldAO<LibraryFolderEditPopupAO, LibraryFolderAO> {
        public LibraryFolderEditPopupAO(LibraryFolderAO parentAO) {
            super(parentAO);
        }

        @Override
        public LibraryFolderEditPopupAO typeInField(String value) {
            $(byId("name")).shouldBe(visible).setValue(value);
            return this;
        }

        public PermissionTabAO clickOnPermissionsTab() {
            //need to change title after changing it in the gui popup
            Utils.getPopupByTitle("Rename folder")
                    .find(byText("Permissions")).shouldBe(visible).click();
            return new PermissionTabAO(this);
        }

        @Override
        public void closeAll() {
            //in order to avoid ok() bug
            cancel();
        }
    }
}

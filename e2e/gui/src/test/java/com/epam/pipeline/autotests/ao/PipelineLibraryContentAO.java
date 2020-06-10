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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import java.util.Map;
import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIRM_RELEASE;
import static com.epam.pipeline.autotests.ao.Primitive.CROSS;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT_REPOSITORY_SETTINGS;
import static com.epam.pipeline.autotests.ao.Primitive.FIRST_VERSION;
import static com.epam.pipeline.autotests.ao.Primitive.RELEASE;
import static com.epam.pipeline.autotests.ao.Primitive.REPOSITORY;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.VERSION;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.attributesMenu;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.displayAttributes;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.showAttributes;
import static com.epam.pipeline.autotests.utils.Utils.getPopupByTitle;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.tagName;
import static org.openqa.selenium.By.xpath;

public class PipelineLibraryContentAO implements AccessObject<PipelineLibraryContentAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(RELEASE, $(button("RELEASE"))),
            entry(CONFIRM_RELEASE, $(byId("register-version-form-release-button"))),
            entry(VERSION, $(byId("version"))),
            entry(FIRST_VERSION, $(byCssSelector(".ant-table-row .anticon-tag")).closest(".ant-table-row"))
    );
    private final String pipelineName;

    public PipelineLibraryContentAO(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public DocumentTabAO firstVersion() {
        click(FIRST_VERSION);
        return new DocumentTabAO(pipelineName);
    }

    public DocumentTabAO v1() {
        $$(byCssSelector(".ant-table-row")).findBy(text("v1")).shouldBe(visible).click();
        return new DocumentTabAO(pipelineName);
    }

    public DocumentTabAO draft() {
        $$(".ant-table-row").findBy(text("draft-")).shouldBe(visible).click();
        return new DocumentTabAO(pipelineName);
    }

    public DocumentTabAO version(final String version) {
        $(byText(version)).shouldBe(visible).click();
        return new DocumentTabAO(pipelineName);
    }

    public void delete() {
        clickEditButton()
                .delete()
                .confirmDeletion();
    }

    public PipelineLibraryContentAO releaseFirstVersion(String version) {
        return click(RELEASE)
                .setValue(VERSION, version)
                .click(CONFIRM_RELEASE);
    }

    public PipelineLibraryContentAO assertVersion(String version) {
        return ensure(FIRST_VERSION, text(version));
    }

    public PipelineLibraryContentAO assertPipelineName(String name){
        return ensure(xpath("//div[@class = 'browser__item-header']//span"), text(name));
    }

    public PipelineLibraryContentAO assertVersionNot(String version) {
        return ensure(FIRST_VERSION, not(text(version)));
    }

    public String getFirstVersionName(){
        return $(byCssSelector(".browser__tree-item-version")).text();
    }

    public PipelineLibraryContentAO assertReleaseButton() {
        $$(byAttribute("type", "button")).findBy(text("RELEASE")).should(exist);
        return this;
    }

    public PipelineLibraryContentAO assertThereIsNoReleaseButton() {
        $$(byAttribute("type", "button")).findBy(text("RELEASE")).shouldNot(exist);
        return this;
    }

    public PipelineEditPopupAO clickEditButton() {
        sleep(5, SECONDS);
        $(byId("edit-pipeline-button")).shouldBe(visible).click();
        return new PipelineEditPopupAO();
    }

    public PipelineLibraryContentAO assertEditButtonIsDisplayed() {
        $(byId("edit-pipeline-button")).shouldBe(visible);
        return this;
    }

    public PipelineLibraryContentAO assertRunButtonIsDisplayed() {
        $$(tagName("button")).find(text("Run")).shouldBe(visible);
        return this;
    }

    public MetadataSectionAO showMetadata() {
        hover(displayAttributes);
        ensure(attributesMenu, appears);
        performIf(showAttributes, visible,
                page -> click(showAttributes),
                page -> resetMouse()
        );
        return new MetadataSectionAO(this);
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public class PipelineEditPopupAO extends PopupAO<PipelineEditPopupAO, PipelineLibraryContentAO> implements ClosableAO {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(EDIT_REPOSITORY_SETTINGS, context().find(byText("Edit repository settings"))),
                entry(DELETE, $(byId("edit-pipeline-form-delete-button"))),
                entry(SAVE, $(byId("edit-pipeline-form-save-button"))),
                entry(CANCEL, $(byId("edit-pipeline-form-cancel-button"))),
                entry(REPOSITORY, $(byId("repository"))),
                entry(CROSS, $(byClassName("ant-modal-close")))
        );

        public PipelineEditPopupAO() {
            super(PipelineLibraryContentAO.this);
        }

        public PermissionTabAO clickOnPermissionsTab() {
            getPopupByTitle("Edit pipeline info")
                    .find(byText("Permissions")).shouldBe(visible).click();
            return new PermissionTabAO(this);
        }

        public DeletionConfirmationPopupAO delete() {
            sleep(5, SECONDS);
            ensure(DELETE, enabled).click(DELETE);
            return new DeletionConfirmationPopupAO();
        }

        public PipelineLibraryContentAO save() {
            return click(SAVE).parent();
        }

        public PipelineLibraryContentAO cancel() {
            return click(CANCEL).parent();
        }

        public PipelineLibraryContentAO close() {
            return click(CROSS).parent();
        }

        public PipelineEditPopupAO openRepositorySettings() {
            return click(EDIT_REPOSITORY_SETTINGS);
        }

        public String getRepository() {
            return get(REPOSITORY).getValue();
        }

        @Override
        public PipelineLibraryContentAO ok() {
            click(SAVE);
            return parent();
        }

        @Override
        public void closeAll() {
            save();
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    public class DeletionConfirmationPopupAO {
        private final SelenideElement element = $$(className("ant-modal")).findBy(text("Do you want to delete a pipeline with repository or only unregister it?"));
        private final ElementsCollection buttons = element.findAll(className("ant-btn"));
        private final SelenideElement cancelButton = buttons.findBy(text("Cancel"));
        private final SelenideElement unregisterButton = $(byId("edit-pipeline-delete-dialog-unregister-button"));
        private final SelenideElement deleteButton = buttons.findBy(text("Delete"));

        public PipelinesLibraryAO confirmDeletion() {
            element.shouldBe(visible);
            deleteButton.shouldBe(visible, enabled).click();
            element.should(disappear);
            return new PipelinesLibraryAO();
        }

        public PipelineEditPopupAO cancel() {
            element.shouldBe(visible);
            cancelButton.shouldBe(visible, disabled).click();
            element.should(disappear);
            return new PipelineEditPopupAO();
        }

        public PipelinesLibraryAO unregister() {
            unregisterButton.shouldBe(visible).click();
            return new PipelinesLibraryAO();
        }
    }
}

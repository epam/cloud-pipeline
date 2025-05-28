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
package com.epam.pipeline.autotests.ao;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;

import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CROSS;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.ENABLE_VERSIONING;
import static com.epam.pipeline.autotests.ao.Primitive.MOUNT_OPTIONS;
import static com.epam.pipeline.autotests.ao.Primitive.MOUNT_POINT;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PATH;
import static com.epam.pipeline.autotests.ao.Primitive.PERMISSIONS;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.UNREGISTER;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import com.epam.pipeline.autotests.ao.StorageContentAO.AbstractEditStoragePopUpAO;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.codeborne.selenide.SelenideElement;

import java.util.Map;

public class EditStoragePopUpAO extends AbstractEditStoragePopUpAO<EditStoragePopUpAO, PipelinesLibraryAO> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(SAVE, $(byId("edit-storage-dialog-save-button"))),
            entry(DELETE, $(byId("edit-storage-dialog-delete-button"))),
            entry(CANCEL, $(byId("edit-storage-dialog-cancel-button"))),
            entry(PERMISSIONS, $(byText("Permissions")))
    );

    public EditStoragePopUpAO() {
        super(new PipelinesLibraryAO());
    }

    public EditStoragePopUpAO validateEditFormElements() {
        return ensure(PATH, disabled, visible)
                .ensure(NAME, visible)
                .ensure(DESCRIPTION, visible)
                .performIf(C.CLOUD_PROVIDER.equalsIgnoreCase(Cloud.AWS.name())
                        || C.CLOUD_PROVIDER.equalsIgnoreCase(Cloud.GCP.name()), popup -> popup
                        .ensure(ENABLE_VERSIONING, visible))
                .ensure(MOUNT_POINT, visible)
                .ensure(MOUNT_OPTIONS, visible)
                .ensure(SAVE, visible)
                .ensure(DELETE, visible)
                .ensure(CANCEL, visible);
    }

    public EditStoragePopUpAO validateEditFormElementsNfsMount() {
        return ensure(PATH, disabled, visible)
                .ensure(NAME, visible)
                .ensure(DESCRIPTION, visible)
                .ensure(MOUNT_POINT, visible)
                .ensure(MOUNT_OPTIONS, visible)
                .ensure(SAVE, visible)
                .ensure(DELETE, visible)
                .ensure(CANCEL, visible);
    }

    public EditStoragePopUpAO editForNfsMount() {
        if ($(byClassName("ant-modal-header")).isDisplayed() &&
                !$(byClassName("edit-storage-button")).isDisplayed()) {
            return this;
        }
        $(byClassName("edit-storage-button")).shouldBe(enabled).click();
        return this;
    }

    @Override
    public PipelinesLibraryAO ok() {
        return clickSaveButton();
    }

    public PipelinesLibraryAO clickSaveButton() {
        return click(SAVE).parent();
    }

    public PipelinesLibraryAO clickCancel() {
        return click(CANCEL).ensure(CANCEL, not(visible)).parent();
    }

    public DeleteStorageConfirmationPopUp clickDeleteStorageButton() {
        sleep(1, SECONDS);
        click(DELETE);
        return new DeleteStorageConfirmationPopUp(this);
    }

    public PermissionTabAO clickOnPermissionsTab() {
        click(PERMISSIONS);
        return new PermissionTabAO(this);
    }

    @Override
    public SelenideElement context() {
        return $(modalWithTitle("Edit", "storage"));
    }

    @Override
    public void closeAll() {
        ok();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public class DeleteStorageConfirmationPopUp implements AccessObject<DeleteStorageConfirmationPopUp> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(DELETE, $(byId("edit-storage-delete-dialog-delete-button"))),
                entry(UNREGISTER, $(byId("edit-storage-delete-dialog-unregister-button"))),
                entry(CANCEL, $(byId("edit-storage-delete-dialog-cancel-button"))),
                entry(CROSS, context().find(className("ant-modal-close")))
        );
        private final EditStoragePopUpAO editStoragePopUpAO;

        public DeleteStorageConfirmationPopUp(EditStoragePopUpAO editStoragePopUpAO) {
            this.editStoragePopUpAO = editStoragePopUpAO;
        }

        public PipelinesLibraryAO clickDelete() {
            click(DELETE).ensure(DELETE, not(visible));
            return new PipelinesLibraryAO();
        }

        public PipelinesLibraryAO clickUnregister() {
            click(UNREGISTER).ensure(UNREGISTER, not(visible));
            return new PipelinesLibraryAO();
        }

        public EditStoragePopUpAO clickCrossButton() {
            click(CROSS).ensure(CROSS, not(visible));
            return editStoragePopUpAO;
        }

        public EditStoragePopUpAO clickCancel() {
            click(CANCEL).ensure(CANCEL, not(visible));
            return editStoragePopUpAO;
        }

        @Override
        public SelenideElement context() {
            return Utils.getPopupByTitle("Do you want to delete a storage itself or only unregister it?");
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }
}

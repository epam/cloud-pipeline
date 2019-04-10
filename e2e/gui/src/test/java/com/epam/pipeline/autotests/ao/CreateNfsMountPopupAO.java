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

import java.util.Map;

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;

public class CreateNfsMountPopupAO extends StorageContentAO.AbstractEditStoragePopUpAO<CreateStoragePopupAO, PipelinesLibraryAO> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(CANCEL, $(byId("edit-storage-dialog-cancel-button"))),
            entry(CREATE, $(byId("edit-storage-dialog-create-button")))
    );


    public CreateNfsMountPopupAO() {
        super(new PipelinesLibraryAO());
    }

    public PipelinesLibraryAO clickCancel() {
        click(CANCEL).ensure(CANCEL, not(visible));
        return this.parent();
    }

    public PipelinesLibraryAO clickCreateButton() {
        click(CREATE);
        return this.parent();
    }



    public CreateNfsMountPopupAO setNfsMountPath(final String nfsMountPath, final String nfsPrefix) {
        hover(button("Unknown FS Mount target"));
        $(".data-storage-path-input__navigation-dropdown-container").find(withText(nfsPrefix)).click();
        $(byId("edit-storage-storage-path-input")).shouldBe(visible).setValue(nfsMountPath);
        return this;
    }

    public CreateNfsMountPopupAO setNfsMountAlias(String nfsMountName) {
        $(byId("name")).shouldBe(visible).setValue(nfsMountName);
        return this;
    }

    public CreateNfsMountPopupAO setNfsMountDescription(String nfsDescription) {
        $(byId("description")).shouldBe(visible).setValue(nfsDescription);
        return this;
    }

    public CreateNfsMountPopupAO setNfsMountPoint(String mountPoint) {
        $(byId("mountPoint")).shouldBe(visible).setValue(mountPoint);
        return this;
    }

    public CreateNfsMountPopupAO setNfsMountOptions(String mountOptions) {
        $(byId("mountOptions")).shouldBe(visible).setValue(mountOptions);
        return this;
    }

    @Override
    public PipelinesLibraryAO ok() {
        return clickCreateButton();
    }

    @Override
    public PipelinesLibraryAO cancel() {
        return clickCancel();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
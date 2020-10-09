/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.SENSITIVE_STORAGE;

public class CreateStoragePopupAO extends StorageContentAO.AbstractEditStoragePopUpAO<CreateStoragePopupAO, PipelinesLibraryAO> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(CANCEL, $(byId("edit-storage-dialog-cancel-button"))),
            entry(CREATE, $(byId("edit-storage-dialog-create-button"))),
            entry(SENSITIVE_STORAGE, context().find(byText("Sensitive storage"))
                    .parent().find(byClassName("ant-checkbox")))
    );

    public CreateStoragePopupAO() {
        super(new PipelinesLibraryAO());
    }

    public PipelinesLibraryAO clickCancel() {
        return click(CANCEL).ensure(CANCEL, not(visible)).parent();
    }

    public PipelinesLibraryAO clickCreateButton() {
        return click(CREATE).parent();
    }

    public PipelinesLibraryAO clickCreateAndCancel() {
        return click(CREATE).click(CANCEL).parent();
    }

    public CreateStoragePopupAO setStoragePath(String storageName) {
        $(byId("edit-storage-storage-path-input")).shouldBe(visible).setValue(storageName);
        return this;
    }

    public CreateStoragePopupAO clickSensitiveStorageCheckbox() {
        return click(SENSITIVE_STORAGE);
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

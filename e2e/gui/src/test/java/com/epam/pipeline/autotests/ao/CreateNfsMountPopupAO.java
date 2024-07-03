/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.openqa.selenium.By;

import java.util.Map;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static java.lang.String.format;

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

    public CreateNfsMountPopupAO setNfsMountPath(String nfsMountPath) {
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

    public CreateNfsMountPopupAO setNfsMount(final String nfsMount) {
        final String pathInput = "edit-storage-storage-path-input";
        final String fsMountValue = $(byId(pathInput)).parent().find(By.xpath("div//div//div[2]")).getText();
        final String fsMount = fsMountValue.startsWith(": ")
                ? fsMountValue.replace(": ", "")
                : fsMountValue;
        if (nfsMount.equalsIgnoreCase(fsMount)) {
            return this;
        }
        $(byId(pathInput)).parent().find("button").shouldBe(enabled).click();
        final SelenideElement selenideElement = $$(byClassName("ant-row-flex")).stream()
                .filter(m -> m.getText().contains(nfsMount))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        format("the provided %s NFS mount was not found", nfsMount)));
        $(selenideElement).shouldBe(visible).click();
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
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
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.Map;
import java.util.stream.Collectors;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;

public class MetadataFilePreviewAO extends PopupAO<MetadataFilePreviewAO, MetadataSectionAO>{
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(EDIT, $(button("Edit"))),
            entry(CLOSE, $(button("Close"))),
            entry(SAVE, $(button("Save"))),
            entry(VIEW_AS_TEXT, $(byClassName("data-storage-code-form__button ant-switch")))
    );

    private final MetadataSectionAO parentAO;

    public MetadataFilePreviewAO(MetadataSectionAO parentAO) {
        super(parentAO);
        this.parentAO = parentAO;
    }

    public MetadataSectionAO close() {
        click(CLOSE);
        return parentAO;
    }
    public MetadataFilePreviewAO ensureHeaderContainsText(String text) {
        context().find(byClassName("ant-modal-title")).shouldHave(text(text));
        return this;
    }

    public MetadataFilePreviewAO ensureHeaderNotContainText(String text) {
        context().find(byClassName("ant-modal-title")).shouldNotHave(text(text));
        return this;
    }

    public MetadataFilePreviewAO assertFullScreenPreviewContainsText(String text) {
        $(byClassName("ant-spin-nested-loading")).shouldBe(visible);
        String fragment = text.length() > 100 ? text.substring(0, 100) : text;
        sleep(4, SECONDS);
        String textInWindow = $$(byCssSelector(" pre.CodeMirror-line "))
                .texts()
                .stream()
                .collect(Collectors.joining("\n"));
        assert(textInWindow.contains(fragment));
        return this;
    }

    public StorageContentAO editFileWithText(String text) {
        click(EDIT);
        sleep(3, SECONDS);
        Utils.clickAndSendKeysWithSlashes($(byClassName("CodeMirror-line")), text);
        ensure(SAVE, enabled).click(SAVE);
        $(button("OK")).shouldBe(visible).click();
        return new StorageContentAO();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return this.elements;
    }

    @Override
    public SelenideElement context() {
        return $(PipelineSelectors.visible(byClassName("ant-modal-content")));
    }
}


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

import java.util.Arrays;
import java.util.Map;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.Primitive.VALUE_FIELD;
import static java.util.concurrent.TimeUnit.SECONDS;

public class MetadataSectionAO extends PopupAO<MetadataSectionAO, AccessObject> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(ADD_KEY, $(byId("add-key-button"))),
            entry(REMOVE_ALL_KEYS, $(byId("remove-all-keys-button"))),
            entry(ENLARGE, $(PipelineSelectors.buttonByIconClass("anticon-arrows-alt"))),
            entry(FILE_PREVIEW, $(byId("file-preview-container")).find("textarea"))
    );

    private final String keyElementId = "key-column-%s";
    private final String valueElementId = "value-column-%s";
    private final String deleteButtonId = "delete-metadata-key-%s-button";
    private final String keyFieldId = "metadata__key-row";

    public MetadataSectionAO(AccessObject parentAO) {
        super(parentAO);
    }

    public KeysAndValuesAdditionForm addKey() {
        sleep(1, SECONDS);
        click(ADD_KEY);
        return new KeysAndValuesAdditionForm(this);
    }

    public MetadataSectionAO addKeyWithValue(final String key, final String value) {
        addKey()
                .sleep(1, SECONDS)
                .setKey(key)
                .sleep(1, SECONDS)
                .setValue(value)
                .add();
        return this;
    }

    public ConfirmationPopupAO<MetadataSectionAO> deleteKey(final String key) {
        $(byId(String.format(deleteButtonId, key))).shouldBe(visible, enabled).click();
        return new ConfirmationPopupAO<>(this);
    }

    public ConfirmationPopupAO<MetadataSectionAO> deleteAllKeys() {
        click(REMOVE_ALL_KEYS);
        return new ConfirmationPopupAO<>(this);
    }

    public MetadataSectionAO assertKeyIsPresent(final String key) {
        $(byId(String.format(keyElementId, key))).shouldBe(visible);
        return this;
    }

    public MetadataSectionAO assertKeysArePresent(final String... keys) {
        Arrays.stream(keys)
                .forEach(this::assertKeyIsPresent);
        return this;
    }

    public MetadataSectionAO assertKeyNotPresent(final String key) {
        $(byId(String.format(keyElementId, key))).shouldNotBe(visible);
        return this;
    }

    public MetadataSectionAO assertKeysAreNotPresent(final String... keys) {
        Arrays.stream(keys)
                .forEach(this::assertKeyNotPresent);
        return this;
    }

    public MetadataSectionAO assertKeyWithValueIsPresent(final String key, final String value) {
        assertKeyIsPresent(key);
        $(byId(String.format(valueElementId, key))).shouldBe(visible).shouldHave(text(value));
        return this;
    }

    public MetadataSectionAO assertNumberOfKeysIs(int expectedNumberOfKeys) {
        $$(byClassName(keyFieldId)).shouldHaveSize(expectedNumberOfKeys);
        return this;
    }

    public MetadataSectionAO assertFilePreviewContainsText(String text) {
        get(FILE_PREVIEW).shouldHave(text(text));
        return this;
    }

    public MetadataSectionAO ensureMetadataSectionContainsText(String text) {
        $(byCssSelector(".Pane.horizontal.Pane1")).shouldHave(text(text));
        return this;
    }

    public MetadataSectionAO ensureMetadataSectionNotContainText(String text) {
        $(byCssSelector(".Pane.horizontal.Pane1")).shouldNotHave(text(text));
        return this;
    }

    public MetadataFilePreviewAO fullScreen() {
        click(ENLARGE);
        return new MetadataFilePreviewAO(this);
    }

    public MetadataKeyAO selectKeyByOrderNumber(int orderNumber) {
        return new MetadataKeyAO(orderNumber - 1, this);
    }

    public MetadataKeyAO selectKey(final String key) {
        final SelenideElement keyRow = $(byId(String.format(keyElementId, key))).closest(".metadata__key-row");
        return new MetadataKeyAO(
                keyRow,
                keyRow.find(byXpath("following-sibling::*[contains(@class, 'metadata__value-row')]")),
                this
        );
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class KeysAndValuesAdditionForm extends PopupAO<KeysAndValuesAdditionForm, MetadataSectionAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(ADD, $(byId("add-metadata-item-button"))),
                entry(KEY_FIELD, $$(byClassName("metadata__new-key-row")).get(0).find(byClassName("ant-input"))),
                entry(VALUE_FIELD, $$(byClassName("metadata__new-key-row")).get(1).find(byClassName("ant-input")))
        );

        public KeysAndValuesAdditionForm(MetadataSectionAO parentAO) {
            super(parentAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public MetadataSectionAO add() {
            sleep(1, SECONDS);
            click(ADD);
            return parent();
        }

        public KeysAndValuesAdditionForm setKey(String key) {
            return setValue(KEY_FIELD, key);
        }

        public KeysAndValuesAdditionForm setValue(String value) {
            return setValue(VALUE_FIELD, value);
        }
    }
}




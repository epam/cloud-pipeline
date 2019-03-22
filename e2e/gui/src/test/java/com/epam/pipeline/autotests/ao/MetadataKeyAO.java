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
import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Map;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;


public class MetadataKeyAO extends PopupAO<MetadataKeyAO, MetadataSectionAO>{

    private final Map<Primitive, SelenideElement> elements;

    public static By samplesForKey(String key) {
        return By.id(String.format("value-column-%s", key));
    }

    public static final Condition backgroundColorGrey = new Condition("backgroundColorGrey") {
        @Override
        public boolean apply(WebElement element) {
            return element.getCssValue("background-color").equals("rgba(249, 249, 249, 1)");
        }
    };

    public MetadataKeyAO(SelenideElement keyFieldElement, SelenideElement valueFieldElement, MetadataSectionAO parentAO) {
        super(parentAO);
        this.elements = initialiseElements(
                entry(ADD, $(byId("add-metadata-item-button"))),
                entry(KEY_FIELD, keyFieldElement),
                entry(KEY_FIELD_INPUT, $(By.className("metadata__key-row-edit")).find(By.tagName("input"))),
                entry(VALUE_FIELD, valueFieldElement),
                entry(VALUE_FIELD_INPUT, keyFieldElement.parent().find(By.className("ant-input"))),
                entry(DELETE_ICON, keyFieldElement.parent().find(buttonByIconClass("anticon-delete")))
        );
    }

    public MetadataKeyAO(int numberOfKey, MetadataSectionAO parentAO) {
        this($$(By.className("metadata__key-row")).get(numberOfKey),
                $$(By.className("metadata__value-row")).get(numberOfKey),
                parentAO);
    }

    public MetadataKeyAO validateKeyBackgroundIsGrey() {
        return ensure(KEY_FIELD, backgroundColorGrey);
    }

    public MetadataKeyAO changeKey(String key) {
        return click(KEY_FIELD).setValue(KEY_FIELD_INPUT, key).enter();
    }

    public MetadataKeyAO changeValue(String value) {
        return click(VALUE_FIELD).setValue(VALUE_FIELD_INPUT, value).enter(VALUE_FIELD_INPUT);
    }

    public String getValue() {
        return get(VALUE_FIELD).text();
    }

    public MetadataSectionAO close() {
        return parent();
    }

    public MetadataKeyAO assertKeyIs(String key) {
        return ensure(KEY_FIELD, text(key));
    }

    public MetadataKeyAO assertValueIs(String value) {
        return ensure(VALUE_FIELD, text(value));
    }

    public static Condition numberOfSamples(int number) {
        return new Condition("number of samples") {
            @Override
            public boolean apply(WebElement element) {
                return element.findElements(By.tagName("div")).size() == number;
            }
        };
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return this.elements;
    }
}

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
import com.epam.pipeline.autotests.utils.C;

import java.util.Map;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_DICTIONARY;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_VALUE;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.xpath;

public class SystemDictionariesAO extends SettingsPageAO {

    public SystemDictionariesAO(PipelinesLibraryAO parent) {
        super(parent);
    }

    public final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(ADD_DICTIONARY, $(button("Add dictionary"))),
            entry(NAME, $(className("system-dictionary-form__name")).$(xpath(".//input"))),
            entry(ADD_VALUE, $(button("Add value"))),
            entry(SAVE, $(button("Save"))),
            entry(DELETE, $(button("Delete")))
    );

    public boolean systemDictionaryIsExist(String dict) {
        return $(className("ant-table-content"))
                .$(className(format("section-dictionary-%s", dict)))
                .exists();
    }

    public SystemDictionariesAO openSystemDictionary(String dict) {
        $(className("ant-table-content"))
                .$(className(format("section-dictionary-%s", dict)))
                .click();
        return this;
    }

    private boolean valueIsExist(String value) {
        return $(className("system-dictionary-form__items"))
                .find(byAttribute("value", value)).exists();
    }

    public SystemDictionariesAO addNewDictionary(String dict, String value) {
        click(ADD_DICTIONARY);
        setValue(NAME, dict);
        addDictionaryValue(value);
        return this;
    }

    public SystemDictionariesAO addDictionaryValue(String value) {
        if (valueIsExist(value)) {
            return this;
        }
        click(ADD_VALUE);
        SelenideElement newValue = $(className("system-dictionary-form__items"))
                .findAll(xpath(".//div[@class='system-dictionary-form__row']/input"))
                .filter(cssClass("cp-error"))
                .first();
        setValue(newValue, value);
        click(SAVE);
        sleep(1, SECONDS);
        get(SAVE).shouldBe(disabled);
        return this;
    }

    public SystemDictionariesAO deleteDictionaryValue(String value) {
        $(className("system-dictionary-form__items"))
                .find(byAttribute("value", value))
                .find(byXpath("following-sibling::button")).click();
        return this;
    }

    public SystemDictionariesAO deleteDictionary(String dict) {
        click(DELETE);
        new ConfirmationPopupAO<>(this)
                .ensureTitleIs(format("Are you sure you want to delete \"%s\" dictionary?", dict))
                .sleep(1, SECONDS)
                .click(button(OK.name()));
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}

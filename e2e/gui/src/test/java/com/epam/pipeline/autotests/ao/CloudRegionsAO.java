/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Selectors.by;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.$$x;
import com.codeborne.selenide.SelenideElement;
import static com.epam.pipeline.autotests.ao.Primitive.FILE_STORAGES;
import static com.epam.pipeline.autotests.ao.Primitive.OBJECT_STORAGES;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import org.openqa.selenium.By;
import static org.openqa.selenium.By.className;

import java.util.Map;

public class CloudRegionsAO implements AccessObject<CloudRegionsAO> {

    public final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(OBJECT_STORAGES, $(byXpath("//*[contains(text(), 'Object storages')]"))
                    .closest(".ant-row").find(by("role", "combobox"))),
            entry(FILE_STORAGES, $(byXpath("//*[contains(text(), 'File storages')]"))
                    .closest(".ant-row").find(by("role", "combobox"))),
            entry(SAVE, $(byId("edit-region-form-ok-button")))
    );

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public CloudRegionsAO selectRegion(String region) {
        $$(className("a-w-s-regions-form__region-row")).findBy(Condition.text(region)).click();
        return this;
    }

    public String getMountRule(Primitive rule) {
        return get(rule).getText();
    }

    public CloudRegionsAO save() {
        if (get(SAVE).is(enabled)) {
            click(SAVE);
            get(SAVE).waitUntil(disabled, DEFAULT_TIMEOUT);
        }
        return this;
    }

}

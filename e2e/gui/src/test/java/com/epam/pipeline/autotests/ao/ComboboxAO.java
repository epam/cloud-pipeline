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

import java.util.Collections;
import java.util.Map;

import static com.codeborne.selenide.CollectionCondition.sizeGreaterThan;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$$;
import static org.openqa.selenium.By.tagName;

@SuppressWarnings("unchecked")
public abstract class ComboboxAO<ELEMENT_TYPE extends ComboboxAO, PARENT_TYPE extends AccessObject>
        implements AccessObject<ELEMENT_TYPE> {

    private boolean isClosed = false;
    private PARENT_TYPE parentAO;

    public ComboboxAO(PARENT_TYPE parentAO) {
        this.parentAO = parentAO;
    }

    @Override
    public SelenideElement context() {
        return $$(byClassName("ant-select-dropdown-menu")).find(visible);
    }

    abstract SelenideElement closingElement();

    public PARENT_TYPE close() {
        if (!isClosed) {
            closingElement().shouldBe(visible).click();
        }
        return parentAO;
    }

    public ELEMENT_TYPE set(String value) {
        context().find(byText(value)).shouldBe(visible).click();
        isClosed = true;
        return (ELEMENT_TYPE) this;
    }

    public ELEMENT_TYPE shouldHaveSizeGreaterThan(int size) {
        context().findAll(tagName("li")).shouldHave(sizeGreaterThan(size));
        return (ELEMENT_TYPE) this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }
}

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
import org.openqa.selenium.Keys;

import java.util.Map;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_SELECTION;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.SEARCH_INPUT;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL_NON_SENSITIVE;
import static com.epam.pipeline.autotests.ao.Primitive.TABLE;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SelectLimitMountsPopupAO extends PopupAO<SelectLimitMountsPopupAO, Profile> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CANCEL, context().find(byText("Cancel"))),
            entry(OK, context().find(byXpath("//button/span[.='OK']"))),
            entry(CLEAR_SELECTION, context().find(byClassName("ant-btn-danger"))),
            entry(SELECT_ALL, context().find(byXpath("//button/span[.='Select all']")).closest("button")),
            entry(SELECT_ALL_NON_SENSITIVE, context().find(byXpath("//button/span[.='Select all non-sensitive']")).closest("button")),
            entry(SEARCH_INPUT, context().find(byClassName("ant-input-search"))),
            entry(TABLE, context().find(byClassName("ant-table-content")))
    );

    public SelectLimitMountsPopupAO(Profile parentAO) {
        super(parentAO);
    }

    @Override
    public Profile cancel() {
        return click(CANCEL).parent();
    }

    public SelectLimitMountsPopupAO clearSelection() {
        return click(CLEAR_SELECTION);
    }

    @Override
    public SelenideElement context() {
        return $$(byClassName("ant-modal-content")).find(visible);
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public SelectLimitMountsPopupAO clickSearch() {
        click(SEARCH_INPUT);
        return this;
    }

    public SelectLimitMountsPopupAO pressEnter() {
        actions().sendKeys(Keys.ENTER).perform();
        return this;
    }

    public SelectLimitMountsPopupAO setSearchStorage(String storage) {
        actions().sendKeys(storage).perform();
        return this;
    }

    public SelectLimitMountsPopupAO searchStorage(String storage) {
        return clickSearch()
                .setSearchStorage(storage)
                .pressEnter();
    }

    public SelectLimitMountsPopupAO selectStorage(final String storage) {
        elements().get(TABLE)
                .find(byXpath(format(
                        ".//td[contains(@class, 'ant-row') and " +
                                "starts-with(., '%s')]", storage)))
                .closest(".ant-table-row-level-0").find(byClassName("ant-checkbox-input")).click();
        return this;
    }


}

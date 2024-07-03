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
import org.openqa.selenium.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byTitle;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_SELECTION;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.SEARCH_INPUT;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL_NON_SENSITIVE;
import static com.epam.pipeline.autotests.ao.Primitive.SENSITIVE_STORAGE;
import static com.epam.pipeline.autotests.ao.Primitive.TABLE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;

public class SelectLimitMountsPopupAO<PARENT_TYPE>
        extends PopupAO<SelectLimitMountsPopupAO<PARENT_TYPE>, PARENT_TYPE> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CANCEL, context().find(byText("Cancel")).parent()),
            entry(OK, context().find(byClassName("ant-btn-primary"))),
            entry(CLEAR_SELECTION, context().find(byClassName("ant-btn-danger"))),
            entry(SELECT_ALL, context().find(byXpath("//button/span[.='Select all']")).closest("button")),
            entry(SELECT_ALL_NON_SENSITIVE, context().find(byXpath("//button/span[.='Select all non-sensitive']")).closest("button")),
            entry(SEARCH_INPUT, context().find(byClassName("ant-input"))),
            entry(TABLE, context().find(byClassName("ant-table-content"))),
            entry(SENSITIVE_STORAGE, context().find(byClassName("ant-alert-message")))
    );

    public static final String NEXT_PAGE = "Next Page";

    public SelectLimitMountsPopupAO(PARENT_TYPE parentAO) {
        super(parentAO);
    }

    @Override
    public PARENT_TYPE cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public PARENT_TYPE ok() {
        return click(OK).parent();
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> clearSelection() {
        return click(CLEAR_SELECTION).sleep(1, SECONDS);
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> selectAllNonSensitive() {
        return click(SELECT_ALL_NON_SENSITIVE).sleep(1, SECONDS);
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> selectAll() {
        return click(SELECT_ALL).sleep(1, SECONDS);
    }

    @Override
    public SelenideElement context() {
        return $$(byClassName("ant-modal-content")).find(visible);
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> clickSearch() {
        click(SEARCH_INPUT);
        return this;
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> pressEnter() {
        actions().sendKeys(Keys.ENTER).perform();
        return this;
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> searchStorage(String storage) {
        return clear(SEARCH_INPUT)
                .setValue(SEARCH_INPUT, storage)
                .pressEnter();
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> selectStorage(final String storage) {
        while (!elements().get(TABLE).find(byText(storage)).isDisplayed()
                && $(byTitle(NEXT_PAGE)).exists()
                && $(byTitle(NEXT_PAGE)).has(not(cssClass("ant-pagination-disabled")))) {
            click(byTitle(NEXT_PAGE));
        }
        elements().get(TABLE).find(byText(storage)).closest("tr").find(byClassName("ant-checkbox")).click();
        return this;
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> storagesCountShouldBeGreaterThan(int size) {
        List<SelenideElement> storagesList = new ArrayList <SelenideElement>();
        while (true) {
            storagesList.addAll($(byClassName("ant-modal-body")).find(byClassName("ant-table-tbody")).findAll("tr"));
            if (!$(byTitle(NEXT_PAGE)).exists() || $(byTitle(NEXT_PAGE)).has(cssClass("ant-pagination-disabled"))) {
                break;
            }
            click(byTitle(NEXT_PAGE));
        }
        assertTrue(storagesList.size() >= size);
        return this;
    }

    public int countObjectStorages() {
        int listTypeSize = 0;
        while (true) {
            listTypeSize += (int) $(byClassName("ant-table-tbody")).$$(byClassName("ant-table-row"))
                    .stream()
                    .map(e -> e.find(byXpath("./td[3]")))
                    .filter(e -> !e.text().equals("NFS"))
                    .count();
            if (!$(byTitle(NEXT_PAGE)).exists() || $(byTitle(NEXT_PAGE)).has(cssClass("ant-pagination-disabled"))) {
                break;
            }
            click(byTitle(NEXT_PAGE));
        }
        click(CANCEL);
        return listTypeSize;
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> validateNotFoundStorage() {
        get(TABLE).shouldHave(text("No data storages available"));
        return this;
    }
}

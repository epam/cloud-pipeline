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
import com.epam.pipeline.autotests.utils.SelenideElements;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

import java.util.Map;

import static com.codeborne.selenide.CollectionCondition.sizeGreaterThan;
import static com.codeborne.selenide.CollectionCondition.texts;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
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

public class SelectLimitMountsPopupAO extends PopupAO<SelectLimitMountsPopupAO, PipelineRunFormAO> {
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

    public SelectLimitMountsPopupAO(PipelineRunFormAO parentAO) {
        super(parentAO);
    }

    @Override
    public PipelineRunFormAO cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public PipelineRunFormAO ok() {
        return click(OK).parent();
    }

    public SelectLimitMountsPopupAO clearSelection() {
        return click(CLEAR_SELECTION).sleep(1, SECONDS);
    }

    public SelectLimitMountsPopupAO selectAllNonSensitive() {
        return click(SELECT_ALL_NON_SENSITIVE).sleep(1, SECONDS);
    }

    public SelectLimitMountsPopupAO selectAll() {
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

    public SelectLimitMountsPopupAO clickSearch() {
        click(SEARCH_INPUT);
        return this;
    }

    public SelectLimitMountsPopupAO pressEnter() {
        actions().sendKeys(Keys.ENTER).perform();
        return this;
    }

    public SelectLimitMountsPopupAO setSearchStorage(String storage) {
        clear(SEARCH_INPUT);
        setValue(SEARCH_INPUT, storage);
        return this;
    }

    public SelectLimitMountsPopupAO searchStorage(String storage) {
        return clear(SEARCH_INPUT)
                .setValue(SEARCH_INPUT, storage)
                .pressEnter();
    }

    public SelectLimitMountsPopupAO selectStorage(final String storage) {
        elements().get(TABLE).find(byText(storage)).closest("tr").find(byClassName("ant-checkbox")).click();
        return this;
    }

    public SelectLimitMountsPopupAO storagesCountShouldBeGreaterThan(int size) {
        $(byClassName("ant-table-tbody")).findAll("tr").shouldHave(sizeGreaterThan(size));
        return this;
    }

    public SelectLimitMountsPopupAO validateFields(final String... names) {
        By columnHeader = byXpath("//thead[@class='ant-table-thead']//th");
        SelenideElements.of(columnHeader)
                .shouldHave(texts(names));
        return this;
    }

    public int countObjectStorages() {
        return Integer.parseInt(get(OK).text().replaceAll("[^0-9]", "")) - countStoragesWithType("NFS");
    }

    private int countStoragesWithType(String type) {
        int listTypeSize = (int) $(byClassName("ant-table-tbody")).$$(byClassName("ant-table-row"))
                .stream()
                .map(e -> e.find(byXpath("./td[3]")))
                .filter(e -> e.text().equals(type))
                .count();
        click(CANCEL);
        return listTypeSize;
    }
}

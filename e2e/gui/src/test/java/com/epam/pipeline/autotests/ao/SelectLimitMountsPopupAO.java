/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.CollectionCondition.sizeGreaterThan;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
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

public class SelectLimitMountsPopupAO<PARENT_TYPE>
        extends PopupAO<SelectLimitMountsPopupAO<PARENT_TYPE>, PARENT_TYPE> {
    // a checkbox, the name and the type of a storage
    private static final int STORAGE_ROW_CELLS = 3;
    private static final String NFS_TYPE = "NFS";

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CANCEL, context().find(byText("Cancel")).parent()),
            entry(OK, context().find(byClassName("ant-btn-primary"))),
            entry(CLEAR_SELECTION, context().find(byClassName("ant-btn-danger"))),
            entry(SELECT_ALL, context().find(byXpath(".//button/span[.='Select all']")).closest("button")),
            entry(SELECT_ALL_NON_SENSITIVE, context().find(byXpath(".//button/span[.='Select all non-sensitive']")).closest("button")),
            entry(SEARCH_INPUT, context().find(byClassName("ant-input"))),
            entry(TABLE, context().find(byClassName("ant-table-content"))),
            entry(SENSITIVE_STORAGE, context().find(byClassName("ant-alert-message")))
    );

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

    /**
     * Clears the selection if there is one: the dialog draws "Clear selection" only while some storage is selected.
     */
    public SelectLimitMountsPopupAO<PARENT_TYPE> clearSelectionIfAny() {
        return get(CLEAR_SELECTION).is(visible) ? clearSelection() : this;
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

    public SelectLimitMountsPopupAO<PARENT_TYPE> setSearchStorage(String storage) {
        clear(SEARCH_INPUT);
        setValue(SEARCH_INPUT, storage);
        return this;
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> searchStorage(String storage) {
        return clear(SEARCH_INPUT)
                .setValue(SEARCH_INPUT, storage)
                .pressEnter();
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> selectStorage(final String storage) {
        elements().get(TABLE).find(byText(storage)).closest("tr").find(byClassName("ant-checkbox")).click();
        return this;
    }

    public SelectLimitMountsPopupAO<PARENT_TYPE> storagesCountShouldBeGreaterThan(int size) {
        $(byClassName("ant-modal-body")).find(byClassName("ant-table-tbody")).findAll("tr")
                .shouldHave(sizeGreaterThan(size));
        return this;
    }

    /**
     * Checks the columns of the storages table. The table has no header (showHeader={false},
     * client/.../launch/dialogs/AvailableStoragesBrowser.js), so a row's cells are checked instead: a checkbox,
     * the name and the type of a storage.
     */
    public SelectLimitMountsPopupAO<PARENT_TYPE> validateStorageRows() {
        final SelenideElement table = elements().get(TABLE);
        table.find("thead").shouldNot(exist);
        final SelenideElement row = table.find(byClassName("ant-table-row")).shouldBe(visible);
        row.findAll("td").shouldHave(size(STORAGE_ROW_CELLS));
        row.find("td").find(byClassName("ant-checkbox")).should(exist);
        return this;
    }

    /**
     * Counts the selected object storages, NFS ones left out, on every page of the dialog, then closes it. The
     * dialog shows 20 storages a page (PAGE_SIZE, client/.../launch/dialogs/AvailableStoragesBrowser.js), and has
     * no pagination when they fit on one page.
     */
    public int countObjectStorages() {
        int count = 0;
        for (int page = 1; ; page++) {
            count += (int) context().find(byClassName("ant-table-tbody")).findAll(byClassName("ant-table-row"))
                    .stream()
                    .filter(row -> row.find(byClassName("ant-checkbox-checked")).exists())
                    .filter(row -> !NFS_TYPE.equals(row.find(byXpath("./td[3]")).text()))
                    .count();
            final SelenideElement next = context().find(byClassName("ant-pagination-next"));
            if (!next.exists() || next.has(cssClass("ant-pagination-disabled"))) {
                break;
            }
            next.click();
            context().find(byClassName("ant-pagination-item-active")).shouldHave(exactText(String.valueOf(page + 1)));
        }
        click(CANCEL);
        return count;
    }
}

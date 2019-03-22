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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.codeborne.selenide.CollectionCondition.texts;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.tagName;

public class StorageRulesTabAO extends AbstractPipelineTabAO<StorageRulesTabAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(DELETE, $$(tagName("a")).findBy(text("Delete"))),
            entry(ADD_NEW_RULE, $$(tagName("button")).findBy(text("Add new rule")))
    );
    private final String pipelineName;

    public StorageRulesTabAO(String pipelineName) {
        super(pipelineName);
        this.pipelineName = pipelineName;
    }

    @Override
    protected StorageRulesTabAO open() {
        changeTabTo(STORAGE_RULES_TAB);
        return this;
    }

    public StorageRulesTabAO deleteStorageRule(String fileMaskString) {
        $(byText(fileMaskString)).closest("tr").findAll(tagName("td")).get(3).find(tagName("a")).shouldHave(text("Delete")).click();
        $(className("ant-confirm-title")).shouldHave(text("Do you want to delete rule \"" + fileMaskString + "\"?"));
        $(button("OK")).shouldBe(visible).click();
        return this;
    }

    public StorageRulesTabAO addNewStorageRule(String fileMaskString) {
        return openAddNewStorageDialog()
                .pipelineNameShouldBe(pipelineName)
                .setFileMask(fileMaskString)
                .enableMoveToSTS()
                .ok();
    }

    public RuleAdditionPopupAO openAddNewStorageDialog() {
        click(ADD_NEW_RULE);
        return new RuleAdditionPopupAO(this);
    }

    public List<String> rules() {
        return $(byClassName("ant-table-tbody")).findAll("tr").stream()
                .map(toColumns())
                .peek(validateDate())
                .peek(validateMoveToShortTermStorageFlag())
                .peek(validateDeleteButton())
                .map(toMask())
                .collect(Collectors.toList());
    }

    public StorageRulesTabAO shouldContainRulesTable() {
        $("thead").findAll("th")
                .shouldHave(texts("Mask", "Created", "Move to Short-Term Storage", ""));
        return this;
    }

    public StorageRulesTabAO shouldContainRule(int row, String mask) {
        $(byClassName("ant-table-tbody")).findAll("tr").stream()
                .skip(row)
                .limit(1)
                .map(toColumns())
                .peek(validateMaskEqualsTo(mask))
                .peek(validateDate())
                .peek(validateMoveToShortTermStorageFlag())
                .forEach(validateDeleteButton());
        return this;
    }

    private Function<SelenideElement, ElementsCollection> toColumns() {
        return row -> row.findAll("td");
    }

    private Function<ElementsCollection, String> toMask() {
        return columns -> columns.get(0).text();
    }

    private Consumer<ElementsCollection> validateDeleteButton() {
        return columns -> columns.get(3).find("a").shouldHave(text("Delete"));
    }

    private Consumer<ElementsCollection> validateMoveToShortTermStorageFlag() {
        return columns -> columns.get(2).find("input")
                .shouldHave(
                        attribute("type", "checkbox"),
                        attribute("value", "on")
                )
                .shouldBe(disabled);
    }

    private Consumer<ElementsCollection> validateMaskEqualsTo(String mask) {
        return columns -> columns.get(0).shouldHave(text(mask));
    }

    private Consumer<ElementsCollection> validateDate() {
        return columns -> Utils.validateDateTimeString(columns.get(1).text());
    }

    public StorageRulesTabAO rulesCountShouldBe(int size) {
        $(byClassName("ant-table-tbody")).findAll("tr").shouldHaveSize(size);
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class RuleAdditionPopupAO extends PopupAO<RuleAdditionPopupAO, StorageRulesTabAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(FILE_MASK, $(byId("fileMask"))),
                entry(MOVE_TO_STS, $(byText("Move to STS")).closest(".ant-form-item").find(className("ant-checkbox"))),
                entry(CREATE, $(button("Create")))
        );

        public RuleAdditionPopupAO(StorageRulesTabAO parentAO) {
            super(parentAO);
        }

        public RuleAdditionPopupAO pipelineNameShouldBe(String pipelineName) {
            $(byAttribute("title", pipelineName)).shouldHave(text(pipelineName));
            return this;
        }

        public RuleAdditionPopupAO setFileMask(String fileMask) {
            return setValue(FILE_MASK, fileMask);
        }

        public RuleAdditionPopupAO enableMoveToSTS() {
            return click(MOVE_TO_STS);
        }

        @Override
        public StorageRulesTabAO ok() {
            return click(CREATE).parent();
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }
}

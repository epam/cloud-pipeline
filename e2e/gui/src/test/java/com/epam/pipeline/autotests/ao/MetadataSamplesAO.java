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
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.SelenideElements;
import com.google.common.collect.Comparators;
import java.util.Arrays;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.codeborne.selenide.CollectionCondition.texts;
import static com.codeborne.selenide.Condition.exist;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners.confine;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;

import static java.util.Objects.isNull;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertTrue;

public class MetadataSamplesAO implements AccessObject<MetadataSamplesAO> {

    public static final By hideMetadata = byId("hide-metadata-button");
    public static final By rows = byClassName("rt-tr-group");
    public static final By cells = byClassName("browser__metadata-column-cell");
    public static final By columnHeader = byCssSelector(".rt-th.rt-resizable-header");
    public static final By decreaseOrderIcon = byClassName("anticon-caret-down");
    public static final By increaseOrderIcon = byClassName("anticon-caret-up");
    public static final By searchMetadata = byId("search-metadata-input");
    public static final By showColumnsButton = buttonByIconClass("anticon-bars");
    public static final By checkBox = byClassName("ant-checkbox-wrapper");


    private List<String> fields;

    public MetadataSamplesAO performForEachRow(final Consumer<SampleRow> actions) {
        SelenideElements.of(rows).stream()
                .map(SampleRow::new)
                .forEach(actions);
        return this;
    }

    public MetadataSamplesAO validateFields(final String... names) {
        SelenideElements.of(columnHeader)
                .shouldHave(texts(names));
        return this;
    }

    public MetadataSamplesAO ensureNumberOfRowsIs(int expectedNumberOfRows) {
        SelenideElements.of(rows).shouldHaveSize(expectedNumberOfRows);
        return this;
    }

    public SampleRow getRow(final int rowNumber) {
        final ElementsCollection allRows = SelenideElements.of(rows);
        return new SampleRow(allRows.get(rowNumber - 1));
    }

    public List<String> getColumnContent(final String fieldName) {
        final int fieldIndex = getFields().indexOf(fieldName) + 1;
        return SelenideElements.of(rows).stream()
                .map(row -> row.findAll(cells).get(fieldIndex))
                .map(SelenideElement::text)
                .collect(Collectors.toList());
    }

    public static By columnHeader(String columnName) {
        return confine(withText(columnName), columnHeader, "column header");
    }

    private List<String> getFields() {
        if (isNull(fields)) {
            fields = SelenideElements.of(columnHeader).texts();
        }
        return fields;
    }

    public MetadataSamplesAO validateSortedByIncrease(final String fieldName) {
        validateSortedBy(fieldName, Comparator.naturalOrder());
        return this;
    }

    public MetadataSamplesAO validateSortedByDecrease(final String fieldName) {
        validateSortedBy(fieldName, Comparator.reverseOrder());
        return this;
    }

    public ShowColumnsMenu showColumnsMenu() {
        click(showColumnsButton);
        return new ShowColumnsMenu(this);
    }

    private void validateSortedBy(final String fieldName, final Comparator<String> comparator) {
        final List<String> columnContent = getColumnContent(fieldName);
        assertTrue(Comparators.isInOrder(columnContent, comparator),
                String.format("%s column values %s are not sorted properly", fieldName, columnContent)
        );
    }

    public MetadataSamplesAO selectRows(int... indexes) {
        Arrays.stream(indexes)
                .mapToObj(this::getRow)
                .forEach(SampleRow::selectRow);

        return this;
    }

    public class SampleRow implements AccessObject<SampleRow> {
        private final SelenideElement particularRow;

        public SampleRow(final SelenideElement particularRow) {
            this.particularRow = particularRow;
        }

        public MetadataSectionAO clickOnRow() {
            particularRow.click();
            return new MetadataSectionAO(this);
        }

        public SampleRow ensureCell(final String fieldName, Condition condition) {
            getCell(fieldName).particularCell.should(condition);
            return this;
        }

        public Cell getCell(final String fieldName) {
            final int fieldIndex = getFields().indexOf(fieldName) + 1;
            return getCell(fieldIndex);
        }

        private Cell getCell(final int fieldIndex) {
            return new Cell(particularRow.findAll(cells).get(fieldIndex));
        }

        public SampleRow selectRow() {
            particularRow.find(className("ant-checkbox-wrapper")).shouldBe(visible).click();

            return this;
        }
    }

    public static class Cell implements AccessObject<Cell> {
        private final SelenideElement particularCell;

        public Cell(SelenideElement cell) {
            particularCell = cell;
        }

        public void ensureCellContainsHyperlink(String text) {
            particularCell.shouldHave(hyperlink(text));
        }

        public void ensureCellContainsHyperlink() {
            particularCell.shouldHave(hyperlink());
        }

        public void ensureCellContains(String substring) {
            particularCell.find(withText(substring)).should(exist);
        }

        public String getCellContent() {
            return particularCell.getText();
        }

        public MetadataSectionAO clickOnHyperlink() {
            particularCell.find(tagName("a")).click();
            return new MetadataSectionAO(this);
        }

        public static Condition hyperlink() {
            return hyperlink("");
        }

        public static Condition hyperlink(String text) {
            return new Condition("hyperlink") {
                @Override
                public boolean apply(final WebElement element) {
                    return element.findElement(tagName("a")).isDisplayed()
                            && element.findElement(withText(text)).isDisplayed();
                }
            };
        }
    }

    public static class ShowColumnsMenu implements AccessObject<ShowColumnsMenu> {
        private final MetadataSamplesAO parentAO;

        public ShowColumnsMenu(final MetadataSamplesAO parentAO) {
            this.parentAO = parentAO;
        }

        public ShowColumnsMenu clickCheckboxForField(final String fieldName) {
            context()
                    .find(withText(fieldName))
                    .closest(".ant-checkbox-wrapper")
                    .should(visible)
                    .click();
            parentAO.fields = null;
            return this;
        }

        public MetadataSamplesAO close() {
            parentAO.click(showColumnsButton);
            return parentAO;
        }

        @Override
        public SelenideElement context() {
            return $(PipelineSelectors.visible(byClassName("ant-popover-content")));
        }
    }
}

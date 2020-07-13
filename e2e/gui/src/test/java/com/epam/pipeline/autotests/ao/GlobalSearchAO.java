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

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.codeborne.selenide.CollectionCondition.sizeGreaterThanOrEqual;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.codeborne.selenide.Selenide.switchTo;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners.confine;
import static java.util.stream.Collectors.toList;
import static org.testng.Assert.assertEquals;

public class GlobalSearchAO implements AccessObject<GlobalSearchAO> {

    private Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(FOLDERS, context().find(type("FOLDER"))),
            entry(PIPELINES, context().find(type("PIPELINE"))),
            entry(RUNS, context().find(type("RUN"))),
            entry(TOOLS, context().find(type("TOOL"))),
            entry(DATA, context().find(type("DATA"))),
            entry(ISSUES, context().find(type("ISSUE"))),
            entry(SEARCH, context().find(byAttribute("placeholder", C.SEARCH_PREFIX))),
            entry(QUESTION_MARK, context().find(byClassName("earch__hint-icon-container"))),
            entry(SEARCH_RESULT, context().find(byId("search-results")))
    );

    /**
     * Checks if an type button element is disable.
     */
    public static Condition disable = new Condition("be disable type") {
        @Override
        public boolean apply(final WebElement element) {
            return cssClass("earch__disabled").apply(element);
        }
    };

    /**
     * Checks that type button is selected
     * @see WebElement#isSelected()
     */
    public static Condition selected = new Condition("selected") {
        @Override
        public boolean apply(WebElement element) {
            return cssClass("earch__active").apply(element);
        }
    };

    public static By searchItem(final String item) {
        return confine(byText(item), byId("search-results"), "search result item");
    }

    public static By searchItemWithText(final String item) {
        return confine(withText(item), byId("search-results"), "search result item");
    }

    public GlobalSearchAO search(final String query) {
        clear(SEARCH);
        get(SEARCH).shouldBe(enabled).sendKeys(Keys.chord(Keys.CONTROL), query);
        return this;
    }

    public SearchResultItemPreviewAO openSearchResultItem(final String item) {
        return getSearchResultItemPreviewAO(searchItem(item));
    }

    public SearchResultItemPreviewAO openSearchResultItemWithText(final String item) {
        return getSearchResultItemPreviewAO(searchItemWithText(item));
    }

    public SearchResultItemPreviewAO getSearchResultItemPreviewAO(final By searchItem) {
        hover(searchItem);
        return new SearchResultItemPreviewAO(this);
    }

    public <TARGET extends AccessObject<TARGET>> TARGET moveToSearchResultItem(
            final String name,
            final Supplier<TARGET> targetSupplier) {
        return getTarget(targetSupplier, searchItem(name));
    }

    public <TARGET extends AccessObject<TARGET>> TARGET moveToSearchResultItemWithText(
            final String name,
            final Supplier<TARGET> targetSupplier) {
        return getTarget(targetSupplier, searchItemWithText(name));
    }

    private <TARGET extends AccessObject<TARGET>> TARGET getTarget(final Supplier<TARGET> targetSupplier, final By searchItem) {
        click(searchItem);
        return targetSupplier.get();
    }

    public GlobalSearchAO validateSearchResults(final int count, final String itemName) {
        if (count <= 0) {
            messageShouldAppear("Nothing found");
            return this;
        }
        get(SEARCH_RESULT)
                .findAll(".earch__search-result-item")
                .shouldHaveSize(count)
                .forEach(i -> i.shouldHave(text(itemName)));
        return this;
    }

    public GlobalSearchAO validateCountSearchResults(final int count) {
        get(SEARCH_RESULT)
                .findAll(".earch__search-result-item")
                .shouldHave(sizeGreaterThanOrEqual(count));
        return this;
    }

    public GlobalSearchAO close() {
        actions().sendKeys(Keys.ESCAPE).perform();
        return this;
    }

    public By type(final String name) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context.findElements(byClassName("earch__type-button")).stream()
                        .filter(webElement -> webElement.getText().contains(name))
                        .collect(toList());
            }
        };
    }

    @Override
    public SelenideElement context() {
        return $(PipelineSelectors.visible(byClassName("earch__search-container")));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class SearchResultItemPreviewAO extends PopupAO<SearchResultItemPreviewAO, GlobalSearchAO> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(TITLE, context().find(byClassName("review__title"))),
                entry(DESCRIPTION, context().find(byClassName("review__description"))),
                entry(HIGHLIGHTS, context().find(byClassName("review__highlights"))),
                entry(PREVIEW, context().find(byClassName("review__content-preview"))),
                entry(INFO, context().find(byClassName("review__info"))),
                entry(INFO_TAB, context().find(byClassName("review__run-table"))),
                entry(TAGS, context().find(byClassName("review__tags"))),
                entry(PREVIEW_TAB, context().find(By.xpath(".//div[@class='review__content-preview'][2]"))),
                entry(ATTRIBUTES, context().find(byClassName("review__attribute"))),
                entry(TITLE_FIELD, context().find(byClassName("review__sub-title"))),
                entry(SHORT_DESCRIPTION, context().find(byClassName("review__tool-description"))),
                entry(ENDPOINT, $(withText("Endpoints:")).closest("tr").find("a"))
        );
        private static Condition completed = Condition.or("finished",
                LogAO.Status.SUCCESS.reached, LogAO.Status.STOPPED.reached, LogAO.Status.FAILURE.reached);

        SearchResultItemPreviewAO(final GlobalSearchAO parentAO) {
            super(parentAO);
        }

        public GlobalSearchAO close() {
            return parent().close();
        }

        @Override
        public SelenideElement context() {
            return $$(byClassName("earch__preview")).find(visible);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public SearchResultItemPreviewAO checkCompletedField() {
            ElementsCollection list = get(INFO_TAB).findAll(By.xpath(".//tr"));
            IntStream.range(1, list.size()).forEach(i -> list.get(i).shouldBe(completedFieldCorrespondStatus()));
            return this;
        }

        private Condition completedFieldCorrespondStatus() {
            return new Condition("completed field correspond Status") {
                @Override
                public boolean apply(final WebElement element) {
                    final String dateRegex = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$";
                    return $(element).find(By.xpath("./td[1]")).has(completed)
                            ? $(element).find(By.xpath("./td[4]")).text().matches(dateRegex)
                            : $(element).find(By.xpath("./td[4]")).text().equals("");
                }
            };
        }

        public SearchResultItemPreviewAO checkTags(String ... list) {
            String tags = String.format("%s %s %s %s", get(TAGS).find(By.xpath(".//span[2]/span")).text(),
                    get(TAGS).find(By.xpath(".//span[3]")).text(),
                    get(TAGS).find(By.xpath(".//span[4]")).text(),
                    get(TAGS).find(By.xpath(".//span[5]")).text());
            Arrays.stream(list).forEach(tags::contains);
            return this;
        }

        public ToolPageAO clickOnEndpointLink() {
            String endpoint = getEndpointLink();
            get(ENDPOINT).click();
            switchTo().window(1);
            return new ToolPageAO(endpoint);
        }

        public String getEndpointLink() {
            return get(ENDPOINT).shouldBe(visible).attr("href");
        }

        public SearchResultItemPreviewAO checkEndpointsLink(String expectedLink) {
            assertEquals(getEndpointLink(), expectedLink);
            return this;
        }
    }
}

package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners.confine;
import static java.util.stream.Collectors.toList;

public class GlobalSearchAO implements AccessObject<GlobalSearchAO> {

    private Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(FOLDERS, context().find(type("FOLDER"))),
            entry(PIPELINES, context().find(type("PIPELINE"))),
            entry(RUNS, context().find(type("RUN"))),
            entry(TOOLS, context().find(type("TOOL"))),
            entry(DATA, context().find(type("DATA"))),
            entry(ISSUES, context().find(type("ISSUE"))),
            entry(SEARCH, context().find(byAttribute("placeholder", "MagellanDev search"))),
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
        return confine(byText(item), byId("search-results"),"search result item");
    }

    public GlobalSearchAO search(final String query) {
        clear(SEARCH);
        get(SEARCH).shouldBe(enabled).sendKeys(Keys.chord(Keys.CONTROL), query);
        return this;
    }

    public SearchResultItemPreviewAO openSearchResultItem(final String item) {
        final By searchItem = searchItem(item);
        hover(searchItem);
        return new SearchResultItemPreviewAO(this);
    }

    public <TARGET extends AccessObject<TARGET>> TARGET moveToSearchResultItem(final String name,
                                                                               final Supplier<TARGET> targetSupplier) {
        final By searchItem = searchItem(name);
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
                entry(PREVIEW, context().find(byClassName("review__content-preview")))
        );

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
    }
}

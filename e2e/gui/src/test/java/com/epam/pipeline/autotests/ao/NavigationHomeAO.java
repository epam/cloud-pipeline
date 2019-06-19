package com.epam.pipeline.autotests.ao;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.List;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;
import static java.util.stream.Collectors.toList;

public class NavigationHomeAO implements AccessObject<PipelinesLibraryAO> {

    public static By panel(final String panelName) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $(".home-page__global-container")
                        .findAll(".home-page__panel").stream()
                        .filter(element -> text(panelName).apply(element))
                        .collect(toList());
            }
        };
    }

    public GlobalSearchAO globalSearch() {
        actions().sendKeys(Keys.chord(Keys.CONTROL, "F")).perform();
        return new GlobalSearchAO();
    }

}

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

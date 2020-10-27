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
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURATION;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.testng.Assert.fail;

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
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CONFIGURATION, $(button("Configure")))
    );

    public GlobalSearchAO globalSearch() {
        actions().sendKeys(Keys.chord(Keys.CONTROL, "F")).perform();
        return new GlobalSearchAO();
    }

    public ConfigureDashboardPopUp configureDashboardPopUpOpen() {
        click(CONFIGURATION);
        return new ConfigureDashboardPopUp(this);
    }

    public NavigationHomeAO checkEndpointsLinkOnServicesPanel(String endpoint) {
       return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class ConfigureDashboardPopUp extends PopupAO<ConfigureDashboardPopUp, NavigationHomeAO> {

        public ConfigureDashboardPopUp(NavigationHomeAO parentAO) {
            super(parentAO);
        }

        public ConfigureDashboardPopUp markCheckboxByName(String name) {
            if (name.equals("Services")) {
                SelenideElement checkBox = context()
                        .find(byXpath(format(".//span[.='%s']/preceding-sibling::span", name)));
                if(!checkBox.has(cssClass("ant-checkbox-checked"))) {
                    checkBox.click();
                }
            } else {
                fail("Wrong checkbox name was selected");
            }
            return this;
        }
    }
}

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
package com.epam.pipeline.autotests.utils;

import com.codeborne.selenide.Condition;
import java.util.Arrays;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.Color;

import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static java.util.Objects.requireNonNull;

/**
 * Project-specific conditions. All conditions supposed to be generic enough to be used all over the application.
 */
public interface Conditions {
    /**
     * Checks if an element is expanded tab.
     * <p>
     * It takes any parent element and then uses its scope to find a tab.
     */
    Condition expandedTab = new Condition("be expanded tab") {
        @Override
        public boolean apply(final WebElement element) {
            final WebElement tab = element.findElement(byAttribute("role", "tab"));
            return attribute("aria-expanded", "true").apply(tab);
        }
    };

    /**
     * Checks if an element is collapsed tab.
     * <p>
     * It takes any parent element and then uses its scope to find a tab.
     */
    Condition collapsedTab = new Condition("be collapsed tab") {
        @Override
        public boolean apply(final WebElement element) {
            final WebElement tab = element.findElement(byAttribute("role", "tab"));
            return attribute("aria-expanded", "false").apply(tab);
        }
    };

    /**
     * Checks if an element is selected tab.
     */
    Condition selectedTab = new Condition("be selected tab") {
        @Override
        public boolean apply(final WebElement element) {
            return attribute("aria-selected", "true").apply(element);
        }
    };

    /**
     * Checks if a combo box has supposed value.
     *
     * @param supposedValue The value (exact match, case-sensitive) that is supposed to be selected in a combo box.
     * @return The {@link Condition} that checks if a {@link PipelineSelectors#combobox(String) combo box}
     * has a particular value.
     */
    static Condition selectedValue(final String supposedValue) {
        return new Condition("value " + supposedValue) {
            @Override
            public boolean apply(final WebElement element) {
                final WebElement selected = element.findElement(By.className("ant-select-selection-selected-value"));
                return matchText(supposedValue).apply(selected);
            }

            @Override
            public String actualValue(final WebElement element) {
                final WebElement selected = element.findElement(By.className("ant-select-selection-selected-value"));
                return selected.getText();
            }
        };
    }

    /**
     * Checks if an element contains given qualifiers.
     *
     * @param qualifiers Qualifiers to check.
     * @return The {@link Condition} that checks if an element contains all of specifier {@code qualifiers}.
     */
    static Condition contains(final By... qualifiers) {
        return new Condition("contains") {
            @Override
            public boolean apply(final WebElement element) {
                return Arrays.stream(qualifiers)
                             .allMatch(qualifier -> $(element).find(qualifier).is(visible));
            }
        };
    }

    /**
     * <p>Synonym for #contains. Useful for better readability.</p>
     */
    static Condition contain(final By... qualifiers) {
        return contains(qualifiers);
    }

    /**
     * Checks if the text of an element exact matches the provided pattern.
     * <p>
     * Note:
     * It uses exact match whereas {@link Condition#matchText(String)} prepend and append the provided pattern
     * with part of any symbol in any quantity (".*"). So can be used as "contains pattern", however,
     * this method can be used as "equals pattern".
     *
     * @param pattern Regular expression to match text.
     * @return Condition that can be used to check if the text of an element exact matches some pattern.
     */
    static Condition textMatches(final String pattern) {
        requireNonNull(pattern, "Pattern should be an object");
        return new Condition(String.format("text matches {%s}", pattern)) {
            @Override
            public boolean apply(final WebElement element) {
                return element.getText().matches(pattern);
            }
        };
    }

    /**
     * Checks if the text of an element starts with the provided prefix.
     *
     * @param prefix Regular expression to match text.
     * @return Condition that can be used to check if the text of an element exact matches some pattern.
     */
    static Condition startsWith(final String prefix) {
        return textMatches(String.format("^%s", prefix));
    }

    /**
     * Checks if the text of an element ends with the provided postfix.
     *
     * @param postfix Regular expression to match text.
     * @return Condition that can be used to check if the text of an element exact matches some pattern.
     */
    static Condition endsWith(final String postfix) {
        return textMatches(String.format("%s$", postfix));
    }

    /**
     * Checks if the input value contains the given substring.
     *
     * @param value Substring of the input value.
     * @return Condition that checks if the input value contains the given substring.
     */
    static Condition valueContains(final String value) {
        requireNonNull(value, "Value should be an object");
        return new Condition("contains in value") {
            @Override
            public boolean apply(final WebElement element) {
                return element.getAttribute("value").contains(value);
            }

            @Override
            public String toString() {
                return String.format("%s '%s'", name, value);
            }
        };
    }

    /**
     * Checks if the given code editor is editable.
     *
     * @return Condition that checks if the code editor is editable.
     */
    static Condition readOnlyEditor() {
        return new Condition("read only code editor") {
            @Override
            public boolean apply(final WebElement element) {
                return !element.findElements(byClassName("code-editor__read-only-editor")).isEmpty();
            }
        };
    }

    /**
     * Checks if an element has the given color.
     *
     * @param color Expected {@code color}
     * @return Condition that checks if element has given background color.
     */
    static Condition backgroundColor(final Color color) {
        return new Condition("background color " + color) {
            @Override
            public boolean apply(final WebElement element) {
                final Color actualColor = actualColor(element);
                return color.equals(actualColor);
            }

            @Override
            public String actualValue(final WebElement element) {
                return actualColor(element).asRgba();
            }

            private Color actualColor(final WebElement element) {
                return Color.fromString(element.getCssValue("background-color"));
            }
        };
    }

    Condition selectedMenuItem = new Condition("selected navigation menu item") {
        @Override
        public boolean apply(final WebElement element) {
            return Condition.or("selected navigation menu item",
                    cssClass("navigation__navigation-menu-item-selected"),
                    cssClass("navigation__highlighted-navigation-menu-item-selected"))
                    .apply(element);
        }
    };
}

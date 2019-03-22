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
import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.List;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners.confine;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.className;

/**
 * Project-specific selectors. All selectors supposed to be generic enough to be used all over the application.
 */
public interface PipelineSelectors {

    By displayAttributes = byId("display-attributes");
    By attributesMenu = visible(byClassName("ant-dropdown-menu"));
    By showAttributes = confine(byText("Attributes"), attributesMenu,"show attributes");
    By hideAttributes = confine(byText("Hide attributes"), attributesMenu, "hide attributes");

    /**
     * Returns {@link By} qualifier of a button with text (case-sensitive full match).
     *
     * @param text Content text of the button to search for.
     * @return {@link By} that can be used to find a {@link SelenideElement} of a button with provided text.
     */
    static By button(final String text) {
        return visible(By.xpath(String.format(".//button[normalize-space(.) = '%s']", text)));
    }

    static By buttonByIconClass(final String className) {
        return visible(By.xpath(String.format(".//button[./i[contains(@class, '%s')]]", className)));
    }

    static By editButton() {
        return buttonByIconClass("anticon-edit");
    }

    static By settingsButton() {
        return buttonByIconClass("anticon-setting");
    }

    static By deleteButton() {
        return buttonByIconClass("anticon-delete");
    }

    static By visible(final By by) {
        return Combiners.select(Condition.visible, by, String.format("visible(%s)", by));
    }

    static By metadataWithName(final String name) {
        final String metadataClass = "pipelines-library-tree-node-metadata";
        final String titleClass = "pipelines-library__tree-item-title";
        return byXpath(String.format(
                ".//*[contains(@class, '%s') and .//*[contains(@class, '%s') and text() = '%s']]",
                metadataClass, titleClass, name
        ));
    }

    /**
     * Selects a pipeline in a tree by name.
     *
     * @param name Case sensitive string with exact text of a searching folder.
     * @return Qualifier of a pipeline with exact {@code name} in a tree.
     */
    static By pipelineWithName(final String name) {
        final String pipelineClass = "pipelines-library-tree-node-pipeline";
        final String titleClass = "pipelines-library__tree-item-title";
        return byXpath(String.format(
            ".//*[contains(@class, '%s') and .//*[contains(@class, '%s') and .//*[text() = '%s']]]",
            pipelineClass, titleClass, name
        ));
    }

    /**
     * Selects a pipeline in a tree by name.
     *
     * @param name Case sensitive string with exact text of a searching folder.
     * @param titleClass Title class of the element in a tree.
     * @return Qualifier of a pipeline with exact {@code name} in a tree.
     */
    static By pipelineWithName(final String name, final String titleClass) {
        final String pipelineClass = "pipelines-library-tree-node-pipeline";
        return byXpath(String.format(
                ".//*[contains(@class, '%s') and .//*[contains(@class, '%s') and text() = '%s']]",
                pipelineClass, titleClass, name
        ));
    }

    /**
     * Selects a field by its exact label text.
     *
     * @param label Exact text of a label
     * @return Qualifier of a field with exact {@code label} that contain both label and input.
     */
    static By fieldWithLabel(final String label) {
        final String fieldRowClass = "form-item";
        final String fieldLabelClass = "ant-form-item-label";
        return byXpath(String.format(
                "(//*[contains(@class, '%s') and .//*[contains(@class, '%s') and .//*[contains(text(), '%s')]]])[last()]",
            fieldRowClass, fieldLabelClass, label
        ));
    }

    /**
     * Selects an input of a field.
     *
     * Can be used with {@link com.epam.pipeline.autotests.ao.AccessObject#setValue}
     *
     * @param field Field, {@link By} qualifier of a particular field that contain input you need.
     * @return Qualifier of a field with exact {@code label} that contain both label and input.
     */
    static By inputOf(final By field) {
        final By target = byXpath(".//input[@type = 'text']");
        return Combiners.confine(target, field, "input of " + field);
    }

    /**
     * Selects combo box by its placeholder.
     *
     * @param placeholder Placeholder in most cases is the same with its label.
     * @return Qualifier of a combo box.
     */
    static By combobox(final String placeholder) {
        final String comboboxClass = "ant-select-selection__rendered";
        final String placeholderClass = "ant-select-selection__placeholder";
        return byXpath(String.format(
            ".//*[@class = '%s' and .//*[@class = '%s' and text() = '%s']]",
            comboboxClass, placeholderClass, placeholder
        ));
    }

    static By controlWithin(final By field) {
        final By control = byXpath(".//*[contains(concat(' ', @class, ' '), ' ant-form-item-control ')]");
        return Combiners.confine(control, field, String.format("control within %s", field));
    }

    static By runWithId(final String id) {
        final String runClass = String.format("run-%s", id);
        return byClassName(runClass);
    }

    /**
     * Selects dropdown menu.
     *
     * Note!
     * It is supposed that there is only one drop-down menu at the moment. To be sure wrap it in the
     * qualifier of a {@link #visible(By) visible} element, {@code visible(menu())}.
     *
     * @return Qualifier of a dropdown menu.
     */
    static By menu() {
        final String menuClass = "ant-select-dropdown-menu";
        return byXpath(String.format(".//ul[@role = 'menu' and contains(@class, '%s')]", menuClass));
    }

    /**
     * Selects menu item by its exact text dropdown.
     *
     * @return Qualifier of a dropdown menu.
     */
    static By menuitem(final String exactText) {
        final String menuItemClass = "ant-select-dropdown-menu-item";
        return byXpath(String.format(
            ".//li[@role = 'menuitem' and contains(@class, '%s') and text() ='%s']",
            menuItemClass, exactText
        ));
    }

    /**
     * Selects menu item by its exact text dropdown and class name.
     *
     * @return Qualifier of a dropdown menu.
     */
    static By menuitem(final String menuItemClass, final String exactText) {
        return byXpath(String.format(
                ".//li[@role = 'menuitem' and contains(@class, '%s') and text() ='%s']",
                menuItemClass, exactText
        ));
    }

    /**
     * Selects combo box within some specified context.
     *
     * @param field Actually not only field is allowed you can pass any
     * @return Qualifier of a combo box.
     */
    static By comboboxOf(final By field) {
        return Combiners.confine(className("ant-select-selection__rendered"), field, "combo box of " + field);
    }

    static By checkboxOf(final By field) {
        return Combiners.confine(byXpath(".//input[@type = 'checkbox']"), field, "check box of " + field);
    }

    static By hintOf(final By field) {
        return Combiners.confine(className("anticon-question-circle"), field, "hint of " + field);
    }

    /**
     * Selects collapsible panel by its exact name.
     *
     * @param name Case-sensitive exact {@code name} of the searching panel.
     * @return Qualifier of a panel with exact {@code name}.
     */
    static By collapsiblePanel(final String name) {
        requireNonNull(name);
        final String panelClass = "ant-collapse-item";
        return byXpath(String.format(
            ".//*[contains(@class, '%s') and .//*[@role = 'tab' and .//text() = '%s']]",
            panelClass, name
        ));
    }

    /**
     * Selects modal by its exact title.
     *
     * @param titlePrefix Case-sensitive {@code titlePrefix} of the searching modal.
     * @param titlePostfix Case-sensitive {@code titlePostfix} of the searching modal.
     * @return Qualifier of a modal where title has given {@code titlePrefix} and {@code titlePostfix}.
     */
    static By modalWithTitle(final String titlePrefix, final String titlePostfix) {
        requireNonNull(titlePrefix);
        requireNonNull(titlePostfix);
        return byXpath(String.format(
            ".//*[contains(@role, 'dialog') and " +
                    ".//*[contains(@class, 'ant-modal-title') and contains(text(), '%s') and contains(text(), '%s')]]",
            titlePrefix,
            titlePostfix
        ));
    }

    /**
     * Selects modal by its exact title.
     *
     * @param title Case-sensitive {@code title} of the searching modal.
     * @return Qualifier of a modal with exact {@code title}.
     */
    static By modalWithTitle(final String title) {
        requireNonNull(title);
        return byXpath(String.format(
            ".//*[contains(@role, 'dialog') and .//*[contains(@class, 'ant-modal-title') and text() = '%s']]",
            title
        ));
    }

    /**
     * Selects tab by its exact name.
     *
     * @param name Case-sensitive {@code name} of the searching tab.
     * @return Qualifier of a tab with exact {@code name}.
     */
    static By tabWithName(final String name) {
        return byXpath(String.format(
            ".//*[contains(@class, 'ant-menu-item') and .//*[text() = '%s']]",
            name
        ));
    }

    /**
     * Selects configuration in pipeline tree by configuration name.
     *
     * @param name Case-sensitive {@code name} of the searching configuration.
     * @return Qualifier of a configuration in pipeline tree with exact {@code name}.
     */
    static By configurationWithName(final String name) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context.findElements(byCssSelector("[class^=\"pipelines-library-tree-node-configuration\"]")).stream()
                        .filter(element -> !element.findElements(byText(name)).isEmpty())
                        .collect(toList());
            }

            @Override
            public String toString() {
                return String.format("configuration with name %s", name);
            }
        };
    }

    /**
     * Selects version of the pipeline.
     *
     * @return Qualifier of a version of the pipeline.
     */
    static By version() {
        return new By() {

            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context
                        .findElements(byClassName("ant-table-row")).stream()
                        .filter(element -> !element.findElements(byCssSelector(".ant-table-row .anticon-tag")).isEmpty())
                        .collect(toList());
            }

            @Override
            public String toString() {
                return "version of pipeline";
            }
        };
    }

    /**
     * Selects element with the given text even if the text is split by React framework.
     *
     * Example:
     *
     * The element below could be found by calling {@code elementWithText(tagName("span"), "Stop pipeline")}.
     *
     * <pre>
     *     {@code
     *          <span>
     *              <!-- react-text: 42 -->
     *              "Stop "
     *              <!-- /react-text -->
     *              <!-- react-text: 43 -->
     *              "pipeline"
     *              <!-- /react-text -->
     *          </span>
     *     }
     * </pre>
     *
     * @param qualifier Of the element to be found.
     * @param text      To be found in element's text.
     * @return Qualifier of the element with the given text.
     */
    static By elementWithText(final By qualifier, final String text) {
        return Combiners.select(text(text), qualifier, String.format("element with text %s %s", text, qualifier.toString()));
    }

    /**
     * Returns the given qualifier with additional information in {@code toString()}
     * about the intention of using the output qualifier as a context for searching.
     */
    static By in(final By scope) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context.findElements(scope);
            }

            @Override
            public String toString() {
                return String.format("in context of %s", scope);
            }
        };
    }

    /**
     * Returns the qualifier of the document body context.
     */
    static By globalContext() {
        return By.tagName("body");
    }

    /**
     * Returns visible combobox dropdown qualifier.
     */
    static By comboboxDropdown() {
        return visible(byClassName("ant-select-dropdown"));
    }

    /**
     * Returns qualifier in global scope.
     */
    static By ignoreScope(final By target) {
        return Combiners.confine(target, byXpath("//ancestor-or-self::body"), "in global scope " + target);
    }

    /**
     * Private methods holder interface. It is not supposed to be used nowhere but in this class.
     */
    interface Combiners {
        /**
         * Composition on qualifiers performed.
         *
         * @param target Qualifier of an element to search.
         * @param scope Qualifier of a scope context.
         * @param humanReadable Short description of composed qualifier that is used in test results.
         * @return Qualifier that searches for a {@code target} element in a particular {@code scope}
         */
        static By confine(final By target, final By scope, final String humanReadable) {
            requireNonNull(target);
            requireNonNull(scope);
            requireNonNull(humanReadable);
            return new By() {
                @Override
                public List<WebElement> findElements(final SearchContext context) {
                    return context.findElements(scope).stream()
                                  .flatMap(scopeContext -> scopeContext.findElements(target).stream())
                                  .collect(toList());
                }

                @Override
                public String toString() {
                    return humanReadable;
                }
            };
        }
        /**
         * @see #confine(By, By, String)
         */
        static By confine(final By target, final By scope) {
            final String humanReadable = String.format("{ %s in context of %s }", target, scope);
            return confine(target, scope, humanReadable);
        }

        /**
         * Compose qualifier with some criteria to filter out elements that doesn't meet this criteria.
         *
         * @param criteria Condition that elements must match.
         * @param qualifier Used to perform starting search.
         * @param humanReadable Short description of composed qualifier that is used in test results.
         * @return Qualifier that searches for a {@code qualifier} that meet some particular {@code criteria}.
         */
        static By select(final Condition criteria, final By qualifier, final String humanReadable) {
            return new By() {
                @Override
                public List<WebElement> findElements(final SearchContext context) {
                    return context.findElements(qualifier).stream()
                                  .filter(criteria)
                                  .collect(toList());
                }

                @Override
                public String toString() {
                    return humanReadable;
                }
            };
        }

        /**
         * @see #select(Condition, By, String)
         */
        static By select(final Condition criteria, final By qualifier) {
            final String humanReadable = String.format("{ %s that meets criteria %s }", qualifier, criteria);
            return select(criteria, qualifier, humanReadable);
        }
    }
}

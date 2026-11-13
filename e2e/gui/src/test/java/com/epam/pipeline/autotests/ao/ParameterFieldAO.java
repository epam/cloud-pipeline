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

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners.confine;
import static java.lang.String.format;

/**
 * A parameter row of the launch and configuration forms
 * (client/src/components/pipelines/launch/form/parameters/parameter/index.js). The row has the classes
 * {@code launch-form-parameter-<name>} and {@code launch-form-parameter-key-parameter_<index>}; the form gives
 * a parameter the lowest key index it has free, starting from 0 (form/utilities/parameter-utilities.js,
 * generateKey). The name is a text, and an input only while it is edited (parameter/name-input/index.jsx);
 * the value is the input of the row's form item.
 */
public class ParameterFieldAO extends By implements AccessObject<ParameterFieldAO> {
    /**
     * The parameter that never appears to present.
     */
    public static final ParameterFieldAO nonExistentParameter =
            parameter("NONEXISTENT PARAMETER NAME", "NONEXISTENT PARAMETER VALUE");

    /**
     * The name text of a parameter row: parameter-name-input.css is a lower-case file name, so its CSS module
     * classes lose their first letter.
     */
    static final String NAME_CLASS = "arameter-name-input__parameter-name";
    static final String NAME_INPUT_CLASS = "arameter-name-input__parameter-name-input";
    private static final String KEY_CLASS_PREFIX = "launch-form-parameter-key-";
    private static final Pattern KEY_INDEX = Pattern.compile(KEY_CLASS_PREFIX + "parameter_(\\d+)");

    public final By qualifier;
    public final By nameInput;
    public final By valueInput;
    public final By removeButton;

    protected ParameterFieldAO(final By qualifier) {
        this(
                qualifier,
                confine(byXpath(format(".//input[%s]", hasClass(NAME_INPUT_CLASS))), qualifier),
                confine(byXpath(format(".//*[%s]//input", hasClass("ant-form-item-control"))), qualifier),
                byClassName("dynamic-delete-button")
        );
    }

    protected ParameterFieldAO(final By qualifier, final By nameInput, final By valueInput, final By removeButton) {
        this.qualifier = qualifier;
        this.nameInput = nameInput;
        this.valueInput = valueInput;
        this.removeButton = removeButton;
    }

    /**
     * Streams all visible parameters.
     */
    public static Stream<ParameterFieldAO> parameters() {
        return parameterIndexes()
                .stream()
                .map(ParameterFieldAO::parameterByIndex);
    }

    public static ParameterFieldAO parameter(final String name, final String value) {
        return new ParameterFieldAO(parameterRow(format("%s and .//input[@value = '%s']", nameClass(name), value)));
    }

    /**
     * @param index The numerical part of the key class {@code launch-form-parameter-key-parameter_<index>}
     *              of the parameter row you search.
     */
    public static ParameterFieldAO parameterByIndex(final int index) {
        return new ParameterFieldAO(parameterRow(hasClass(format("%sparameter_%d", KEY_CLASS_PREFIX, index))));
    }

    /**
     * @param number The order number of the parameter field you search.
     */
    public static ParameterFieldAO parameterByOrder(final int number) {
        return parameters()
                .skip(number - 1)
                .findFirst()
                .orElse(nonExistentParameter);
    }

    /**
     * @param name Value of the parameter's name.
     */
    public static ParameterFieldAO parameterByName(final String name) {
        return new ParameterFieldAO(parameterRow(nameClass(name)));
    }

    /**
     * @param value Value of the parameter.
     */
    public static ParameterFieldAO parameterByValue(final String value) {
        return new ParameterFieldAO(parameterRow(format(
                "contains(@class, '%s') and .//*[%s]//input[@value = '%s']",
                KEY_CLASS_PREFIX, hasClass("ant-form-item-control"), value
        )));
    }

    public int index() {
        return keyIndex(context().getAttribute("class"));
    }

    @Override
    public String toString() {
        return this.qualifier.toString();
    }

    @Override
    public SelenideElement context() {
        return $(qualifier);
    }

    @Override
    public ParameterFieldAO setValue(final By qualifier, final String value) {
        final boolean nameOrValue = qualifier == nameInput || qualifier == valueInput;
        return AccessObject.super
                .setValue(qualifier, value)
                .performIf(nameOrValue, AccessObject::resetMouse);
    }

    @Override
    public List<WebElement> findElements(final SearchContext context) {
        return context.findElements(qualifier);
    }

    /**
     * All the parameter rows of the page.
     */
    static By parameterRows() {
        return byXpath(format(".//*[contains(@class, '%s')]", KEY_CLASS_PREFIX));
    }

    /**
     * The key indexes of all the parameter rows of the page, in the order of the rows.
     */
    static List<Integer> parameterIndexes() {
        return $$(parameterRows())
                .stream()
                .map(row -> row.getAttribute("class"))
                .map(ParameterFieldAO::keyIndex)
                .collect(Collectors.toList());
    }

    private static int keyIndex(final String classes) {
        final Matcher key = KEY_INDEX.matcher(classes);
        if (!key.find()) {
            throw new IllegalStateException(format("The parameter row with classes '%s' has no key class", classes));
        }
        return Integer.parseInt(key.group(1));
    }

    /**
     * The row matches also itself, so that a parameter row can be checked for in the context of the same row,
     * e.g. {@code parameter.ensure(parameterByName(name), exist)} on a {@link RunParameterAO}.
     */
    private static By parameterRow(final String condition) {
        return byXpath(format("./descendant-or-self::*[%s]", condition));
    }

    private static String nameClass(final String name) {
        return hasClass("launch-form-parameter-" + name);
    }

    private static String hasClass(final String className) {
        return format("contains(concat(' ', normalize-space(@class), ' '), ' %s ')", className);
    }
}

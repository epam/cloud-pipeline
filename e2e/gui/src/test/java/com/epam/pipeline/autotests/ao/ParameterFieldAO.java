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
import java.util.stream.Stream;

import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners.confine;

public class ParameterFieldAO extends By implements AccessObject<ParameterFieldAO> {
    /**
     * The parameter that never appears to present.
     */
    public static final ParameterFieldAO nonExistentParameter =
            parameter("NONEXISTENT PARAMETER NAME", "NONEXISTENT PARAMETER VALUE");

    public final By qualifier;
    public final By nameInput;
    public final By valueInput;
    public final By removeButton;

    protected ParameterFieldAO(final By qualifier) {
        this(
                qualifier,
                confine(inputByIdSuffix("name"), qualifier),
                confine(inputByIdSuffix("value"), qualifier),
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
        final String prefix = "param_";
        final By parameterField = inputByIdSuffix("key");
        return $$(parameterField)
                .stream()
                .map(SelenideElement::val)
                .map(key -> key.replace(prefix, ""))
                .map(Integer::valueOf)
                .map(ParameterFieldAO::parameterByIndex);
    }

    public static ParameterFieldAO parameter(final String name, final String value) {
        final String controlClass = "ant-form-item-control";
        final String nameAsAChild = String.format(".//input[@placeholder = 'Name' and @value = '%s']", name);
        final String valueAsAChild = String.format(".//input[@value = '%s']", value);
        final By parameterField = byXpath(String.format(
                ".//*[contains(concat(' ', @class, ' '), ' %s ') and %s and %s]",
                controlClass, nameAsAChild, valueAsAChild
        ));
        return new ParameterFieldAO(parameterField);
    }

    /**
     * @param index The numerical part of {@code id} attribute of the field you search.
     */
    public static ParameterFieldAO parameterByIndex(final int index) {
        final String controlClass = "ant-form-item-control";
        final String templateId = parameterId("key", index);
        final By parameterField = byXpath(String.format(
                ".//*[contains(concat(' ', @class, ' '), ' %s ') and .//@id = '%s']",
                controlClass, templateId
        ));
        return new ParameterFieldAO(parameterField);
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
        final String controlClass = "ant-form-item-control";
        final By parameterField = byXpath(String.format(
                ".//*[contains(concat(' ', @class, ' '), ' %s ') and .//input[@placeholder = 'Name' and @value = '%s']]",
                controlClass, name
        ));
        return new ParameterFieldAO(parameterField);
    }

    /**
     * @param value Value of the parameter.
     */
    public static ParameterFieldAO parameterByValue(final String value) {
        final String controlClass = "ant-form-item-control";
        final By parameterField = byXpath(String.format(
                ".//*[contains(concat(' ', @class, ' '), ' %s ') and .//input[@value = '%s']]",
                controlClass, value
        ));
        return new ParameterFieldAO(parameterField);
    }

    public int index() {
        final String key = context().find(inputByIdSuffix("key")).val();
        final String index = key.replace("param_", "");
        return Integer.valueOf(index);
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
     * Produces parameter input id by its {@code index} and {@code entry}.
     *
     * @param entry Entry is all the part after the last dot in the id attribute of an input.
     * @param index Index is a digit part of
     */
    private static String parameterId(final String entry, final int index) {
        final String idTemplate = "parameters.params.param_%d.%s";
        return String.format(idTemplate, index, entry);
    }

    /**
     * This XPath is a simple ends-with function, but the original function is not supported by chrome.
     *
     * @param suffix The last part of an id.
     * @return Qualifier of input which id ends with given {@code suffix}.
     */
    private static By inputByIdSuffix(final String suffix) {
        final String inputThatHasIdWithSuffix = String.format(
                ".//input[substring(@id, string-length(@id) - string-length('%s') + 1) = '%s']",
                suffix, suffix
        );
        return byXpath(inputThatHasIdWithSuffix);
    }
}

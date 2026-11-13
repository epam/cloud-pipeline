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
import com.codeborne.selenide.SelenideElement;

import java.util.Map;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;

public class DetachedConfigurationParameterAO implements AccessObject<DetachedConfigurationParameterAO>{

    private final Map<Primitive, SelenideElement> elements;
    private final Configuration configuration;
    private final SelenideElement nameText;

    /**
     * @param parameterIndex The key index of the parameter row, see {@link ParameterFieldAO#parameterByIndex(int)}.
     */
    public DetachedConfigurationParameterAO(Configuration configuration, int parameterIndex) {
        this.configuration = configuration;
        final ParameterFieldAO parameter = ParameterFieldAO.parameterByIndex(parameterIndex);
        this.nameText = $(parameter.qualifier).find(byClassName(ParameterFieldAO.NAME_CLASS));

        // A detached configuration shows a value in a combobox that offers the metadata fields
        // (launch/form/parameters/parameter/inputs/metadata-auto-complete.jsx)
        this.elements = initialiseElements(
                entry(PARAMETER_NAME, $(parameter.nameInput)),
                entry(REMOVE_PARAMETER, $(parameter.qualifier).find(parameter.removeButton)),
                entry(PARAMETER_VALUE, $(parameter.valueInput))
        );
    }

    /**
     * The name of a parameter is a text, and the name input after a click on it. In a configuration with
     * a pipeline the names of the parameters can't be edited: they are a text only
     * (launch/form/parameters/parameter/name-input/index.jsx).
     */
    public DetachedConfigurationParameterAO setName(String name) {
        if (nameText.exists()) {
            nameText.click();
        }
        setValue(PARAMETER_NAME, name).resetMouse();
        return this;
    }

    public DetachedConfigurationParameterAO setValue(String value) {
        setValue(PARAMETER_VALUE, value).resetMouse();
        return this;
    }

    public DetachedConfigurationParameterAO typeValue(String value) {
        setValue(PARAMETER_VALUE, value);
        return this;
    }

    public DetachedConfigurationParameterAO addToValue(String value) {
        addToValue(PARAMETER_VALUE, value);
        return this;
    }

    public DetachedConfigurationParameterAO validateParameter(String name, String value) {
        if (get(PARAMETER_NAME).exists()) {
            ensure(PARAMETER_NAME, Condition.have(value(name)));
        } else {
            ensure(nameText, Condition.have(exactText(name)));
        }
        ensure(PARAMETER_VALUE, Condition.have(value(value)));
        return this;
    }

    /**
     * Checks whether the name of the parameter can be edited: its name text is marked so.
     */
    public DetachedConfigurationParameterAO parameterNameIsEnable(boolean isEnable) {
        ensure(nameText, cssClass(isEnable ? "arameter-name-input__editing-enabled"
                : "arameter-name-input__editing-disabled"));
        return this;
    }

    public DetachedConfigurationParameterAO deleteParameter() {
        ensure(REMOVE_PARAMETER, visible);
        click(REMOVE_PARAMETER);
        return this;
    }

    public Configuration close() {
        return configuration;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}

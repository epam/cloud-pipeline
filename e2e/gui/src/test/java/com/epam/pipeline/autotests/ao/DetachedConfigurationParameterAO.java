/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import static com.codeborne.selenide.Selectors.byClassName;
import com.codeborne.selenide.SelenideElement;

import java.util.Map;

import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.lang.String.format;

public class DetachedConfigurationParameterAO implements AccessObject<DetachedConfigurationParameterAO>{

    private final Map<Primitive, SelenideElement> elements;
    private final Configuration configuration;

    public DetachedConfigurationParameterAO(Configuration configuration, int parameterIndex) {
        final SelenideElement parameter = $(byId("launch-pipeline-parameters-panel"))
                .$(byClassName(format("launch-form-parameter-key-parameter_%s", parameterIndex)));
        this.configuration = configuration;

        this.elements = initialiseElements(
                entry(PARAMETER_FIELD, parameter.$(byClassName("arameter-name-input__parameter-name"))),
                entry(PARAMETER_NAME, parameter.$(byClassName("arameter-name-input__parameter-name-input"))),
                entry(PARAMETER_VALUE, parameter.$(byClassName("ant-form-item-control")).$x(".//input")),
                entry(REMOVE_PARAMETER, parameter.$(byClassName("dynamic-delete-button")))
        );
    }

    public DetachedConfigurationParameterAO setName(String name) {
        if (get(PARAMETER_FIELD).exists()) {
            get(PARAMETER_FIELD).click();
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
        ensure(PARAMETER_NAME, Condition.have(value(name)));
        ensure(PARAMETER_VALUE, Condition.have(value(value)));
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

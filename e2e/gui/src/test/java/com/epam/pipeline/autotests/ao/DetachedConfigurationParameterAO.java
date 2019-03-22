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

import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;

public class DetachedConfigurationParameterAO implements AccessObject<DetachedConfigurationParameterAO>{

    private final Map<Primitive, SelenideElement> elements;
    private final Configuration configuration;

    public DetachedConfigurationParameterAO(Configuration configuration, int parameterIndex) {
        this.configuration = configuration;

        this.elements = initialiseElements(
                entry(PARAMETER_NAME, $(byId(String.format("parameters.params.param_%d.name", parameterIndex)))),
                entry(REMOVE_PARAMETER, $(byId(String.format("parameters.params.param_%d.name", parameterIndex)))
                        .closest(".launch-pipeline-form__form-item-row").closest(".launch-pipeline-form__form-item-row")
                        .find(byId("remove-parameter-button"))),
                entry(PARAMETER_VALUE, $(byId(String.format("parameters.params.param_%d.name", parameterIndex)))
                        .closest(".launch-pipeline-form__form-item-row").closest(".launch-pipeline-form__form-item-row")
                        .find(byAttribute("role", "combobox")).find("input"))
        );
    }

    public DetachedConfigurationParameterAO setName(String name) {
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

    public Configuration close() {
        return configuration;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}

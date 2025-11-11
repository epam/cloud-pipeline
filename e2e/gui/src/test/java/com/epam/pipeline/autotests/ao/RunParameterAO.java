/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import java.util.Map;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.lang.String.format;

public class RunParameterAO
        extends ParameterFieldAO
        implements AccessObject<ParameterFieldAO> {

    private final Map<Primitive, SelenideElement> elements;
    private final PipelineRunFormAO pipelineRunFormAO;

    public RunParameterAO(PipelineRunFormAO pipelineRunFormAO, int parameterIndex) {
        super(parameterByIndex(parameterIndex));
        final SelenideElement parameter = $(byClassName(format("launch-form-parameter-key-parameter_%s", parameterIndex)));
        this.pipelineRunFormAO = pipelineRunFormAO;

        this.elements = initialiseElements(
                entry(PARAMETER_FIELD, parameter.$(byClassName("arameter-name-input__parameter-name"))),
                entry(PARAMETER_NAME, parameter.$(byClassName("arameter-name-input__parameter-name-input"))),
                entry(PARAMETER_VALUE, parameter.$(byClassName("ant-form-item-control")).$x(".//input")),
                entry(PARAMETER_PATH, parameter.$(byClassName("aunch-form-parameter-input__launch-parameter-path-input-addon"))),
                entry(REMOVE_PARAMETER, parameter.$(byClassName("dynamic-delete-button"))),
                entry(PARAMETER_ENABLED, parameter.find(byClassName("ant-checkbox")))
        );
    }

    public RunParameterAO setName(String name) {
        if (get(PARAMETER_FIELD).exists()) {
            get(PARAMETER_FIELD).click();
        }
        return (RunParameterAO) setValue(get(PARAMETER_NAME), name);
    }

    public RunParameterAO setValue(String value) {
        return (RunParameterAO) setValue(this.valueInput, value);
    }

    public PathAdditionDialogAO openPathAdditionDialog() {
        click(PARAMETER_PATH);
        return new PathAdditionDialogAO(this);
    }

    public RunParameterAO setEnableParameter(boolean isEnabled) {
        if((!isEnabled && get(PARAMETER_ENABLED).has(cssClass("ant-checkbox-checked"))) ||
                    (isEnabled && !get(PARAMETER_ENABLED).has(cssClass("ant-checkbox-checked")))) {
            click(PARAMETER_ENABLED);
        }
        return this;
    }

    public PipelineRunFormAO close() {
        return pipelineRunFormAO;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public PipelineRunFormAO remove() {
        click(REMOVE_PARAMETER);
        return pipelineRunFormAO;
    }
}

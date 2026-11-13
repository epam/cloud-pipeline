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

import java.util.Map;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;

public class RunParameterAO
        extends ParameterFieldAO
        implements AccessObject<ParameterFieldAO> {

    private final Map<Primitive, SelenideElement> elements;
    private final PipelineRunFormAO pipelineRunFormAO;
    private final SelenideElement nameText;

    /**
     * @param parameterIndex The key index of the parameter row, see {@link ParameterFieldAO#parameterByIndex(int)}.
     */
    public RunParameterAO(PipelineRunFormAO pipelineRunFormAO, int parameterIndex) {
        super(parameterByIndex(parameterIndex));
        this.pipelineRunFormAO = pipelineRunFormAO;
        final SelenideElement parameter = $(qualifier);
        this.nameText = parameter.find(byClassName(NAME_CLASS));

        this.elements = initialiseElements(
                entry(PARAMETER_NAME, $(nameInput)),
                // the addon of the path input, launch/form/parameters/parameter/inputs/path-parameter-input.jsx
                entry(PARAMETER_PATH, parameter.find(
                        byClassName("aunch-form-parameter-input__launch-parameter-path-input-addon"))),
                entry(REMOVE_PARAMETER, parameter.find(byClassName("dynamic-delete-button"))),
                entry(PARAMETER_VALUE, $(valueInput))
        );
    }

    /**
     * A configuration form shows the name of a parameter as a text, and the name input after a click on it.
     */
    public RunParameterAO setName(String name) {
        if (nameText.exists()) {
            nameText.click();
        }
        return (RunParameterAO) setValue(this.nameInput, name);
    }

    public RunParameterAO setValue(String value) {
        return (RunParameterAO) setValue(this.valueInput, value);
    }

    public PathAdditionDialogAO openPathAdditionDialog() {
        click(PARAMETER_PATH);
        return new PathAdditionDialogAO(this);
    }

    /**
     * The value of a boolean parameter is the "Enabled" check box, and an added boolean parameter has it unchecked
     * (launch/form/parameters/parameter/inputs/bool-parameter-input.jsx).
     */
    public RunParameterAO setEnableParameter(boolean isEnabled) {
        final SelenideElement checkbox = $(qualifier).find(byClassName("ant-checkbox"));
        if (checkbox.shouldBe(visible).has(cssClass("ant-checkbox-checked")) != isEnabled) {
            checkbox.click();
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

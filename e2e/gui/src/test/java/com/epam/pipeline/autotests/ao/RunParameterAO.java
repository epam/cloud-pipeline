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

import com.codeborne.selenide.SelenideElement;

import java.util.Map;

import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
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
        final SelenideElement parameter = $(byId("launch-pipeline-parameters-panel"))
                .$(byClassName(format("param_%d", parameterIndex)));
        this.pipelineRunFormAO = pipelineRunFormAO;

        this.elements = initialiseElements(
                entry(PARAMETER_FIELD, parameter.$(byClassName("cp-text-not-important"))),
                entry(PARAMETER_NAME, $(byId(format(idTemplate, parameterIndex, "name")))),
                entry(PARAMETER_VALUE, $(byId(format(idTemplate, parameterIndex, "value")))),
                entry(PARAMETER_PATH, parameter.$(byClassName("launch-pipeline-form__path-type"))),
                entry(REMOVE_PARAMETER, parameter.$(byId("remove-parameter-button")))
        );
    }

    public RunParameterAO setName(String name) {
        get(PARAMETER_FIELD).click();
        return (RunParameterAO) setValue(get(PARAMETER_NAME), name);
    }

    public RunParameterAO setValue(String value) {
        return (RunParameterAO) setValue(this.valueInput, value);
    }

    public PathAdditionDialogAO openPathAdditionDialog() {
        click(PARAMETER_PATH);
        return new PathAdditionDialogAO(this);
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

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

package com.epam.pipeline.entity.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Common model step parameters.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RModelStepParameters.class, name = ModelStepType.Name.MODEL),
        @JsonSubTypes.Type(value = ProtocolStepParameters.class, name = ModelStepType.Name.PROTOCOL)})
public abstract class AbstractStepParameters {

    /**
     * Step type.
     */
    private ModelStepType type;

    /**
     * Step main script.
     */
    private String script;

    /**
     * List of the step input parameters.
     */
    private List<InputParameter> inputs;

    /**
     * List of the step csv outputs.
     */
    private List<ConfValue> outputs;
}

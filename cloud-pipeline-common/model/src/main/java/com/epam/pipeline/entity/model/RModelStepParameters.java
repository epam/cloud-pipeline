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

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Original model parameters.
 *
 * Corresponding model step type is {@link ModelStepType#MODEL}.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RModelStepParameters extends AbstractStepParameters {

    /**
     * Name of the column that will contain the output.
     *
     * The column is used as a result observations filter criteria where keys specified
     * in {@link AbstractStepParameters#outputs} are used as valid ones and all other are ignored.
     */
    private String outputColumn;

    /**
     * Values that will be collected for all filtered result observations.
     */
    private List<ConfValue> values;
}

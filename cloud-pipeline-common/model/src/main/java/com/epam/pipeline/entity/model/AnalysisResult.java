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

import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Model analysis result.
 */
@Value
public class AnalysisResult {

    /**
     * A set of named in-memory CSV files.
     *
     * Map keys are the same that were specified in {@link AbstractStepParameters#outputs}.
     *
     * For example, it can represent the following files content:
     *
     * data_1.csv
     * column1,column2
     * value11,value12
     *
     * data_2.csv
     * column1,column2,column3
     * value11,value12,value13
     * value21,value22,value23
     * value31,value32,value33
     *
     * As a single map:
     *
     * {
     *     "VAL1": [Row1, Row2],
     *     "VAL2": [Row1, Row2, Row3]
     * }
     */
    private final Map<String, List<Row>> outputs;

    public static AnalysisResult empty() {
        return new AnalysisResult(Collections.emptyMap());
    }
}

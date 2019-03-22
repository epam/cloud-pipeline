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

package com.epam.pipeline.entity.pipeline.run.parameter;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PipelineRunParameter {
    private String name;
    /**
     * Represents original parameter value without environment variable substitution
     */
    private String value;

    private String type;
    /**
     * Represents parameter value with resolved environment variables, ${VAR} ans $VAR
     * syntax is supported
     */
    private String resolvedValue;
    private List<DataStorageLink> dataStorageLinks;

    public PipelineRunParameter(String parameter) {
        this(parameter, null);
    }

    public PipelineRunParameter(String name, String value) {
        this(name, value, null);
    }

    public PipelineRunParameter(String name, String value, String type) {
        this.name = name;
        this.value = value;
        this.type = type;
    }

    @Override public String toString() {
        if (value != null) {
            return "PipelineRunParameter{" + "name='" + name + '\'' + ", value='" + value + '\'' + '}';
        } else {
            return "PipelineRunParameter{" + "name='" + name + "\'}";
        }
    }
}

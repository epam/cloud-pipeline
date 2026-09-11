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

package com.epam.pipeline.vo;

import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class ContextualPreferenceVO {
    String name;
    String value;
    PreferenceType type;
    ContextualPreferenceExternalResource resource;

    @JsonCreator
    public ContextualPreferenceVO(
            @JsonProperty("name") final String name,
            @JsonProperty("value") final String value,
            @JsonProperty("type") final PreferenceType type,
            @JsonProperty("resource") final ContextualPreferenceExternalResource resource) {
        this.name = name;
        this.value = value;
        this.type = type;
        this.resource = resource;
    }
}

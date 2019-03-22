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
package com.epam.pipeline.autotests.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Parameter {
    public String type;
    public String value;
    public Boolean required;

    public Parameter(final String type, final String value, final Boolean required) {
        this.type = type;
        this.value = value;
        this.required = required;
    }

    public static Parameter optional(final String type, final String value) {
        return new Parameter(type, value, false);
    }

    public static Parameter required(final String type, final String value) {
        return new Parameter(type, value, true);
    }
}

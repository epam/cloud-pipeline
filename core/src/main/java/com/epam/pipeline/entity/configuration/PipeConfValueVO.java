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

package com.epam.pipeline.entity.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Setter
@Getter
public class PipeConfValueVO {

    public static final String DEFAULT_TYPE = "string";
    public static final String DEFAULT_VALUE = "";
    public static final boolean DEFAULT_REQUIRED = false;
    protected static final  List<String> DEFAULT_AVAIL_VALUES = new ArrayList<>();

    @JsonProperty(value = "value")
    private String value;

    @JsonProperty(value = "type")
    private String type;

    @JsonProperty(value = "required")
    private boolean required;

    @JsonProperty(value = "no_override")
    private Boolean noOverride;

    @JsonProperty(value = "enum")
    private List<Object> availableValues;

    private List<Map<String, String>> validation;

    /**
     * String expression to determine visibility of a param
     * User for GUI client only
     */
    private String visible;

    private String description;

    PipeConfValueVO() {
        this(DEFAULT_VALUE, DEFAULT_TYPE, DEFAULT_REQUIRED, DEFAULT_AVAIL_VALUES);
    }

    public PipeConfValueVO(String value) {
        this(value, DEFAULT_TYPE, DEFAULT_REQUIRED, DEFAULT_AVAIL_VALUES);
    }

    public PipeConfValueVO(String value, String type) {
        this(value, type, DEFAULT_REQUIRED, DEFAULT_AVAIL_VALUES);
    }

    public PipeConfValueVO(String value, String type, boolean required) {
        this(value, type, required, DEFAULT_AVAIL_VALUES);
    }

    public PipeConfValueVO(String value, String type, boolean required, List<String> availableValues) {
        this.value = value;
        this.type = type;
        this.required = required;
        this.availableValues = Collections.unmodifiableList(availableValues);
        this.validation = new ArrayList<>();
    }

}

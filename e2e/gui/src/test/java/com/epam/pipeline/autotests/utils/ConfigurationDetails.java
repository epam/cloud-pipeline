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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationDetails {
    @JsonProperty("main_class")
    public String mainClass;
    @JsonProperty("main_file")
    public String mainFile;
    @JsonProperty("instance_size")
    public String instanceSize;
    @JsonProperty("instance_disk")
    public String instanceDisk;
    @JsonProperty("docker_image")
    public String dockerImage;
    @JsonProperty("cmd_template")
    public String cmdTemplate;
    public Map<String, Parameter> parameters;
    public String timeout;
    @JsonProperty("is_spot")
    public Boolean spot;
}

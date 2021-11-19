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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

/**
 * Represents a set of settings for a {@link com.epam.pipeline.entity.pipeline.Pipeline}, usually is
 * stored in config.json file in pipeline's repository.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ConfigurationEntry {

    public static final String DEFAULT = "default";

    private String name = DEFAULT;
    private String description;
    @JsonProperty(value = DEFAULT)
    private Boolean defaultConfiguration = false;

    private PipelineConfiguration configuration;

    public ConfigurationEntry(PipelineConfiguration configuration) {
        this.configuration = configuration;
        this.defaultConfiguration = true;
    }

    public boolean checkConfigComplete() {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        if (configuration == null || !StringUtils.hasText(configuration.getCmdTemplate())) {
            return false;
        }
        return true;
    }
}

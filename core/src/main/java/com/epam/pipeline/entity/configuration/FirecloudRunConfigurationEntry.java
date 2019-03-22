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

import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration entry for {@link ExecutionEnvironment#FIRECLOUD}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FirecloudRunConfigurationEntry extends AbstractRunConfigurationEntry {
    public static final String PARAMETERS_PROPERTY = "parameters";

    private ExecutionEnvironment executionEnvironment = ExecutionEnvironment.FIRECLOUD;
    private String methodName;
    private String methodSnapshot;
    private String methodConfigurationName;
    private String methodConfigurationSnapshot;
    private List<InputsOutputs> methodInputs;
    private List<InputsOutputs> methodOutputs;

    //TODO: added only for backward compatibility with UI Client, remove later
    private Configuration configuration;

    @Override
    public boolean checkConfigComplete() {
        return StringUtils.hasText(methodName) &&
                StringUtils.hasText(methodSnapshot) &&
                (StringUtils.hasText(methodConfigurationName) ||
                        !CollectionUtils.isEmpty(methodInputs) ||
                        !CollectionUtils.isEmpty(methodOutputs));
    }

    @Override
    public PipelineStart toPipelineStart() {
        return null;
    }

    @Override
    public Integer getWorkerCount() {
        return null;
    }

    @JsonIgnore
    public Map<String, PipeConfValueVO> getParameters() {
        return configuration == null ? Collections.emptyMap() : configuration.getParameters();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Configuration {
        @JsonProperty(value = PARAMETERS_PROPERTY)
        @JsonDeserialize(using = PipelineConfValuesMapDeserializer.class)
        private Map<String, PipeConfValueVO> parameters = new LinkedHashMap<>();
    }

}

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
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents set of settings for one of supported execution environments {@link ExecutionEnvironment}
 */
@Data
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "executionEnvironment",
        defaultImpl = RunConfigurationEntry.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FirecloudRunConfigurationEntry.class, name = "FIRECLOUD"),
        @JsonSubTypes.Type(value = RunConfigurationEntry.class, name = "CLOUD_PLATFORM"),
        @JsonSubTypes.Type(value = DtsRunConfigurationEntry.class, name = "DTS")})
public abstract class AbstractRunConfigurationEntry {
    private ExecutionEnvironment executionEnvironment;
    private String name;
    private Long rootEntityId;
    private List<RunSid> runSids;
    private String configName;
    @JsonProperty(value = "default")
    private boolean defaultConfiguration = false;

    public abstract boolean checkConfigComplete();
    public abstract PipelineStart toPipelineStart();

    /**
     * Returns number of additional workers for running configuration
     * @return
     */
    @JsonIgnore
    public abstract Integer getWorkerCount();
}

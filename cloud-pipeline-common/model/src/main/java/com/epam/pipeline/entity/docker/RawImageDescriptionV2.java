/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.docker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown=true)
public class RawImageDescriptionV2 {
    private Long registry;

    private String image;

    private String tag;

    @JsonProperty("architecture")
    private String architecture;
    @JsonProperty("os")
    private String os;
    @JsonProperty("docker_version")
    private String dockerVersion;

    @JsonProperty("created")
    private String created;

    @JsonProperty("container")
    private String container;

    @JsonProperty("container_config")
    private Config containerConfig;

    @JsonProperty("config")
    private Config config;

    @JsonProperty("history")
    private List<HistoryEntryV2> history;

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown=true)
    public static class Config {
        @JsonProperty("Labels")
        private Map<String, String> labels;
    }

    public ImageDescription getImageDescription() {
        return new ImageDescription(this);
    }
}

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

package com.epam.pipeline.entity.docker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * A Value Object, representing Docker Registry Manifest of schema V2.
 * See https://docs.docker.com/registry/spec/manifest-v2-2/ for reference.
 */
@Getter
@Setter
@NoArgsConstructor
public class ManifestV2 {
    private Integer schemaVersion;
    private String mediaType;
    private Config config;
    /**
     * Contains layers, sorted from the base image
     */
    private List<Config> layers;

    /**
     * The {@code digest} field comes from the docker-content-digest header and represents manifest's identifier.
     */
    private String digest;

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Config {
        private String digest;
        private Long size;
    }
}

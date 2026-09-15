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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * A Value Object, representing Docker Registry Manifest of schema V2.
 * See https://docs.docker.com/registry/spec/manifest-v2-2/ for reference.
 *
 * The same object represents a manifest list (a multi platform image) and an OCI image index: such a manifest
 * has no {@code config} and no {@code layers}, but references platform specific image manifests
 * in the {@code manifests} field.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ManifestV2 {
    private Integer schemaVersion;
    private String mediaType;
    private Config config;
    /**
     * Contains layers, sorted from the base image
     */
    private List<Config> layers;
    /**
     * Contains image manifests, referenced by a manifest list (an index), and is empty for an image manifest
     */
    private List<ManifestReference> manifests;

    /**
     * The {@code digest} field comes from the docker-content-digest header and represents manifest's identifier.
     */
    private String digest;

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Config {
        private String digest;
        private Long size;
    }

    /**
     * A reference to an image manifest of a specific platform from a manifest list (an index).
     */
    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ManifestReference {
        private String digest;
        private Long size;
        private String mediaType;
        private Platform platform;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Platform {
        private String architecture;
        private String os;
    }
}

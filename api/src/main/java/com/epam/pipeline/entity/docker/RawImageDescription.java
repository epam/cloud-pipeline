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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown=true)
public class RawImageDescription {
    private Long registry;

    @JsonProperty("name")
    private String image;

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("history")
    private List<HistoryEntry> history;

    /**
     * Contains image's layers in the following format:
     * [
     *   {
     *     "blobSum": "sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"
     *   },
     *   ...
     * ]
     */
    @JsonProperty("fsLayers")
    private List<Map<String, String>> fsLayers;
    @JsonProperty("signature")
    private Object signature;

    public ImageDescription getImageDescription() {
        return new ImageDescription(this);
    }

}

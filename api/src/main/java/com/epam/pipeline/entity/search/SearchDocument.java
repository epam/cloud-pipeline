/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.search;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SearchDocument {

    private String elasticId;
    private String id;
    private String parentId;
    private String name;
    private String description;
    private SearchDocumentType type;
    private List<HightLight> highlights;
    private float score;
    private String owner;
    private Map<String, String> attributes;

    @JsonAnyGetter
    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class HightLight {

        private String fieldName;
        private List<String> matches;
    }
}

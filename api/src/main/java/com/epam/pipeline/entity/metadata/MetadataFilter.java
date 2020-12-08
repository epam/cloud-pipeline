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

package com.epam.pipeline.entity.metadata;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class MetadataFilter {

    @ApiModelProperty(notes = "ID of a folder to search for metadata", required = true)
    private Long folderId;
    @JsonIgnore
    private boolean recursive = false;
    @ApiModelProperty(notes = "name of a metadata class for search", required = true)
    private String metadataClass;
    @ApiModelProperty(notes = "index of page result starting from 1", required = true)
    private Integer page;
    @ApiModelProperty(notes = "size of page result, must be greater than 0", required = true)
    private Integer pageSize;
    @ApiModelProperty(notes = "list of strings to perform substring case "
            + "insensitive search in metadata attributes")
    private List<String> searchQueries;
    @ApiModelProperty(notes = "list of key-values pairs for exact match, "
            + "key may be an arbitrary string or one of predefined "
            + "available field names: ENTITY_ID, ENTITY_NAME, EXTERNAL_ID, PARENT_ID")
    private List<FilterQuery> filters;
    @ApiModelProperty(notes = "list of fields to perform sorting by, with optional descending order; "
            + "any key string and predefined fields are supported: "
            + "ENTITY_ID, ENTITY_NAME, EXTERNAL_ID, PARENT_ID<br/>")
    private List<OrderBy> orderBy;
    @ApiModelProperty(notes = "list string to perform substring insensitive search in external ids")
    private List<String> externalIdQueries;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterQuery {
        private String key;
        private String value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBy {
        private String field;
        private boolean desc = false;
    }
}

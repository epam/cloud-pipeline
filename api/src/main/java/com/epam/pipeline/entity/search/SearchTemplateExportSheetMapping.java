/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class SearchTemplateExportSheetMapping {
    private String column;
    /**
     * 1-based row start
     */
    @JsonProperty("start_row")
    private Integer startRow;
    /**
     * String with placeholders
     */
    private String value;
    private boolean unique;
    /**
     * Some items may not contain values for requested placeholders.
     * If so and this flag enabled placeholders shall not be resolved.
     * CASE: keep_unresolved=true
     * {Placeholder1}/{Placeholder2} -> ResolvedValue/{Placeholder2}
     * CASE: keep_unresolved=false
     * {Placeholder1}/{Placeholder2} -> ResolvedValue/
     */
    @JsonProperty("keep_unresolved")
    private boolean keepUnresolved;
}

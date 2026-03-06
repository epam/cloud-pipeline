/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.pipeline.run;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Objects;

@Value
@Builder
@Jacksonized
@EqualsAndHashCode
public class PipelineRunResult {

    Long runId;
    String name;
    String fileMask;
    List<String> items;

    @JsonIgnore
    public void addItem(final String path) {
        items.add(path);
    }

    @JsonIgnore
    public void validate() {
        Validate.isTrue(Objects.nonNull(this.getRunId()), "Run ID should be provided for run result object!");
        Validate.isTrue(this.getRunId() > 0, "Run ID should be > 0 for run result object!");
        Validate.isTrue(StringUtils.isNotBlank(this.getName()), "Name should be provided!");
        Validate.isTrue(StringUtils.isNotBlank(this.getFileMask()), "File mask should be provided!");
        Validate.isTrue(CollectionUtils.isNotEmpty(this.getItems()),
                "Items should be provided for PipelineRunResult object!");
        this.getItems().forEach(
            path -> Validate.isTrue(StringUtils.isNotBlank(path), "Item path should not be empty!")
        );
    }

}

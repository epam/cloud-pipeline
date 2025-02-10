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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;

@Value
@Builder
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
        Assert.notNull(this.getRunId(), "Run ID should be provided for run result object!");
        Assert.isTrue(this.getRunId() > 0, "Run ID should be > 0 for run result object!");
        Assert.isTrue(StringUtils.hasText(this.getName()), "Name should be provided!");
        Assert.isTrue(StringUtils.hasText(this.getFileMask()), "File mask should be provided!");
        Assert.notEmpty(this.getItems(), "Items should be provided for PipelineRunResult object!");
        this.getItems().forEach(
            path -> Assert.isTrue(StringUtils.hasText(path), "Item path should not be empty!")
        );
    }

}

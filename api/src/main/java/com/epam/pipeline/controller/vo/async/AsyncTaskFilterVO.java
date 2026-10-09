/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.controller.vo.async;

import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Which tasks to list. Every field is optional; an empty filter lists everything the caller may see.
 *
 * <p>{@code owner} is never taken from the request for a regular user: the ACL layer overwrites it
 * with the authenticated login, so a foreign login cannot be substituted. An administrator may leave
 * it empty to see everyone's tasks, or set it to look at one user.</p>
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(example = "{\"type\": \"DOWNLOAD_ARCHIVE\", \"statuses\": [\"PENDING\", \"RUNNING\"], "
        + "\"page\": 1, \"pageSize\": 20}")
public class AsyncTaskFilterVO {

    @Schema(description = "Ignored for a regular user, who always sees only own tasks.")
    private String owner;

    private AsyncTaskType type;

    private List<AsyncTaskStatus> statuses;

    @Schema(type = "string", example = "2026-10-08 06:30:35.000")
    private LocalDateTime createdFrom;

    @Schema(type = "string", example = "2026-10-08 18:30:35.000")
    private LocalDateTime createdTo;

    @Schema(type = "string", example = "2026-10-09 06:30:35.000", description = "Tasks whose status has not changed since then.")
    private LocalDateTime statusChangedBefore;

    @Schema(type = "string", example = "2026-10-02 06:30:35.000", description = "Tasks finished before then.")
    private LocalDateTime finishedBefore;

    @Schema(description = "Counted from 1. Ignored unless pageSize is set.")
    private Integer page;

    @Schema(description = "Omit it, or leave it at zero, to get every matching task without paging.")
    private Integer pageSize;
}

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

package com.epam.pipeline.entity.datastorage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Everything tunable about archive downloads, in one preference. Part of it is read here, part by the
 * executor service through {@code GET /preferences/{key}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DownloadArchiveConfig {

    /**
     * Bucket the archives go to - a plain bucket, not a storage of the platform. Required.
     */
    private String bucket;

    /**
     * Region whose credentials sign the link. Empty means the platform's default region; set it when
     * the bucket lives elsewhere, as a signature is bound to its region.
     */
    private Long regionId;

    /**
     * Largest selection that may be archived. Counted by the executor, so an oversized request fails
     * once the task runs rather than when it is accepted.
     */
    private Long maxSizeBytes;

    /**
     * How long a finished archive is kept, counted from the moment it became available.
     */
    private Integer ttlDays;

    /**
     * How many tasks one user may have in flight. Checked when a task is created.
     */
    private Integer maxUserTasks;

    /**
     * How long a worker may work on one task, that is how long the task may stay in {@code RUNNING}.
     * The orchestrator hands the value over at startup and the worker stops itself once past it.
     */
    private Integer workerMaxRunningMinutes;

    /**
     * How long one status may stay unchanged before the task is taken for stuck and failed. Measured by
     * {@code status_changed}, so {@code RUNNING} and {@code STOPPING} get a window each.
     *
     * <p>Must exceed {@code workerMaxRunningMinutes} by the worker's poll interval, cleanup and report:
     * failing a task does not stop its thread, and a worker failed while still alive deletes the
     * archive it had just made.</p>
     */
    private Integer stuckTasksThresholdMinutes;
}

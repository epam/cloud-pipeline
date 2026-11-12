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

package com.epam.pipeline.entity.async;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Everything tunable about the asynchronous tasks, in one preference. Part of it is read here, part by
 * the executor service through {@code GET /preferences/{key}}.
 *
 * <pre>
 * {
 *   "maxUserTasks": 3,
 *   "stuckTasksThresholdMinutes": 120,
 *   "tasks": {
 *     "storageDownloadAsArchive": { ... },
 *     "storageUnpackArchive":     { ... }
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncTasksConfig {

    public static final int DEFAULT_MAX_USER_TASKS = 3;

    /**
     * Maximum available active tasks for a single user at a time across all task types.
     */
    private Integer maxUserTasks;

    /**
     * How long one status may stay unchanged before the task is taken for stuck and failed. Measured by
     * {@code status_changed}, so {@code RUNNING} and {@code STOPPING} get a window each.
     *
     * <p>One value for the whole service, because it governs the cleaner rather than a task type. It
     * must exceed the <b>larger</b> of the two {@code workerMaxRunningMinutes} by the time a worker
     * needs to notice its own deadline, clean up and report: failing a task does not stop its thread,
     * and a worker failed while still alive undoes the work it has just finished.</p>
     */
    private Integer stuckTasksThresholdMinutes;

    private Tasks tasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tasks {
        private StorageDownloadArchiveTaskConfig storageDownloadAsArchive;
        private StorageUnpackArchiveTaskConfig storageUnpackArchive;
    }

    /**
     * The one place the {@code maxUserTasks} fallback lives, so whoever enforces the quota - the api
     * here, and the executor services reading the same preference - cannot drift apart on what an
     * unset value means.
     */
    public static int maxUserTasksOrDefault(final AsyncTasksConfig config) {
        return Optional.ofNullable(config)
                .map(AsyncTasksConfig::getMaxUserTasks)
                .orElse(DEFAULT_MAX_USER_TASKS);
    }

    /**
     * The {@code storageDownloadAsArchive} section, never null, so a caller reads a default out of an
     * absent section instead of guarding two levels of nesting at every use.
     */
    public static StorageDownloadArchiveTaskConfig downloadOrEmpty(final AsyncTasksConfig config) {
        return Optional.ofNullable(config)
                .map(AsyncTasksConfig::getTasks)
                .map(Tasks::getStorageDownloadAsArchive)
                .orElseGet(() -> StorageDownloadArchiveTaskConfig.builder().build());
    }

    /**
     * The {@code storageUnpackArchive} section, never null, for the same reason.
     */
    public static StorageUnpackArchiveTaskConfig unpackOrEmpty(final AsyncTasksConfig config) {
        return Optional.ofNullable(config)
                .map(AsyncTasksConfig::getTasks)
                .map(Tasks::getStorageUnpackArchive)
                .orElseGet(() -> StorageUnpackArchiveTaskConfig.builder().build());
    }
}

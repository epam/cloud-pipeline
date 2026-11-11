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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnpackArchiveConfig {

    /**
     * Largest archive object that may be extracted, checked against the storage listing before a byte
     * of it is fetched. The cheapest refusal there is.
     */
    private Long maxArchiveSizeBytes;

    /**
     * Largest total <b>uncompressed</b> size that may be written. Enforced twice: against what the
     * archive declares, where its format reveals it, and again against the bytes actually written.
     */
    private Long maxSizeBytes;

    /**
     * Largest number of entries an archive may hold. Known up front for ZIP and plain TAR, and counted
     * while extracting for everything else - a bomb of many tiny entries costs little in bytes and a
     * great deal in requests, so the size limit alone does not cover it.
     */
    private Integer maxFilesCount;

    /**
     * How long a worker may work on one extraction, that is how long the task may stay in
     * {@code RUNNING}. The orchestrator hands the value over at startup and the worker stops itself
     * once past it.
     */
    private Integer workerMaxRunningMinutes;
}

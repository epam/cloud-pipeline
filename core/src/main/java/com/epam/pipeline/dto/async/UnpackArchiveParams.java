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

package com.epam.pipeline.dto.async;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UnpackArchiveParams implements AsyncTaskParams {

    private Long sourceStorageId;
    private String sourcePath;

    private Long destinationStorageId;
    private String destinationPath;

    /**
     * Remove the archive once the extraction succeeded. Authorized as a write on the source when the
     * task is created, and performed last so that a failure leaves the caller their archive.
     */
    private boolean deleteSourceOnSuccess;

    /**
     * Replace entries that already exist in the destination. With {@code false} a single collision
     * refuses the whole task and nothing is written at all. Its scope is only the entries the archive
     * would write; anything else in the destination is never touched either way.
     */
    private boolean overwrite;

    @Override
    public AsyncTaskType getType() {
        return AsyncTaskType.UNPACK_ARCHIVE;
    }
}

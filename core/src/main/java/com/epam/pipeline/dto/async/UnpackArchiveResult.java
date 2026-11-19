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

/**
 * What the extraction wrote. Reported by the executor on success, and only then.
 *
 * <p>Unlike a download archive this names no artifact the platform owns: the content belongs to the
 * destination storage the caller chose, so there is nothing to hand over later and nothing to expire.
 * The task stays {@code SUCCESS} for good.</p>
 *
 * <p>Two things are deliberately absent. <b>Skipped entries</b> - symlinks, hardlinks, devices and
 * empty directories on an object storage - are logged by the executor and not reported here; adding a
 * count later is a backward-compatible change, and a list would need a cap, since an archive may hold
 * thousands of symlinks. <b>The keys that were written</b> are not here either, and could not be: the
 * queue accepts a result on {@code SUCCESS} only ({@code AsyncTaskManager.assertPayloadForStatus}), so
 * a field meant for cleaning up after a cancellation or a failure would be populated exactly when
 * there is nothing to clean up. That journal belongs in the executor's own scratch directory -
 * {@code spec.md} §10, open question 1.</p>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UnpackArchiveResult implements AsyncTaskResult {

    private Long destinationStorageId;
    private String destinationPath;

    private Integer filesWritten;
    private Long bytesWritten;

    @Override
    public AsyncTaskType getType() {
        return AsyncTaskType.UNPACK_ARCHIVE;
    }
}

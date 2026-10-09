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

import com.epam.pipeline.dto.async.AsyncTaskResult;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What an executor reports about a task it was given. One shape for every transition: the status it
 * moved to, plus whichever of the two payloads that status carries - a result on success, a message on
 * failure.
 *
 * <p>The example is spelled out because the payload is polymorphic: its {@code type} selects which
 * shape the rest of the object has, and a generated placeholder there cannot be resolved.</p>
 */
@Schema(example = "{\"status\": \"SUCCESS\", \"result\": "
        + "{\"type\": \"DOWNLOAD_ARCHIVE\", \"archiveSizeBytes\": 12345, \"skippedFiles\": 0}}")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskStatusVO {

    private AsyncTaskStatus status;
    private AsyncTaskResult result;
    private String errorMessage;
}

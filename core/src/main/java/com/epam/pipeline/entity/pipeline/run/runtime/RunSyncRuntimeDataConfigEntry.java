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

package com.epam.pipeline.entity.pipeline.run.runtime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RunSyncRuntimeDataConfigEntry {
    // Storage path where folder for the particular run will be located.
    // Each run folder will have its own name equal to run id.
    private final String runFolderPathPrefix;
    // (Optional) Path where data for this config entry will be located.
    // This path is relative to run folder.
    // If not present, data should be located in the run folder itself.
    private final String dataPathPrefix;
}

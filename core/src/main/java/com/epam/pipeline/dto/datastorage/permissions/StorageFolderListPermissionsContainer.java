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

package com.epam.pipeline.dto.datastorage.permissions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
@Builder
public class StorageFolderListPermissionsContainer {
    /**
     * Contains requested folder permission mask. If null no permissions found for parent folders.
     */
    private Integer folderMask;
    /**
     * Map of file names to permission masks that current folder may contain
     */
    private Map<String, Integer> files;
    /**
     * Map of folder names to permission masks that current folder may contain
     */
    private Map<String, Integer> folders;
}

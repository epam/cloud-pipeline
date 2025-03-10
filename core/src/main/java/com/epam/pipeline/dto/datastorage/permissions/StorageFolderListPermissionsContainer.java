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
import org.apache.commons.collections4.ListUtils;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class StorageFolderListPermissionsContainer {
    /**
     * Indicates whether list permissions are granted for the requested folder.
     * If so, files/folders fields will be ignored.
     * If no permissions are granted, only items that exactly match the names of the files or folders will be listed.
     */
    private boolean hasListPermissions;
    /**
     * List of file names that folder may contain
     */
    private List<String> files;
    /**
     * List of folder names that folder may contain
     */
    private List<String> folders;

    public boolean folderNotAllowed(final String folderName) {
        return !hasListPermissions && !ListUtils.emptyIfNull(folders).contains(folderName);
    }

    public boolean fileNotAllowed(final String fileName) {
        return !hasListPermissions && !ListUtils.emptyIfNull(files).contains(fileName);
    }
}

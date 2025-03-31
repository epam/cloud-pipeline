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

package com.epam.pipeline.manager.datastorage.permissions;

import com.epam.pipeline.dto.datastorage.permissions.StorageFolderListPermissionsContainer;
import org.apache.commons.collections4.MapUtils;

import java.util.Objects;

public interface StoragePathPermissionsUtils {

    static Integer getFolderMask(final StorageFolderListPermissionsContainer container, final String folderName) {
        if (Objects.isNull(container)) {
            return null;
        }
        return MapUtils.emptyIfNull(container.getFolders()).getOrDefault(folderName, container.getFolderMask());
    }

    static Integer getFileMask(final StorageFolderListPermissionsContainer container, final String fileName) {
        if (Objects.isNull(container)) {
            return null;
        }
        return MapUtils.emptyIfNull(container.getFiles()).getOrDefault(fileName, container.getFolderMask());
    }

    static boolean filterNotAllowedFiles(final StorageFolderListPermissionsContainer container,
                                         final String fileName) {
        return Objects.nonNull(container)
                && Objects.isNull(container.getFolderMask())
                && !MapUtils.emptyIfNull(container.getFiles()).containsKey(fileName);
    }

    static boolean filterNotAllowedFolders(final StorageFolderListPermissionsContainer container,
                                           final String folderName) {
        return Objects.nonNull(container)
                && Objects.isNull(container.getFolderMask())
                && !MapUtils.emptyIfNull(container.getFolders()).containsKey(folderName);
    }
}

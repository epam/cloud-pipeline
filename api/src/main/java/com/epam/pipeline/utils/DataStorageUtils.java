/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.utils;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.pipeline.run.parameter.DataStorageLink;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@SuppressWarnings("HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataStorageUtils {

    public static DataStorageLink constructDataStorageLink(final AbstractDataStorage dataStorage,
                                                           final String path,
                                                           final String mask) {
        final DataStorageLink dataStorageLink = new DataStorageLink();
        dataStorageLink.setAbsolutePath(path.trim());
        dataStorageLink.setDataStorageId(dataStorage.getId());
        String relativePath = path.trim().substring(mask.length());
        if (relativePath.startsWith(ProviderUtils.DELIMITER)) {
            relativePath = relativePath.substring(1);
        }
        final String[] parts = relativePath.split(ProviderUtils.DELIMITER);
        final String lastPart = parts[parts.length - 1];
        if (lastPart.contains(".")) {
            String newPath = "";
            for (int i = 0; i < parts.length - 1; i++) {
                newPath = newPath.concat(parts[i] + ProviderUtils.DELIMITER);
            }
            if (newPath.endsWith(ProviderUtils.DELIMITER)) {
                newPath = newPath.substring(0, newPath.length() - 1);
            }
            dataStorageLink.setPath(newPath);
        } else {
            dataStorageLink.setPath(relativePath);
        }
        return dataStorageLink;
    }

    public static DataStorageLink constructDataStorageFileLink(final AbstractDataStorage dataStorage,
                                                               final String path) {
        final String relativePath;
        if (path.startsWith(dataStorage.getPathMask())) {
            relativePath = path.substring(dataStorage.getPathMask().length())
                    .substring(ProviderUtils.DELIMITER.length());
        } else {
            relativePath = path.startsWith(ProviderUtils.DELIMITER)
                    ? path.substring(1) : path;
        }
        final DataStorageLink dataStorageLink = new DataStorageLink();
        dataStorageLink.setAbsolutePath(dataStorage.getPathMask()
                + ProviderUtils.DELIMITER + relativePath);
        dataStorageLink.setDataStorageId(dataStorage.getId());
        dataStorageLink.setPath(relativePath);
        return dataStorageLink;
    }

}

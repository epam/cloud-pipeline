/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers;

import com.epam.pipeline.controller.vo.data.storage.RestoreFolderVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

import java.util.Optional;
import java.util.function.Function;

public final class ProviderUtils {

    public static final String DELIMITER = "/";
    public static final String FOLDER_TOKEN_FILE = ".DS_Store";
    public static final String OWNER_TAG_KEY = "CP_OWNER";

    private ProviderUtils() {
        //no op
    }

    public static String withTrailingDelimiter(final String path) {
        return StringUtils.isNotBlank(path) && !path.endsWith(DELIMITER) ? path + DELIMITER : path;
    }

    public static String withoutLeadingDelimiter(final String path) {
        return StringUtils.isNotBlank(path) && path.startsWith(ProviderUtils.DELIMITER) ? path.substring(1) : path;
    }

    public static String normalizeBucketName(String name) {
        String bucketName = name.trim().toLowerCase();
        bucketName = bucketName.replaceAll("[^a-z0-9\\-]+", "-");
        return bucketName;
    }

    public static String withoutTrailingDelimiter(final String path) {
        return StringUtils.isNotBlank(path) && path.endsWith(DELIMITER) ? path.substring(0, path.length() - 1) : path;
    }

    public static <T> PathDescription getSizeByPath(final Iterable<T> items, final String requestPath,
                                                    final Function<T, Long> getSize, final Function<T, String> getName,
                                                    final PathDescription pathDescription) {
        final boolean rootOrFolder = isRootOrFolder(requestPath);

        for (final T item : items) {
            if (rootOrFolder) {
                // if required path is definitely folder lists all files without filtering
                pathDescription.increaseSize(getSize.apply(item));
            } else if (getName.apply(item).equals(requestPath)) {
                // a file with exact match has been found => required path is path to file
                pathDescription.setSize(getSize.apply(item));
                pathDescription.setCompleted(true);
                return pathDescription;
            } else if (getName.apply(item).startsWith(requestPath + ProviderUtils.DELIMITER)) {
                // if required path is folder but '/' at the end was not specified
                pathDescription.increaseSize(getSize.apply(item));
            }
        }

        return pathDescription;
    }

    public static boolean isRootOrFolder(final String requestPath) {
        return StringUtils.isBlank(requestPath) || requestPath.endsWith(DELIMITER);
    }

    public static boolean isFileWithDeleteMarkerAndShouldBeRestore(final AbstractDataStorageItem item,
                                                                   final RestoreFolderVO restoreFolderVO) {
        final AntPathMatcher matcher = new AntPathMatcher();
        return item.getType() == DataStorageItemType.File &&
                ((DataStorageFile) item).getDeleteMarker() &&
                ((DataStorageFile) item).getVersion() != null &&
                Optional.ofNullable(restoreFolderVO.getIncludeList()).map(includeList -> includeList.stream()
                        .anyMatch(pattern -> matcher.match(pattern, item.getName()))).orElse(true) &&
                Optional.ofNullable(restoreFolderVO.getExcludeList()).map(excludeList -> excludeList.stream()
                        .noneMatch(pattern -> matcher.match(pattern, item.getName()))).orElse(true);
    }
}

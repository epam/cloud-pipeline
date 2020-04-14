/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DatastoragePath;
import com.epam.pipeline.entity.datastorage.PathDescription;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
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
        return StringUtils.isNotBlank(path) && path.startsWith(DELIMITER) ? path.substring(1) : path;
    }

    public static DatastoragePath parsePath(final String fullPath) {
        final String[] chunks = fullPath.split(DELIMITER);
        final String root = chunks[0];
        final String path = chunks.length > 1 ?
                StringUtils.join(Arrays.copyOfRange(chunks, 1, chunks.length), DELIMITER) : StringUtils.EMPTY;
        return new DatastoragePath(root, path);
    }

    public static String normalizeBucketName(final String name) {
        final DatastoragePath datastoragePath = parsePath(name);
        final String bucketName = datastoragePath.getRoot()
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9\\-]+", "-");
        return StringUtils.isBlank(datastoragePath.getPath()) ?
                bucketName : bucketName + DELIMITER + datastoragePath.getPath();
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

    public static String buildPath(final AbstractDataStorage dataStorage, final String path) {
        final DatastoragePath datastoragePath = parsePath(dataStorage.getPath());
        return mergePaths(datastoragePath.getPath(), path);
    }

    public static String mergePaths(final String parent, final String child) {
        if (StringUtils.isBlank(parent)) {
            return child;
        }
        if (StringUtils.isBlank(child)) {
            return parent;
        }
        return withTrailingDelimiter(parent) + withoutLeadingDelimiter(child);
    }

    public static String removePrefix(final String path, final String prefix) {
        return path.startsWith(prefix) ? path.replaceFirst(prefix, StringUtils.EMPTY) : path;
    }
}

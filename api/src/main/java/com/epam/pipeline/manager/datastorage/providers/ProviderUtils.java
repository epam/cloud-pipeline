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

import org.apache.commons.lang3.StringUtils;

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

    public static <T> Long getSizeByPath(final Iterable<T> items, final String requestPath,
                                         final Function<T, Long> getSize, final Function<T, String> getName) {
        final boolean rootOrFolder = isRootOrFolder(requestPath);

        Long folderSize = 0L;
        for (final T item : items) {
            if (rootOrFolder) {
                folderSize += getSize.apply(item);
            } else if (getName.apply(item).equals(requestPath)) {
                return getSize.apply(item);
            } else if (getName.apply(item).startsWith(requestPath + ProviderUtils.DELIMITER)) {
                folderSize += getSize.apply(item);
            }
        }

        return folderSize;
    }

    public static boolean isRootOrFolder(final String requestPath) {
        return StringUtils.isBlank(requestPath) || requestPath.endsWith(DELIMITER);
    }
}

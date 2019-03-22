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

package com.epam.pipeline.manager.utils;

import org.apache.commons.lang.StringUtils;

public final class MetadataParsingUtils {

    public static final String CSV_DELIMITER = ",";
    public static final String TAB_DELIMITER = "\t";

    private MetadataParsingUtils() {
        // no op
    }

    public static String getDelimiterFromFileExtension(String fileName) {
        if (StringUtils.endsWithIgnoreCase(fileName, ".csv")) {
            return CSV_DELIMITER;
        }
        if (StringUtils.endsWithIgnoreCase(fileName, ".tdf") || StringUtils.endsWithIgnoreCase(fileName, ".tsv")) {
            return TAB_DELIMITER;
        }
        throw new IllegalArgumentException(String.format("Unsupported extension for file %s", fileName));
    }
}

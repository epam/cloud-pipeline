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

package com.epam.pipeline.utils;

import org.apache.commons.lang3.StringUtils;
import java.nio.charset.Charset;

public final class FileContentUtils {

    private static final int CONTENT_CHECK_LIMIT = 1024;
    private static final int ASCII_LIMIT = 128;
    private static final int SPACE = 31;
    private static final int BACKSPACE = 8;
    private static final int CARRIAGE_RETURN = 13;

    private FileContentUtils() {}

    public static boolean isBinaryContent(byte[] byteContent) {
        if (byteContent == null || byteContent.length == 0) {
            return false;
        }
        return !isAsciiContent(
                new String(byteContent, 0, Math.min(byteContent.length, CONTENT_CHECK_LIMIT),
                        Charset.defaultCharset()));
    }

    /**
     * Checks if all symbols in the input string are in ASCII range fro letters [31,127] or
     * it is one of common special symbols: new line, tab, etc.
     * @param data
     * @return
     */
    public static boolean isAsciiContent(String data) {
        if (StringUtils.isBlank(data)) {
            return false;
        }
        return data.chars().allMatch(c -> (c >= SPACE && c < ASCII_LIMIT) ||
                (c >= BACKSPACE && c <= CARRIAGE_RETURN));
    }

}

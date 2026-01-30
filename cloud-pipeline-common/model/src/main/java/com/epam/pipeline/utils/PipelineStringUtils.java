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

public final class PipelineStringUtils {

    public static final String DASH = "-";
    private static final String ALPHANUMERIC_DASH_TEMPLATE = "[^a-zA-Z0-9\\-]+";

    private PipelineStringUtils() {
        //no op
    }

    public static String convertToAlphanumericWithDashes(final String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        return input.replaceAll(ALPHANUMERIC_DASH_TEMPLATE, DASH);
    }

    /**
     * Spring MVC may bind {@code @RequestBody String} for {@code application/json} using a String converter,
     * resulting in the JSON literal being passed verbatim (e.g. {@code "\"TEST\""}). To preserve the legacy
     * behaviour (treating JSON string bodies as plain text), we unquote such bodies here.
     */
    public static String normalizeJsonStringLiteral(final String raw) {
        if (raw == null) {
            return "";
        }
        final String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            // Minimal unquoting (covers our API usage where body is a simple JSON string).
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return raw;
    }
}

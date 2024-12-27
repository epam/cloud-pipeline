/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DynamicPipelineRunParameterUtils {

    private DynamicPipelineRunParameterUtils() {
        //
    }

    private static final Pattern PATTERN = Pattern.compile("\\$\\[([a-zA-Z_]+)]");
    private static final Map<String, Supplier<String>> PLACEHOLDER_TO_FUNCTION =
            new HashMap<String, Supplier<String>>() {{
                put("UUID", () -> UUID.randomUUID().toString());
            }};

    static String applyDynamicValue(final String value) {
        final Matcher valueMatcher = PATTERN.matcher(value);
        if (valueMatcher.find()) {
            final String dynamicFunctionName = valueMatcher.group(1);
            return PLACEHOLDER_TO_FUNCTION.getOrDefault(dynamicFunctionName, () -> null).get();
        }
        return null;
    }
}

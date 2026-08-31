/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.utils;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class KubernetesMemoryParser {
    private static final Pattern MEMORY_PATTERN = Pattern.compile("^(\\d+)([EPTGMKi]{1,2})?$");
    private static final Map<String, Double> UNIT_TO_BYTES = new HashMap<>();
    //CHECKSTYLE:OFF
    static {
        UNIT_TO_BYTES.put("Ki", Math.pow(2, 10));
        UNIT_TO_BYTES.put("Mi", Math.pow(2, 20));
        UNIT_TO_BYTES.put("Gi", Math.pow(2, 30));
        UNIT_TO_BYTES.put("Ti", Math.pow(2, 40));
        UNIT_TO_BYTES.put("Pi", Math.pow(2, 50));
        UNIT_TO_BYTES.put("Ei", Math.pow(2, 60));

        UNIT_TO_BYTES.put("K", Math.pow(10, 3));
        UNIT_TO_BYTES.put("M", Math.pow(10, 6));
        UNIT_TO_BYTES.put("G", Math.pow(10, 9));
        UNIT_TO_BYTES.put("T", Math.pow(10, 12));
        UNIT_TO_BYTES.put("P", Math.pow(10, 15));
        UNIT_TO_BYTES.put("E", Math.pow(10, 18));
    }
    //CHECKSTYLE:ON

    private KubernetesMemoryParser() {
        //no op
    }

    public static Long parseMemoryToBytes(final String value) {
        final Matcher matcher = MEMORY_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        final Long digits = Long.parseLong(matcher.group(1));
        final String unit = matcher.group(2);

        if (StringUtils.isBlank(unit)) {
            return digits;
        }

        if (!UNIT_TO_BYTES.containsKey(unit)) {
            log.error("Unknown k8s memory unit {}", unit);
        }
        return digits * UNIT_TO_BYTES.get(unit).longValue();
    }
}

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

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

//TODO: Make parametrized test
public class KubernetesRequestParserTest {

    private static final Map<String, Long> VALID_MEMORY_VALUES = new HashMap<>();
    private static final Map<String, Long> VALID_CPU_VALUES = new HashMap<>();
    //CHECKSTYLE:OFF
    static {
        VALID_MEMORY_VALUES.put("123", 123L);
        VALID_MEMORY_VALUES.put("10K", 10000L);
        VALID_MEMORY_VALUES.put("1Mi", 1024L * 1024L);
    }
    static {
        VALID_CPU_VALUES.put("3", 3L);
        VALID_CPU_VALUES.put("3800m", 3L);
        VALID_CPU_VALUES.put("2100m", 2L);
        VALID_CPU_VALUES.put("4000m", 4L);
    }
    //CHECKSTYLE:NO

    @Test
    public void shouldParseValidMemoryValues() {
        VALID_MEMORY_VALUES.forEach((value, expected) ->
                assertEquals(expected, KubernetesRequestParser.parseRequest(value)));
    }
    @Test
    public void shouldParseValidCPUValues() {
        VALID_CPU_VALUES.forEach((value, expected) ->
                assertEquals(expected, KubernetesRequestParser.parseRequest(value)));
    }

    @Test
    public void shouldReturnNullForInvalidPattern() {
        assertNull(KubernetesRequestParser.parseRequest("123KB"));
    }
}
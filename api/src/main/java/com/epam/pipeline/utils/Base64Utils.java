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

package com.epam.pipeline.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;

import java.util.Map;

public final class Base64Utils {
    public static final String EMPTY_ENCODED_MAP = Base64.encodeBase64String("{}".getBytes());
    private static final ObjectMapper MAPPER = new ObjectMapper();


    private Base64Utils() {

    }

    public static String encodeBase64Map(Map<String, String> toEncode) throws JsonProcessingException {
        return  Base64.encodeBase64String(MAPPER.writeValueAsString(toEncode).getBytes());
    }
}

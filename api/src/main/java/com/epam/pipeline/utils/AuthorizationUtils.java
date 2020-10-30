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

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public interface AuthorizationUtils {

    String BASIC_AUTH = "Basic";

    static String[] parseBasicAuth(final String authorization) {
        if (authorization != null && authorization.startsWith(BASIC_AUTH)) {
            // Authorization: Basic base64credentials
            final String base64Credentials = authorization.substring(BASIC_AUTH.length()).trim();
            final String credentials = new String(Base64.getDecoder().decode(base64Credentials),
                    StandardCharsets.UTF_8);
            // credentials = username:password
            final String[] values = credentials.split(":", 2);
            if (values.length < 2 || StringUtils.isBlank(values[0]) || StringUtils.isBlank(values[1])) {
                return null;
            }
            return values;
        }
        return null;
    }
}

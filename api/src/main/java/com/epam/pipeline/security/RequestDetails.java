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

package com.epam.pipeline.security;

import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import org.springframework.util.StringUtils;

@Builder
@Data
public class RequestDetails {

    private static final String H_CONTENT_TYPE_APPLICATION_JSON = "-H 'Content-Type: application/json'";
    private static final String QUOTE = "'";
    private static final String PARAMS_MARK = "?";
    private static final String DATA = "--data";

    private final String httpMethod;
    private final StringBuffer path;
    private final String query;
    private final String body;

    @SneakyThrows
    @Override
    public String toString() {
        StringBuilder curl = new StringBuilder(
                String.format("curl -k %s -X%s '%s",
                        H_CONTENT_TYPE_APPLICATION_JSON,
                        httpMethod,
                        path
                )
        );
        if (!StringUtils.isEmpty(query)) {
            curl.append(PARAMS_MARK).append(query).append(QUOTE);
        } else {
            curl.append(QUOTE);
        }

        if (!StringUtils.isEmpty(body)) {
            curl.append(" " + DATA + " " + QUOTE).append(body).append(QUOTE);
        }

        return curl.toString();
    }
}

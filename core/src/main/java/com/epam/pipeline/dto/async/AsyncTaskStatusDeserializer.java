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

package com.epam.pipeline.dto.async;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.util.Arrays;

/**
 * Reads a task status, and says plainly when the value is not one.
 *
 * <p>Left to itself, Jackson reports an unknown constant by naming the Java class it was building and
 * listing every constant of that class. The message is accurate but it exposes internals and is long
 * enough to bury the one thing the caller needs to know, which is that the value it sent is not a
 * status. The platform's error handler passes an exception message through untouched, so the message
 * is shaped here rather than intercepted later.</p>
 */
public class AsyncTaskStatusDeserializer extends JsonDeserializer<AsyncTaskStatus> {

    private static final String UNKNOWN_STATUS = "Status '%s' does not exist";

    @Override
    public AsyncTaskStatus deserialize(final JsonParser parser, final DeserializationContext context)
            throws IOException {
        final String value = parser.getValueAsString();
        return Arrays.stream(AsyncTaskStatus.values())
                .filter(status -> status.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> InvalidFormatException.from(parser,
                        String.format(UNKNOWN_STATUS, value), value, AsyncTaskStatus.class));
    }
}

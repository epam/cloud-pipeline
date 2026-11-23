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

package com.epam.pipeline.security.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializationFailedException;

/**
 * We replace the default DeserializingConverter because of what happens after a release. Sessions are stored as
 * serialized Java objects. When we add or remove a field in a stored class, the old bytes no longer match the new
 * class, and the default reader throws SerializationFailedException. Users who were logged in before the release then
 * get a server error on every request, and they stay stuck until their session row expires.
 * <p>
 * This reader returns null for such bytes instead. Spring Security sees a session with no security context, counts the
 * request as anonymous and sends the user to the login page. One new login fixes it. So a broken release looks like
 * "please log in again" rather than a crash.
 */
@Slf4j
public class PipelineDeserializingConverter extends DeserializingConverter {
    public PipelineDeserializingConverter(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public Object convert(byte[] source) {
        try {
            return super.convert(source);
        } catch (SerializationFailedException e) {
            log.warn(e.getMessage());
            return null;
        }
    }
}

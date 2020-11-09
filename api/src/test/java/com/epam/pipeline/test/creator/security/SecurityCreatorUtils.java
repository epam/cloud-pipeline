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

package com.epam.pipeline.test.creator.security;

<<<<<<< HEAD
import com.epam.pipeline.security.UserContext;
=======
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.security.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
>>>>>>> Issue #1405: Implemented tests (except one) for docker registry controller layer

public final class SecurityCreatorUtils {

    public static final TypeReference<JwtRawToken> JWT_RAW_TOKEN_INSTANCE_TYPE = new TypeReference<JwtRawToken>() {};

    private SecurityCreatorUtils() {

    }

<<<<<<< HEAD
    public static UserContext getUserContext() {
        return new UserContext();
    }

    public static UserContext getUserContext(final boolean external) {
        final UserContext context = new UserContext();
        context.setExternal(external);
        return context;
=======
    public static JwtRawToken getJwtRawToken() {
        return new JwtRawToken(TEST_STRING);
>>>>>>> Issue #1405: Implemented tests (except one) for docker registry controller layer
    }

    public static UserContext getUserContext() {
        return new UserContext();
    }
}

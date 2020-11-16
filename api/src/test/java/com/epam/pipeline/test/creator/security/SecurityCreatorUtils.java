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

import com.epam.pipeline.security.UserContext;

public final class SecurityCreatorUtils {

    public static final TypeReference<Result<Map<AclClass, List<S3bucketDataStorage>>>> ACL_SECURED_ENTITY_MAP_TYPE =
            new TypeReference<Result<Map<AclClass, List<S3bucketDataStorage>>>>() {};

    public static final TypeReference<JwtRawToken> JWT_RAW_TOKEN_INSTANCE_TYPE = new TypeReference<JwtRawToken>() {};

    private SecurityCreatorUtils() {

    }

    public static UserContext getUserContext() {
        return new UserContext();
    }

    public static UserContext getUserContext(final boolean external) {
        final UserContext context = new UserContext();
        context.setExternal(external);
        return context;
    }

    public static AclSid getAclSid() {
        return new AclSid();
    }

    public static JwtRawToken getJwtRawToken() {
        return new JwtRawToken(TEST_STRING);
    }
}

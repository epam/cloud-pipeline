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

package com.epam.pipeline.entity.security;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Objects;

import javax.servlet.http.Cookie;

import com.epam.pipeline.utils.AuthorizationUtils;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationServiceException;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class JwtRawToken implements Serializable {
    private static final String BEARER_PREFIX = "Bearer ";
    private String token;

    public static JwtRawToken fromHeader(String authorizationHeader) {
        if (StringUtils.isEmpty(authorizationHeader)) {
            throw new AuthenticationServiceException("Authorization header is blank");
        }

        return getJwtRawToken(authorizationHeader);
    }

    public static JwtRawToken fromCookie(Cookie authCookie) throws UnsupportedEncodingException {
        if (authCookie == null || StringUtils.isEmpty(authCookie.getValue())) {
            throw new AuthenticationServiceException("Authorization cookie is blank");
        }

        final String authCookieValue = URLDecoder.decode(authCookie.getValue(), "UTF-8");

        return getJwtRawToken(authCookieValue);
    }

    private static JwtRawToken getJwtRawToken(final String authorizationValue) {
        if (authorizationValue.startsWith(BEARER_PREFIX)) {
            return new JwtRawToken(authorizationValue.substring(BEARER_PREFIX.length()));
        }

        if (authorizationValue.startsWith(AuthorizationUtils.BASIC_AUTH)) {
            final String[] credentials = AuthorizationUtils.parseBasicAuth(authorizationValue);
            if (Objects.nonNull(credentials)) {
                return new JwtRawToken(credentials[1]);
            }
        }

        throw new AuthenticationServiceException("Authorization type Bearer or Basic Auth is missed");
    }

    public String toHeader() {
        return BEARER_PREFIX + token;
    }
}

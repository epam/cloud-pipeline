/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.security.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationServiceException;

import javax.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

@Getter
@AllArgsConstructor
public class JwtRawToken {
    private static final String HEADER_PREFIX = "Bearer ";
    private String token;

    public static JwtRawToken fromHeader(String authorizationHeader) {
        if (StringUtils.isEmpty(authorizationHeader)) {
            throw new AuthenticationServiceException("Authorization header is blank");
        }

        if (!authorizationHeader.startsWith(HEADER_PREFIX)) {
            throw new AuthenticationServiceException("Authorization type Bearer is missed");
        }

        return new JwtRawToken(authorizationHeader.substring(HEADER_PREFIX.length(), authorizationHeader.length()));
    }

    public static JwtRawToken fromCookie(Cookie authCookie) throws UnsupportedEncodingException {
        if (authCookie == null || StringUtils.isEmpty(authCookie.getValue())) {
            throw new AuthenticationServiceException("Authorization cookie is blank");
        }

        String authCookieValue = URLDecoder.decode(authCookie.getValue(), "UTF-8");

        if (!authCookieValue.startsWith(HEADER_PREFIX)) {
            throw new AuthenticationServiceException("Authorization type Bearer is missed");
        }

        return new JwtRawToken(authCookieValue.substring(HEADER_PREFIX.length(), authCookieValue.length()));
    }

    public String toHeader() {
        return HEADER_PREFIX + token;
    }
}


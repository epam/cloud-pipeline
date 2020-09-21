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

package com.epam.pipeline.security.jwt;

import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;

@RequiredArgsConstructor
public class JwtAuthenticationProvider implements AuthenticationProvider {
    private final JwtTokenVerifier tokenVerifier;
    private final UserAccessService accessService;

    @Override
    public Authentication authenticate(Authentication authentication) {
        JwtRawToken jwtRawToken = (JwtRawToken) authentication.getCredentials();
        if (jwtRawToken == null) {
            throw new AuthenticationServiceException("Authentication error: missing token");
        }
        JwtTokenClaims claims;
        try {
            claims = tokenVerifier.readClaims(jwtRawToken.getToken());
        } catch (TokenVerificationException e) {
            throw new AuthenticationServiceException("Authentication error", e);
        }

        UserContext context = accessService.getJwtUser(jwtRawToken, claims);
        return new JwtAuthenticationToken(context, context.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (JwtAuthenticationToken.class.isAssignableFrom(authentication));
    }
}

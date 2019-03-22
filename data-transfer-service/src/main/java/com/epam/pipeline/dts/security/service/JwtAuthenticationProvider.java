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

package com.epam.pipeline.dts.security.service;


import com.epam.pipeline.dts.security.exception.TokenVerificationException;
import com.epam.pipeline.dts.security.model.JwtAuthenticationToken;
import com.epam.pipeline.dts.security.model.JwtRawToken;
import com.epam.pipeline.dts.security.model.JwtTokenClaims;
import com.epam.pipeline.dts.security.model.UserContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;

public class JwtAuthenticationProvider implements AuthenticationProvider {
    private JwtTokenVerifier tokenVerifier;

    public JwtAuthenticationProvider(JwtTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

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

        UserContext context = new UserContext(jwtRawToken, claims);

        return new JwtAuthenticationToken(context, context.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (JwtAuthenticationToken.class.isAssignableFrom(authentication));
    }
}

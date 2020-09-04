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

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Slf4j
public class JwtFilterAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenVerifier tokenVerifier;
    private final UserAccessService accessService;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final JwtRawToken rawToken = fetchJwtRawToken(request);
        try {
            if (!StringUtils.isEmpty(rawToken)) {
                JwtTokenClaims claims = tokenVerifier.readClaims(rawToken.getToken());
                UserContext context = new UserContext(rawToken, claims);
                accessService.validateUserBlockStatus(context.getUsername());
                JwtAuthenticationToken token = new JwtAuthenticationToken(context, context.getAuthorities());
                token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(token);
                log.info("Successfully authenticate user with name: " + context.getUsername());
            }
        } catch (TokenVerificationException e) {
            log.info("JWT authentication failed!", e);
        }
        filterChain.doFilter(request, response);
    }

    private JwtRawToken fetchJwtRawToken(final HttpServletRequest request) throws UnsupportedEncodingException {
        JwtRawToken rawToken = null;
        String authorizationHeader = extractAuthHeader(request);
        Cookie authCookie = extractAuthCookie(request);
        try {
            if (!StringUtils.isEmpty(authorizationHeader)) { // attempt obtain JWT token from HTTP header
                rawToken = JwtRawToken.fromHeader(authorizationHeader);
                log.trace("Extracted JWT token from authorization HTTP header");
            } else if (!StringUtils.isEmpty(authCookie)) {   // else try to get token from cookies
                rawToken = JwtRawToken.fromCookie(authCookie);
                log.trace("Extracted JWT token from authorization cookie");
            }
        } catch (AuthenticationServiceException e) {
            log.trace(e.getMessage(), e);
        }
        return rawToken;
    }

    private String extractAuthHeader(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }

    private Cookie extractAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("HttpAuthorization".equals(cookie.getName())) {
                    return cookie;
                }
            }
        }
        return null;
    }
}

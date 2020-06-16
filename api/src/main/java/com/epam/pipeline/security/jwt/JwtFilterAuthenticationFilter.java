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
import java.nio.charset.StandardCharsets;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.epam.pipeline.controller.MultiReadableHttpServletRequestWrapper;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.security.RequestDetails;
import com.epam.pipeline.security.UserContext;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtFilterAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtFilterAuthenticationFilter.class);

    public static final String REQUEST_DETAILS = "request_details";

    private JwtTokenVerifier tokenVerifier;

    public JwtFilterAuthenticationFilter(JwtTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        request = new MultiReadableHttpServletRequestWrapper(request);
        final JwtRawToken rawToken = fetchJwtRawToken(request);
        putRequestDataToLogContext(request);
        try {
            if (!StringUtils.isEmpty(rawToken)) {
                JwtTokenClaims claims = tokenVerifier.readClaims(rawToken.getToken());
                UserContext context = new UserContext(rawToken, claims);
                JwtAuthenticationToken token = new JwtAuthenticationToken(context, context.getAuthorities());
                token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(token);
                LOGGER.info("Successfully authenticate user with name: " + context.getUsername());
            }
        } catch (TokenVerificationException e) {
            LOGGER.info("JWT authentication failed!", e);
        }
        removeRequestDataFromLogContext();
        filterChain.doFilter(request, response);
    }

    private JwtRawToken fetchJwtRawToken(HttpServletRequest request) throws UnsupportedEncodingException {
        JwtRawToken rawToken = null;
        String authorizationHeader = extractAuthHeader(request);
        Cookie authCookie = extractAuthCookie(request);
        try {
            if (!StringUtils.isEmpty(authorizationHeader)) { // attempt obtain JWT token from HTTP header
                rawToken = JwtRawToken.fromHeader(authorizationHeader);
                LOGGER.trace("Extracted JWT token from authorization HTTP header");
            } else if (!StringUtils.isEmpty(authCookie)) {   // else try to get token from cookies
                rawToken = JwtRawToken.fromCookie(authCookie);
                LOGGER.trace("Extracted JWT token from authorization cookie");
            }
        } catch (AuthenticationServiceException e) {
            LOGGER.trace(e.getMessage(), e);
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

    private void removeRequestDataFromLogContext() {
        ThreadContext.clearAll();
    }

    private void putRequestDataToLogContext(HttpServletRequest request) {
        try {
            ThreadContext.put(REQUEST_DETAILS, getRequestDetails(request).toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private RequestDetails getRequestDetails(HttpServletRequest request) throws IOException {
        final RequestDetails.RequestDetailsBuilder builder = RequestDetails.builder();
        final String httpMethod = request.getMethod();
        builder.path(request.getRequestURL())
                .httpMethod(httpMethod)
                .query(request.getQueryString())
                .body(readBody(request));

        return builder.build();
    }

    private String readBody(HttpServletRequest request) throws IOException {
        return IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
    }
}

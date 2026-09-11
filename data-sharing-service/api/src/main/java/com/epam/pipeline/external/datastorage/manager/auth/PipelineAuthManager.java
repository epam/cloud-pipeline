/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.manager.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.external.datastorage.entity.PipelineToken;
import com.epam.pipeline.external.datastorage.exception.PipelineAuthenticationException;
import com.epam.pipeline.external.datastorage.exception.TokenExpiredException;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import com.epam.pipeline.external.datastorage.security.UserContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class PipelineAuthManager {
    public static final String UNAUTHORIZED_USER = "Unauthorized";

    private final PipelineAuthClient authClient;
    private final CloudPipelineApiExecutor apiExecutor;

    public PipelineAuthManager(final CloudPipelineApiBuilder builder,
                               final CloudPipelineApiExecutor apiExecutor) {
        this.authClient = builder.getClient(PipelineAuthClient.class);
        this.apiExecutor = apiExecutor;
    }

    public UserContext getUser() {
        Object principal = getPrincipal();
        if (principal instanceof UserContext) {
            return (UserContext)principal;
        } else {
            return null;
        }
    }

    private Object getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return UNAUTHORIZED_USER;
        }
        return authentication.getPrincipal();
    }

    public String getHeader() {
        return "Bearer " + getToken();
    }

    public String getToken() {
        try {
            return getUser().getToken();
        } catch (TokenExpiredException e) {
            final String token = exchangeTokenFromSamlCredentials();
            getUser().setToken(token);
            return token;
        }
    }

    private String exchangeTokenFromSamlCredentials() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getCredentials() instanceof String)) {
            throw new PipelineAuthenticationException("SAML credentials are not available to obtain a token");
        }
        return getToken((String) authentication.getCredentials());
    }

    public String getToken(final String rawSamlResponse) {
        final PipelineToken response = apiExecutor.execute(
                authClient.getToken(toSamlResponseParameter(rawSamlResponse)));
        if (response == null || response.getToken() == null) {
            throw new PipelineAuthenticationException("Failed to obtain token from Pipeline API");
        }
        return response.getToken();
    }

    /**
     * Spring Security 6 passes the SAML response as a Base64-encoded string (HTTP-POST binding).
     * Legacy spring-security-saml2-core passed raw XML, which had to be encoded here.
     */
    private static String toSamlResponseParameter(final String samlResponse) {
        if (StringUtils.startsWith(StringUtils.trim(samlResponse), "<")) {
            return Base64.getEncoder().encodeToString(samlResponse.getBytes(StandardCharsets.UTF_8));
        }
        return samlResponse;
    }
}

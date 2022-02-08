/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import java.util.Base64;

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.external.datastorage.exception.TokenExpiredException;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.xml.util.XMLHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.util.SAMLUtil;
import org.springframework.stereotype.Service;

import com.epam.pipeline.external.datastorage.entity.PipelineToken;
import com.epam.pipeline.external.datastorage.exception.PipelineAuthenticationException;
import com.epam.pipeline.external.datastorage.security.UserContext;

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
            SAMLCredential credential = (SAMLCredential) SecurityContextHolder.getContext().getAuthentication()
                    .getCredentials();
            String token = getToken(credential);
            getUser().setToken(token);
            return token;
        }
    }

    public String getToken(final SAMLCredential credential) {
        try {
            final String samlToken = XMLHelper.nodeToString(
                SAMLUtil.marshallMessage(credential.getAuthenticationAssertion().getParent()));
            final String base64 = Base64.getEncoder().encodeToString(samlToken.getBytes());
            final PipelineToken response = apiExecutor.execute(authClient.getToken(base64));
            return response.getToken();
        } catch (MessageEncodingException e) {
            throw new PipelineAuthenticationException(e);
        }
    }
}

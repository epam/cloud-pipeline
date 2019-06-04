/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import com.epam.pipeline.external.datastorage.exception.TokenExpiredException;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.xml.util.XMLHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.util.SAMLUtil;
import org.springframework.stereotype.Service;

import com.epam.pipeline.external.datastorage.controller.Result;
import com.epam.pipeline.external.datastorage.controller.ResultStatus;
import com.epam.pipeline.external.datastorage.entity.PipelineToken;
import com.epam.pipeline.external.datastorage.exception.PipelineAuthenticationException;
import com.epam.pipeline.external.datastorage.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Service
public class PipelineAuthManager {
    public static final String UNAUTHORIZED_USER = "Unauthorized";

    private ObjectMapper objectMapper = new ObjectMapper();
    private PipelineAuthClient authClient;

    @Autowired
    public PipelineAuthManager(@Value("${pipeline.api.base.url}") String pipelineBaseUrl,
                               @Value("${pipeline.client.connect.timeout}") long connectTimeout,
                               @Value("${pipeline.client.read.timeout}") long readTimeout) {
        OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .hostnameVerifier((s, sslSession) -> true)
            .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(pipelineBaseUrl)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .client(client)
            .build();

        authClient = retrofit.create(PipelineAuthClient.class);
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

    public String getToken(SAMLCredential credential) {
        try {
            String samlToken = XMLHelper.nodeToString(
                SAMLUtil.marshallMessage(credential.getAuthenticationAssertion().getParent()));
            String base64 = Base64.getEncoder().encodeToString(samlToken.getBytes());
            Response<Result<PipelineToken>> response = authClient.getToken(base64).execute();

            if (response.isSuccessful() && response.body().getStatus() == ResultStatus.OK) {
                return response.body().getPayload().getToken();
            }

            if (!response.isSuccessful()) {
                throw new PipelineAuthenticationException(String.format("Unexpected status: %d, %s", response.code(),
                                                                        response.errorBody() != null ?
                                                                        response.errorBody().string() : ""));
            } else {
                throw new PipelineAuthenticationException(String.format("Unexpected status: %s, %s",
                                                                        response.body().getStatus(),
                                                                        response.body().getMessage()));
            }
        } catch (MessageEncodingException | IOException e) {
            throw new PipelineAuthenticationException(e);
        }
    }
}

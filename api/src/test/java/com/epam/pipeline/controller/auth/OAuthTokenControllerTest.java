/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.auth;

import com.epam.pipeline.manager.access.oidc.OidcClientNotAuthorizedException;
import com.epam.pipeline.manager.access.oidc.OidcExchangedToken;
import com.epam.pipeline.manager.access.oidc.OidcTokenExchangeManager;
import com.epam.pipeline.manager.access.oidc.OidcTokenVerificationException;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OAuthTokenController.class)
public class OAuthTokenControllerTest extends AbstractControllerTest {

    private static final String TOKEN_URL = SERVLET_PATH + "/oauth/token";
    private static final String GRANT_TYPE = "grant_type";
    private static final String SUBJECT_TOKEN = "subject_token";
    private static final String SUBJECT_TOKEN_TYPE = "subject_token_type";
    private static final String REQUESTED_TOKEN_TYPE = "requested_token_type";
    private static final String TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    private static final String JWT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt";
    private static final String PROVIDER_TOKEN = "provider-token";
    private static final String ISSUED_TOKEN = "issued-token";
    private static final String USER_NAME = "JANE";
    private static final String ERROR_PATH = "$.error";
    private static final String INVALID_REQUEST = "invalid_request";
    private static final long EXPIRES_IN = 900L;

    @MockBean
    private OidcTokenExchangeManager tokenExchangeManager;

    @Before
    public void prepareExchange() {
        Mockito.when(tokenExchangeManager.exchange(PROVIDER_TOKEN))
                .thenReturn(new OidcExchangedToken(ISSUED_TOKEN, EXPIRES_IN));
    }

    @Test
    @WithMockUser
    public void shouldReturnTheExchangeResponseUnwrapped() throws Exception {
        mvc().perform(exchangeRequest(ACCESS_TOKEN_TYPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(ISSUED_TOKEN))
                .andExpect(jsonPath("$.issued_token_type").value(JWT_TOKEN_TYPE))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value((int) EXPIRES_IN));
    }

    @Test
    @WithMockUser
    public void shouldNotLetACacheKeepTheToken() throws Exception {
        final MvcResult result = mvc().perform(exchangeRequest(ACCESS_TOKEN_TYPE)).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    @WithMockUser
    public void shouldAcceptASubjectTokenTypedAsJwt() throws Exception {
        mvc().perform(exchangeRequest(JWT_TOKEN_TYPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(ISSUED_TOKEN));
    }

    @Test
    @WithMockUser
    public void shouldAcceptAnExplicitlyRequestedJwt() throws Exception {
        mvc().perform(exchangeRequest(ACCESS_TOKEN_TYPE).param(REQUESTED_TOKEN_TYPE, JWT_TOKEN_TYPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(ISSUED_TOKEN));
    }

    @Test
    @WithMockUser
    public void shouldRefuseAnotherGrantType() throws Exception {
        mvc().perform(request()
                .param(GRANT_TYPE, "client_credentials")
                .param(SUBJECT_TOKEN, PROVIDER_TOKEN)
                .param(SUBJECT_TOKEN_TYPE, ACCESS_TOKEN_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_PATH).value("unsupported_grant_type"));
    }

    @Test
    @WithMockUser
    public void shouldRefuseAMissingGrantType() throws Exception {
        mvc().perform(request()
                .param(SUBJECT_TOKEN, PROVIDER_TOKEN)
                .param(SUBJECT_TOKEN_TYPE, ACCESS_TOKEN_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_PATH).value("unsupported_grant_type"));
    }

    @Test
    @WithMockUser
    public void shouldRefuseAMissingSubjectToken() throws Exception {
        mvc().perform(request()
                .param(GRANT_TYPE, TOKEN_EXCHANGE_GRANT)
                .param(SUBJECT_TOKEN_TYPE, ACCESS_TOKEN_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_PATH).value(INVALID_REQUEST));
    }

    @Test
    @WithMockUser
    public void shouldRefuseAnUnsupportedSubjectTokenType() throws Exception {
        mvc().perform(exchangeRequest("urn:ietf:params:oauth:token-type:saml2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_PATH).value(INVALID_REQUEST));
    }

    @Test
    @WithMockUser
    public void shouldRefuseAMissingSubjectTokenType() throws Exception {
        mvc().perform(request()
                .param(GRANT_TYPE, TOKEN_EXCHANGE_GRANT)
                .param(SUBJECT_TOKEN, PROVIDER_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_PATH).value(INVALID_REQUEST));
    }

    @Test
    @WithMockUser
    public void shouldRefuseAnUnsupportedRequestedTokenType() throws Exception {
        mvc().perform(exchangeRequest(ACCESS_TOKEN_TYPE)
                .param(REQUESTED_TOKEN_TYPE, "urn:ietf:params:oauth:token-type:refresh_token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_PATH).value(INVALID_REQUEST));
    }

    /**
     * The refusal message names a user and the configured claim, so it must not reach the caller.
     */
    @Test
    @WithMockUser
    public void shouldRefuseARejectedTokenWithoutSayingWhy() throws Exception {
        Mockito.when(tokenExchangeManager.exchange(PROVIDER_TOKEN))
                .thenThrow(new OidcTokenVerificationException(
                        "User " + USER_NAME + " named through client portal does not exist"));

        final MvcResult result = mvc().perform(exchangeRequest(ACCESS_TOKEN_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_PATH).value("invalid_grant"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(USER_NAME);
    }

    @Test
    @WithMockUser
    public void shouldRefuseAnUnauthorizedClient() throws Exception {
        Mockito.when(tokenExchangeManager.exchange(PROVIDER_TOKEN))
                .thenThrow(new OidcClientNotAuthorizedException(
                        "Client portal may not act as user " + USER_NAME));

        final MvcResult result = mvc().perform(exchangeRequest(ACCESS_TOKEN_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_PATH).value("unauthorized_client"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(USER_NAME);
    }

    /**
     * A fault of ours is not reported as a bad grant, and never reaches the generic handler, which
     * would answer with the exception's own message.
     */
    @Test
    @WithMockUser
    public void shouldReportOurOwnFailureAsAServerError() throws Exception {
        Mockito.when(tokenExchangeManager.exchange(PROVIDER_TOKEN))
                .thenThrow(new IllegalStateException("signing key is missing"));

        final MvcResult result = mvc().perform(exchangeRequest(ACCESS_TOKEN_TYPE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath(ERROR_PATH).value("server_error"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("signing key");
    }

    private static MockHttpServletRequestBuilder exchangeRequest(final String subjectTokenType) {
        return request()
                .param(GRANT_TYPE, TOKEN_EXCHANGE_GRANT)
                .param(SUBJECT_TOKEN, PROVIDER_TOKEN)
                .param(SUBJECT_TOKEN_TYPE, subjectTokenType);
    }

    private static MockHttpServletRequestBuilder request() {
        return post(TOKEN_URL)
                .servletPath(SERVLET_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
    }
}

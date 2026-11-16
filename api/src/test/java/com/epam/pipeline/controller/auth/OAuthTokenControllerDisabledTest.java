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

import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The exchange is off unless a deployment turns it on, so the route is absent by default rather
 * than present and refusing. No property is set here, which is the state of a deployment that has
 * not configured an identity provider.
 */
@WebMvcTest(controllers = OAuthTokenController.class)
public class OAuthTokenControllerDisabledTest extends AbstractControllerTest {

    private static final String TOKEN_URL = SERVLET_PATH + "/oauth/token";

    @Test
    @WithMockUser
    public void shouldNotServeTheEndpointUnlessItIsEnabled() throws Exception {
        mvc().perform(post(TOKEN_URL)
                .servletPath(SERVLET_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                .param("subject_token", "provider-token")
                .param("subject_token_type", "urn:ietf:params:oauth:token-type:access_token"))
                .andExpect(status().isNotFound());
    }
}

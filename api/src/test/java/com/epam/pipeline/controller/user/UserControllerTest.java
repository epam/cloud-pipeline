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

package com.epam.pipeline.controller.user;

import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserApiService;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
public class UserControllerTest extends AbstractControllerTest {

    private static final String USER_TOKEN_URL = SERVLET_PATH + "/user/token";
    private static final String EXPIRATION = "expiration";
    private static final String NAME = "name";
    private static final String USER_NAME = "JANE";
    private static final String TOKEN = "token";
    private static final long EXPIRATION_SECONDS = 60L;

    private final JwtRawToken token = new JwtRawToken(TOKEN);

    @Autowired
    private UserApiService mockUserApiService;

    @Autowired
    private AuthManager mockAuthManager;

    /**
     * The token of the current user is issued by the service, which refuses it for a token that
     * must not be renewed. Issued from the controller straight through AuthManager, it would skip
     * that check.
     */
    @Test
    @WithMockUser
    public void shouldIssueTheTokenOfTheCurrentUserThroughTheService() throws Exception {
        doReturn(token).when(mockUserApiService).issueTokenForCurrentUser(EXPIRATION_SECONDS);

        mvc().perform(tokenRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.token").value(TOKEN));

        verify(mockUserApiService).issueTokenForCurrentUser(EXPIRATION_SECONDS);
        verify(mockAuthManager, never()).issueTokenForCurrentUser(anyLong());
    }

    @Test
    @WithMockUser
    public void shouldIssueTheTokenOfANamedUserThroughTheService() throws Exception {
        doReturn(token).when(mockUserApiService).issueToken(USER_NAME, EXPIRATION_SECONDS);

        mvc().perform(tokenRequest().param(NAME, USER_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.token").value(TOKEN));

        verify(mockUserApiService).issueToken(eq(USER_NAME), eq(EXPIRATION_SECONDS));
        verify(mockUserApiService, never()).issueTokenForCurrentUser(any());
    }

    private static MockHttpServletRequestBuilder tokenRequest() {
        return get(USER_TOKEN_URL)
                .param(EXPIRATION, String.valueOf(EXPIRATION_SECONDS));
    }
}

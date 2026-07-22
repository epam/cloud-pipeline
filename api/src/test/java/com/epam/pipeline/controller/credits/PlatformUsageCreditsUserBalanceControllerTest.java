/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.credits;

import com.epam.pipeline.acl.credits.PlatformUsageCreditsUserBalanceApiService;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.BALANCE_PAGED_TYPE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.RESET_VALUE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.USER_ID;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.VOID_TYPE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.filterVO;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.pagedResult;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = PlatformUsageCreditsUserBalanceController.class)
public class PlatformUsageCreditsUserBalanceControllerTest extends AbstractControllerTest {

    private static final String BASE_URL = SERVLET_PATH + "/usage/credits/users";
    private static final String RESET_URL = BASE_URL + "/reset";

    @Autowired
    private PlatformUsageCreditsUserBalanceApiService mockApiService;

    @Test
    public void shouldFailFilterForUnauthorizedUser() {
        performUnauthorizedRequest(post(BASE_URL));
    }

    @Test
    @WithMockUser
    public void shouldFilter() throws Exception {
        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO();
        final PagedResult<List<PlatformUsageCreditsUserBalance>> expected = pagedResult();
        doReturn(expected).when(mockApiService).filter(filter);
        final String content = getObjectMapper().writeValueAsString(filter);

        final MvcResult result = performRequest(post(BASE_URL).content(content));

        verify(mockApiService).filter(filter);
        assertResponse(result, expected, BALANCE_PAGED_TYPE);
    }

    @Test
    public void shouldFailResetForUnauthorizedUser() {
        performUnauthorizedRequest(post(RESET_URL).param("value", String.valueOf(RESET_VALUE)));
    }

    @Test
    @WithMockUser
    public void shouldResetForSpecificUser() {
        final MvcResult result = performRequest(
                post(RESET_URL)
                        .param("value", String.valueOf(RESET_VALUE))
                        .param("userIds", String.valueOf(USER_ID)));

        verify(mockApiService).reset(RESET_VALUE, Collections.singletonList(USER_ID));
        assertResponse(result, null, VOID_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldResetForAllUsers() {
        final MvcResult result = performRequest(
                post(RESET_URL).param("value", String.valueOf(RESET_VALUE)));

        verify(mockApiService).reset(RESET_VALUE, null);
        assertResponse(result, null, VOID_TYPE);
    }
}

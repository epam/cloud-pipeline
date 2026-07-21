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

package com.epam.pipeline.acl.credits;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.manager.credits.PlatformUsageCreditsUserBalanceService;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.RESET_VALUE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.USER_ID;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.filterVO;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.pagedResult;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class PlatformUsageCreditsUserBalanceApiServiceTest extends AbstractAclTest {

    @Autowired
    private PlatformUsageCreditsUserBalanceApiService apiService;

    @Autowired
    private PlatformUsageCreditsUserBalanceService mockPlatformUsageCreditsUserBalanceService;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFilterForAdmin() {
        final PlatformUsageCreditsUserBalanceFilterVO filter = filterVO();
        final PagedResult<List<PlatformUsageCreditsUserBalance>> expected = pagedResult();
        doReturn(expected).when(mockPlatformUsageCreditsUserBalanceService).filter(filter);

        assertThat(apiService.filter(filter)).isEqualTo(expected);
        verify(mockPlatformUsageCreditsUserBalanceService).filter(filter);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyFilterForNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> apiService.filter(filterVO()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldResetForSpecificUserForAdmin() {
        apiService.reset(RESET_VALUE, USER_ID);

        verify(mockPlatformUsageCreditsUserBalanceService).reset(RESET_VALUE, USER_ID);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldResetForAllUsersForAdmin() {
        apiService.reset(RESET_VALUE, null);

        verify(mockPlatformUsageCreditsUserBalanceService).reset(RESET_VALUE, null);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyResetForNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> apiService.reset(RESET_VALUE, USER_ID));
    }
}

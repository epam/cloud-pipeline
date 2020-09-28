/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.log;

import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.manager.log.LogManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class LogApiServiceTest extends AbstractAclTest {

    @Autowired
    private LogApiService logApiService;

    @Autowired
    private LogManager mockLogManager;

    private final LogPagination logPagination = LogPagination.builder().build();

    private final LogFilter logFilter = new LogFilter();

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAllowAccessToLogPaginationForAdmin() {
        doReturn(logPagination).when(mockLogManager).filter(logFilter);

        assertThat(logApiService.filter(logFilter)).isEqualTo(logPagination);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyAccessToLogPaginationForNotAdmin() {
        doReturn(logPagination).when(mockLogManager).filter(logFilter);

        logApiService.filter(logFilter);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAllowAccessToLogFilterForAdmin() {
        when(mockLogManager.getFilters()).thenReturn(logFilter);

        assertThat(logApiService.getFilters()).isEqualTo(logFilter);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyAccessToLogFilterForNotAdmin() {
        when(mockLogManager.getFilters()).thenReturn(logFilter);

        logApiService.getFilters();
    }
}

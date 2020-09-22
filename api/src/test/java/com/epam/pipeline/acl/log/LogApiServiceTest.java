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

package com.epam.pipeline.acl.log;

import com.epam.pipeline.acl.AbstractAclTest;
import com.epam.pipeline.acl.AclTestBeans;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.manager.log.LogManager;
import com.epam.pipeline.manager.security.AuthManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {LogApiService.class, AclTestBeans.class})
public class LogApiServiceTest extends AbstractAclTest {

    private static final String UNAUTHORIZED = "Unauthorized";
    private static final String ADMIN_ROLE = "ADMIN";

    @Autowired
    private LogApiService logApiService;

    @Autowired
    private LogManager logManager;

    @Autowired
    private LogPagination logPagination;

    @Autowired
    private AuthManager mockAuthManager;

    private LogFilter logFilter = new LogFilter();

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAllowAccessToLogPagination() {
        doReturn(ADMIN_ROLE).when(mockAuthManager).getAuthorizedUser();
        when(logManager.filter(logFilter)).thenReturn(logPagination);

        assertThat(logApiService.filter(logFilter)).isEqualTo(logPagination);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = UNAUTHORIZED)
    public void shouldDenyAccessToLogPagination() {
        doReturn(UNAUTHORIZED).when(mockAuthManager).getAuthorizedUser();
        when(logManager.filter(logFilter)).thenReturn(logPagination);

        logApiService.filter(logFilter);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAllowAccessToLogFilter() {
        doReturn(ADMIN_ROLE).when(mockAuthManager).getAuthorizedUser();
        when(logManager.getFilters()).thenReturn(logFilter);

        assertThat(logApiService.getFilters()).isEqualTo(logFilter);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = UNAUTHORIZED)
    public void shouldDenyAccessToLogFilter() {
        doReturn(UNAUTHORIZED).when(mockAuthManager).getAuthorizedUser();
        when(logManager.getFilters()).thenReturn(logFilter);

        logApiService.getFilters();
    }
}
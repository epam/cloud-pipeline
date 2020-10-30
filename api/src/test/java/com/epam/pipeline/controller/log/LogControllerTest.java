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

package com.epam.pipeline.controller.log;

import com.epam.pipeline.acl.log.LogApiService;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = LogController.class)
public class LogControllerTest extends AbstractControllerTest {

    @Autowired
    private LogApiService mockLogApiService;

    public static final String TEST_MESSAGE = "testMessage";
    private static final String LOG_ENDPOINT = SERVLET_PATH + "/log/filter";
    private final LogFilter logFilter = new LogFilter();

    @Test
    public void shouldFailForUnauthorizedUserGet() throws Exception {
        performUnauthorizedRequest(get(LOG_ENDPOINT));
    }

    @Test
    @WithMockUser
    public void shouldReturnLogFilter() throws Exception {
        logFilter.setSortOrder("ASC");
        Mockito.doReturn(logFilter).when(mockLogApiService).getFilters();
        final MvcResult mvcResult = performRequest(get(LOG_ENDPOINT));

        Mockito.verify(mockLogApiService).getFilters();

        assertResponse(mvcResult, logFilter, new TypeReference<Result<LogFilter>>() { });
    }

    @Test
    public void shouldFailForUnauthorizedUserPost() throws Exception {
        performUnauthorizedRequest(post(LOG_ENDPOINT));
    }

    @Test
    @WithMockUser
    public void shouldReturnFilteredLogs() throws Exception {
        final LogPagination logPagination = LogPagination.builder().pageSize(5).build();
        logFilter.setMessage(TEST_MESSAGE);
        Mockito.doReturn(logPagination).when(mockLogApiService).filter(logFilter);
        final String content = getObjectMapper().writeValueAsString(logFilter);

        final MvcResult mvcResult = performRequest(post(LOG_ENDPOINT).content(content));

        Mockito.verify(mockLogApiService).filter(logFilter);

        assertResponse(mvcResult, logPagination, new TypeReference<Result<LogPagination>>() { });
    }
}

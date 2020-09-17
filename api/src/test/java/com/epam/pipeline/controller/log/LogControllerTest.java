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

import com.epam.pipeline.controller.AbstractControllerTest;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.manager.log.LogApiService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LogController.class)
@Import(LogController.class)
public class LogControllerTest extends AbstractControllerTest {

    @MockBean
    private LogApiService logApiService;

    @Test
    public void testFilterGet() throws Exception {
        LogFilter logFilter = new LogFilter();
        MvcResult mvcResult = mvc().perform(get("/log/filter")
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(logFilter)))
                .andExpect(status().isOk()).andReturn();

        verify(logApiService, times(1)).getFilters();

        String actual = mvcResult.getResponse().getContentAsString();
        assertThat(actual).isNotBlank();
    }

    @Test
    public void testFilterPost() throws Exception {
        LogFilter logFilter = new LogFilter();
        logFilter.setMessage("testMessage");
        MvcResult mvcResult = mvc().perform(post("/log/filter")
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(logFilter)))
                .andExpect(status().isOk()).andReturn();

        ArgumentCaptor<LogFilter> logFilterCaptor = ArgumentCaptor.forClass(LogFilter.class);
        verify(logApiService, times(1)).filter(logFilterCaptor.capture());
        assertThat(logFilterCaptor.getValue().getMessage()).isEqualTo("testMessage");

        String actual = mvcResult.getResponse().getContentAsString();
        assertThat(actual).isNotBlank();
    }
}

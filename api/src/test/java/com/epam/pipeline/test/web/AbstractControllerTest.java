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

package com.epam.pipeline.test.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.ResponseResult;
import com.epam.pipeline.controller.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@WebTestConfiguration
public abstract class AbstractControllerTest {
    protected static final String EXPECTED_CONTENT_TYPE = "application/json;charset=UTF-8";
    protected static final String SERVLET_PATH = "/restapi";

    private MockMvc mockMvc;
    private ObjectMapper deserializationMapper;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    protected WebApplicationContext wac;

    @Before
    public void setup() throws Exception {
        // checks that all required dependencies are provided.
        assertNotNull("WebApplicationContext isn't provided.", wac);
        assertNotNull("ObjectMapper isn't provided.", objectMapper);

        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity()).build();
        deserializationMapper = JsonMapper.newInstance().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    protected final MockMvc mvc() {
        return mockMvc;
    }

    protected final JsonMapper getObjectMapper() {
        return objectMapper;
    }

    public <T> ResponseResult<T> buildExpectedResult(final T payload) {
        final ResponseResult<T> expectedResult = new ResponseResult<>();
        expectedResult.setStatus("OK");
        expectedResult.setPayload(payload);
        return expectedResult;
    }

    public <T> void assertResponse(final MvcResult mvcResult,
                                   final T payload,
                                   final TypeReference<Result<T>> typeReference) throws Exception {
        final ResponseResult<T> expectedResult = buildExpectedResult(payload);

        final String actual = mvcResult.getResponse().getContentAsString();
        assertTrue(StringUtils.isNotBlank(actual));
        assertThat(actual).isEqualToIgnoringWhitespace(objectMapper.writeValueAsString(expectedResult));

        final Result<T> actualResult = JsonMapper.parseData(actual, typeReference, deserializationMapper);
        assertNotNull(actualResult);
        assertEquals(expectedResult.getPayload(), actualResult.getPayload());
    }

    public void performUnauthorizedRequest(final MockHttpServletRequestBuilder requestBuilder) throws Exception {
        mockMvc.perform(requestBuilder
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    public MvcResult performRequest(final MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return performRequest(requestBuilder, EXPECTED_CONTENT_TYPE);
    }

    public MvcResult performRequest(final MockHttpServletRequestBuilder requestBuilder, String contentType)
            throws Exception {
        return mockMvc.perform(requestBuilder
                .servletPath(SERVLET_PATH)
                .contentType(contentType))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(contentType))
                .andReturn();
    }
}

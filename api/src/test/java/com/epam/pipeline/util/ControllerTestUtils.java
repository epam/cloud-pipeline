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

package com.epam.pipeline.util;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.ResponseResult;
import com.epam.pipeline.controller.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ControllerTestUtils {

    private static final String SERVLET_PATH = "/restapi";
    private static final String CONTENT_TYPE = "application/json;charset=UTF-8";

    @Autowired
    private JsonMapper jsonMapper;

    public <T> ResponseResult<T> buildExpectedResult(final T payload) {
        final ResponseResult<T> expectedResult = new ResponseResult<>();
        expectedResult.setStatus("OK");
        expectedResult.setPayload(payload);
        return expectedResult;
    }

    public <T> void assertResponse(final MvcResult mvcResult,
                                          final JsonMapper objectMapper,
                                          final ResponseResult<T> expectedResult,
                                          final TypeReference<Result<T>> typeReference) throws Exception {
        final String actual = mvcResult.getResponse().getContentAsString();
        Assert.assertTrue(StringUtils.isNotBlank(actual));
        assertThat(actual).isEqualToIgnoringWhitespace(objectMapper.writeValueAsString(expectedResult));

        final Result<T> actualResult = JsonMapper.parseData(actual, typeReference);
        Assert.assertEquals(expectedResult.getPayload(), actualResult.getPayload());
    }

    public <T> void assertResponse(final MvcResult mvcResult,
                                          final T payload,
                                          final TypeReference<Result<T>> typeReference) throws Exception {
        final ResponseResult<T> expectedResult = buildExpectedResult(payload);

        final String actual = mvcResult.getResponse().getContentAsString();
        Assert.assertTrue(StringUtils.isNotBlank(actual));
        assertThat(actual).isEqualToIgnoringWhitespace(jsonMapper.writeValueAsString(expectedResult));

        final Result<T> actualResult = JsonMapper.parseData(actual, typeReference);
        Assert.assertEquals(expectedResult.getPayload(), actualResult.getPayload());
    }

    public void getRequestUnauthorized(MockMvc mvc, String url) throws Exception {
        mvc.perform(get(url)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    public MvcResult getRequest(MockMvc mvc, String url, MultiValueMap<String, String> params, String content)
            throws Exception {
        return mvc.perform(get(url)
                .servletPath(SERVLET_PATH)
                .contentType(CONTENT_TYPE)
                .params(params)
                .content(content))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(CONTENT_TYPE))
                .andReturn();
    }

    public void postRequestUnauthorized(MockMvc mvc, String url) throws Exception {
        mvc.perform(post(url)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    public MvcResult postRequest(MockMvc mvc, String url, MultiValueMap<String, String> params, String content)
            throws Exception {
        return mvc.perform(post(url)
                .servletPath(SERVLET_PATH)
                .contentType(CONTENT_TYPE)
                .params(params)
                .content(content))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(CONTENT_TYPE))
                .andReturn();
    }

    public void putRequestUnauthorized(MockMvc mvc, String url) throws Exception {
        mvc.perform(put(url)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    public MvcResult putRequest(MockMvc mvc, String url, MultiValueMap<String, String> params, String content)
            throws Exception {
        return mvc.perform(put(url)
                .servletPath(SERVLET_PATH)
                .contentType(CONTENT_TYPE)
                .params(params)
                .content(content))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(CONTENT_TYPE))
                .andReturn();
    }

    public void deleteRequestUnauthorized(MockMvc mvc, String url) throws Exception {
        mvc.perform(delete(url)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    public MvcResult deleteRequest(MockMvc mvc, String url, MultiValueMap<String, String> params, String content)
            throws Exception {
        return mvc.perform(delete(url)
                .servletPath(SERVLET_PATH)
                .contentType(CONTENT_TYPE)
                .params(params)
                .content(content))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(CONTENT_TYPE))
                .andReturn();
    }
}

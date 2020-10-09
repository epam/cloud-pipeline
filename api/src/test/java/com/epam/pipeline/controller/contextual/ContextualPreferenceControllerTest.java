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

package com.epam.pipeline.controller.contextual;

import com.epam.pipeline.controller.ResponseResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.contextual.ContextualPreferenceSearchRequest;
import com.epam.pipeline.manager.contextual.ContextualPreferenceApiService;
import com.epam.pipeline.test.creator.contextual.ContextualPreferenceCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.epam.pipeline.util.ControllerTestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ContextualPreferenceController.class)
public class ContextualPreferenceControllerTest extends AbstractControllerTest {

    private static final String TEST_STRING = "TEST";
    private static final String CONTEXTUAL_URL = SERVLET_PATH + "/contextual/preference";
    private static final String LOAD_URL = CONTEXTUAL_URL + "/load";
    private static final String LOAD_ALL_URL = LOAD_URL + "/all";
    private static final ContextualPreferenceLevel PREFERENCE_LEVEL = ContextualPreferenceLevel.ROLE;
    private ContextualPreference contextualPreference;
    private ResponseResult<ContextualPreference> expectedResult;
    private ContextualPreferenceExternalResource contextualPreferenceExternalResource;

    @Autowired
    private ContextualPreferenceApiService mockContextualPreferenceApiService;

    @Before
    public void setUp() {
        contextualPreference = ContextualPreferenceCreatorUtils.getContextualPreference();
        expectedResult = ControllerTestUtils.buildExpectedResult(contextualPreference);

        contextualPreferenceExternalResource
                = ContextualPreferenceCreatorUtils.getCPExternalResource();
    }

    @Test
    public void shouldFailLoadAllForUnauthorizedUser() throws Exception {
        mvc().perform(get(LOAD_ALL_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadAll() throws Exception {
        final List<ContextualPreference> contextualPreferences = Collections.singletonList(contextualPreference);

        Mockito.doReturn(contextualPreferences).when(mockContextualPreferenceApiService).loadAll();

        final MvcResult mvcResult = mvc().perform(get(LOAD_ALL_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockContextualPreferenceApiService).loadAll();

        final ResponseResult<List<ContextualPreference>> expectedResult =
                ControllerTestUtils.buildExpectedResult(contextualPreferences);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<ContextualPreference>>>() { });
    }

    @Test
    public void shouldFailLoadForUnauthorizedUser() throws Exception {
        mvc().perform(get(LOAD_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoad() throws Exception {
        Mockito.doReturn(contextualPreference).when(mockContextualPreferenceApiService)
                .load(TEST_STRING, contextualPreferenceExternalResource);

        final MvcResult mvcResult = mvc().perform(get(LOAD_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .param("name", TEST_STRING)
                .param("level", PREFERENCE_LEVEL.toString())
                .param("resourceId", TEST_STRING))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockContextualPreferenceApiService).load(TEST_STRING, contextualPreferenceExternalResource);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<ContextualPreference>>() { });
    }

    @Test
    public void shouldFailSearchForUnauthorizedUser() throws Exception {
        mvc().perform(post(CONTEXTUAL_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldSearch() throws Exception {
        final ContextualPreferenceSearchRequest searchRequest = ContextualPreferenceCreatorUtils.getCPSearchRequest();

        Mockito.doReturn(contextualPreference).when(mockContextualPreferenceApiService)
                .search(searchRequest.getPreferences(), searchRequest.getResource());

        final MvcResult mvcResult = mvc().perform(post(CONTEXTUAL_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockContextualPreferenceApiService)
                .search(searchRequest.getPreferences(), searchRequest.getResource());

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<ContextualPreference>>() { });
    }

    @Test
    public void shouldFailUpdateForUnauthorizedUser() throws Exception {
        mvc().perform(put(CONTEXTUAL_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldUpdate() throws Exception {
        final ContextualPreferenceVO contextualPreferenceVO =
                ContextualPreferenceCreatorUtils.getContextualPreferenceVO();

        Mockito.doReturn(contextualPreference).when(mockContextualPreferenceApiService).upsert(contextualPreferenceVO);

        final MvcResult mvcResult = mvc().perform(put(CONTEXTUAL_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(contextualPreferenceVO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockContextualPreferenceApiService).upsert(contextualPreferenceVO);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<ContextualPreference>>() { });
    }

    @Test
    public void shouldFailDeleteForUnauthorizedUser() throws Exception {
        mvc().perform(MockMvcRequestBuilders.delete(CONTEXTUAL_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldDelete() throws Exception {
        Mockito.doReturn(contextualPreference).when(mockContextualPreferenceApiService)
                .delete(TEST_STRING, contextualPreferenceExternalResource);

        final MvcResult mvcResult = mvc().perform(MockMvcRequestBuilders.delete(CONTEXTUAL_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .param("name", TEST_STRING)
                .param("level", PREFERENCE_LEVEL.toString())
                .param("resourceId", TEST_STRING))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockContextualPreferenceApiService).delete(TEST_STRING, contextualPreferenceExternalResource);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<ContextualPreference>>() { });
    }
}

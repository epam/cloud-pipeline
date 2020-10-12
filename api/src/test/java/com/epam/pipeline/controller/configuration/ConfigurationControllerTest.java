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

package com.epam.pipeline.controller.configuration;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.ResponseResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.manager.configuration.RunConfigurationApiService;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.epam.pipeline.util.ControllerTestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConfigurationController.class)
public class ConfigurationControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String CONFIGURATION_URL = SERVLET_PATH + "/configuration";
    private static final String CONFIGURATION_BY_ID_URL = CONFIGURATION_URL + "/%d";
    private static final String ALL_CONFIGURATIONS_URL = CONFIGURATION_URL + "/loadAll";
    private JsonMapper mapper;
    private RunConfiguration runConfiguration;
    private RunConfigurationVO runConfigurationVO;
    private ResponseResult<RunConfiguration> expectedResult;

    @Autowired
    private RunConfigurationApiService mockRunConfigurationApiService;

    @Before
    public void setUp() {
        mapper = getObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        runConfigurationVO = ConfigurationCreatorUtils.getRunConfigurationVO();
        runConfiguration = ConfigurationCreatorUtils.getRunConfiguration();

        expectedResult = ControllerTestUtils.buildExpectedResult(runConfiguration);
    }

    @Test
    public void shouldFailSaveConfigurationForUnauthorizedUser() throws Exception {
        mvc().perform(post(CONFIGURATION_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldSaveConfiguration() throws Exception {
        Mockito.doReturn(runConfiguration).when(mockRunConfigurationApiService).save(Mockito.refEq(runConfigurationVO));

        final MvcResult mvcResult = mvc().perform(post(CONFIGURATION_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(runConfigurationVO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockRunConfigurationApiService).save(Mockito.refEq(runConfigurationVO));

        ControllerTestUtils.assertResponse(mvcResult, mapper, expectedResult,
                new TypeReference<Result<RunConfiguration>>() { });
    }

    @Test
    @WithMockUser
    public void shouldUpdateConfiguration() throws Exception {
        runConfigurationVO.setId(ID);

        Mockito.doReturn(runConfiguration).when(mockRunConfigurationApiService)
                .update(Mockito.refEq(runConfigurationVO));

        final MvcResult mvcResult = mvc().perform(post(CONFIGURATION_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(runConfigurationVO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockRunConfigurationApiService).update(Mockito.refEq(runConfigurationVO));

        ControllerTestUtils.assertResponse(mvcResult, mapper, expectedResult,
                new TypeReference<Result<RunConfiguration>>() { });
    }

    @Test
    public void shouldFailDeleteConfigurationForUnauthorizedUser() throws Exception {
        mvc().perform(delete(String.format(CONFIGURATION_BY_ID_URL, ID))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldDeleteConfiguration() throws Exception {
        Mockito.doReturn(runConfiguration).when(mockRunConfigurationApiService).delete(ID);

        final MvcResult mvcResult = mvc().perform(delete(String.format(CONFIGURATION_BY_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockRunConfigurationApiService).delete(ID);

        ControllerTestUtils.assertResponse(mvcResult, mapper, expectedResult,
                new TypeReference<Result<RunConfiguration>>() { });
    }

    @Test
    public void shouldFailLoadConfigurationForUnauthorizedUser() throws Exception {
        mvc().perform(get(String.format(CONFIGURATION_BY_ID_URL, ID))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadConfiguration() throws Exception {
        Mockito.doReturn(runConfiguration).when(mockRunConfigurationApiService).load(ID);

        final MvcResult mvcResult = mvc().perform(get(String.format(CONFIGURATION_BY_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockRunConfigurationApiService).load(ID);

        ControllerTestUtils.assertResponse(mvcResult, mapper, expectedResult,
                new TypeReference<Result<RunConfiguration>>() { });
    }

    @Test
    public void shouldFailLoadAllConfigurationsForUnauthorizedUser() throws Exception {
        mvc().perform(get(ALL_CONFIGURATIONS_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadAllConfigurations() throws Exception {
        final List<RunConfiguration> runConfigurations = Collections.singletonList(runConfiguration);
        Mockito.doReturn(runConfigurations).when(mockRunConfigurationApiService).loadAll();

        final MvcResult mvcResult = mvc().perform(get(ALL_CONFIGURATIONS_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockRunConfigurationApiService).loadAll();

        final ResponseResult<List<RunConfiguration>> expectedResult
                = ControllerTestUtils.buildExpectedResult(runConfigurations);

        ControllerTestUtils.assertResponse(mvcResult, mapper, expectedResult,
                new TypeReference<Result<List<RunConfiguration>>>() { });
    }
}

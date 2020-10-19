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
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.manager.configuration.RunConfigurationApiService;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
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

@WebMvcTest(controllers = ConfigurationController.class)
public class ConfigurationControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String CONFIGURATION_URL = SERVLET_PATH + "/configuration";
    private static final String CONFIGURATION_BY_ID_URL = CONFIGURATION_URL + "/%d";
    private static final String ALL_CONFIGURATIONS_URL = CONFIGURATION_URL + "/loadAll";
    private JsonMapper mapper;
    private final RunConfiguration runConfiguration = ConfigurationCreatorUtils.getRunConfiguration();
    private final RunConfigurationVO runConfigurationVO = ConfigurationCreatorUtils.getRunConfigurationVO();

    @Autowired
    private RunConfigurationApiService mockRunConfigurationApiService;

    @Before
    public void setUp() {
        mapper = getObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Test
    public void shouldFailSaveConfigurationForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(CONFIGURATION_URL));
    }

    @Test
    @WithMockUser
    public void shouldSaveConfiguration() throws Exception {
        final String content = getObjectMapper().writeValueAsString(runConfigurationVO);
        Mockito.doReturn(runConfiguration).when(mockRunConfigurationApiService).save(Mockito.refEq(runConfigurationVO));

        final MvcResult mvcResult = performRequest(post(CONFIGURATION_URL).content(content));

        Mockito.verify(mockRunConfigurationApiService).save(Mockito.refEq(runConfigurationVO));
        assertResponse(mvcResult, mapper, runConfiguration, ConfigurationCreatorUtils.RUN_CONFIGURATION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateConfiguration() throws Exception {
        runConfigurationVO.setId(ID);
        final String content = getObjectMapper().writeValueAsString(runConfigurationVO);
        Mockito.doReturn(runConfiguration).when(mockRunConfigurationApiService)
                .update(Mockito.refEq(runConfigurationVO));

        final MvcResult mvcResult = performRequest(post(CONFIGURATION_URL).content(content));

        Mockito.verify(mockRunConfigurationApiService).update(Mockito.refEq(runConfigurationVO));
        assertResponse(mvcResult, mapper, runConfiguration, ConfigurationCreatorUtils.RUN_CONFIGURATION_TYPE);
    }

    @Test
    public void shouldFailDeleteConfigurationForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(delete(String.format(CONFIGURATION_BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteConfiguration() throws Exception {
        Mockito.doReturn(runConfiguration).when(mockRunConfigurationApiService).delete(ID);
        final MvcResult mvcResult = performRequest(delete(String.format(CONFIGURATION_BY_ID_URL, ID)));

        Mockito.verify(mockRunConfigurationApiService).delete(ID);
        assertResponse(mvcResult, mapper, runConfiguration, ConfigurationCreatorUtils.RUN_CONFIGURATION_TYPE);
    }

    @Test
    public void shouldFailLoadConfigurationForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(CONFIGURATION_BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadConfiguration() throws Exception {
        Mockito.doReturn(runConfiguration).when(mockRunConfigurationApiService).load(ID);
        final MvcResult mvcResult = performRequest(get(String.format(CONFIGURATION_BY_ID_URL, ID)));

        Mockito.verify(mockRunConfigurationApiService).load(ID);
        assertResponse(mvcResult, mapper, runConfiguration, ConfigurationCreatorUtils.RUN_CONFIGURATION_TYPE);
    }

    @Test
    public void shouldFailLoadAllConfigurationsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(ALL_CONFIGURATIONS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllConfigurations() throws Exception {
        final List<RunConfiguration> runConfigurations = Collections.singletonList(runConfiguration);
        Mockito.doReturn(runConfigurations).when(mockRunConfigurationApiService).loadAll();

        final MvcResult mvcResult = performRequest(get(ALL_CONFIGURATIONS_URL));

        Mockito.verify(mockRunConfigurationApiService).loadAll();
        assertResponse(mvcResult, mapper, runConfigurations, ConfigurationCreatorUtils.RUN_CONFIGURATION_LIST_TYPE);
    }
}

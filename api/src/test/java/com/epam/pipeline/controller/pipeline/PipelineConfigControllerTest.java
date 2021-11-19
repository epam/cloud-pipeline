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

package com.epam.pipeline.controller.pipeline;

import com.epam.pipeline.acl.pipeline.PipelineConfigApiService;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class PipelineConfigControllerTest extends AbstractControllerTest {

    private static final String PIPELINE_URL = SERVLET_PATH + "/pipeline";
    private static final String PIPELINE_ID_URL = PIPELINE_URL + "/%d";
    private static final String PIPELINE_ID_CONFIGURATIONS_URL = PIPELINE_ID_URL + "/configurations";
    private static final String PIPELINE_ID_CONFIGURATIONS_RENAME_URL = PIPELINE_ID_CONFIGURATIONS_URL + "/rename";
    private static final String PIPELINE_ID_PARAMETERS_URL = PIPELINE_ID_URL + "/parameters";
    private static final String PIPELINE_ID_LANGUAGE_URL = PIPELINE_ID_URL + "/language";

    private static final String VERSION = "version";
    private static final String OLD_NAME = "oldName";
    private static final String NEW_NAME = "newName";
    private static final String CONFIG_NAME = "configName";
    private static final String NAME = "name";

    private final ConfigurationEntry configurationEntry = ConfigurationCreatorUtils.getConfigurationEntry();
    private final PipelineConfiguration pipelineConfiguration = ConfigurationCreatorUtils.getPipelineConfiguration();
    private final List<ConfigurationEntry> configurationEntries = Collections.singletonList(configurationEntry);

    @Autowired
    private PipelineConfigApiService mockConfigApiService;

    @Test
    @WithMockUser
    public void shouldGetPipelineConfigurations() throws Exception {
        doReturn(configurationEntries).when(mockConfigApiService).loadConfigurations(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_CONFIGURATIONS_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING)));

        verify(mockConfigApiService).loadConfigurations(ID, TEST_STRING);
        assertResponse(mvcResult, configurationEntries, ConfigurationCreatorUtils.CONFIGURATION_ENTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailGetPipelineConfigurationsForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_CONFIGURATIONS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreatePipelineConfiguration() throws Exception {
        doReturn(configurationEntries).when(mockConfigApiService).addConfiguration(ID, configurationEntry);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_CONFIGURATIONS_URL, ID))
                .content(stringOf(configurationEntry)));

        verify(mockConfigApiService).addConfiguration(ID, configurationEntry);
        assertResponse(mvcResult, configurationEntries, ConfigurationCreatorUtils.CONFIGURATION_ENTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailCreatePipelineConfigurationForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_CONFIGURATIONS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldRenamePipelineConfiguration() throws Exception {
        doReturn(configurationEntries).when(mockConfigApiService).renameConfiguration(ID, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_CONFIGURATIONS_RENAME_URL, ID))
                .params(multiValueMapOf(OLD_NAME, TEST_STRING,
                                        NEW_NAME, TEST_STRING)));

        verify(mockConfigApiService).renameConfiguration(ID, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, configurationEntries, ConfigurationCreatorUtils.CONFIGURATION_ENTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailRenamePipelineConfigurationForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_CONFIGURATIONS_RENAME_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeletePipelineConfiguration() throws Exception {
        doReturn(configurationEntries).when(mockConfigApiService).deleteConfiguration(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(String.format(PIPELINE_ID_CONFIGURATIONS_URL, ID))
                .params(multiValueMapOf(CONFIG_NAME, TEST_STRING)));

        verify(mockConfigApiService).deleteConfiguration(ID, TEST_STRING);
        assertResponse(mvcResult, configurationEntries, ConfigurationCreatorUtils.CONFIGURATION_ENTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailDeletePipelineConfigurationForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(PIPELINE_ID_CONFIGURATIONS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineParameters() throws Exception {
        doReturn(pipelineConfiguration).when(mockConfigApiService)
                .loadParametersFromScript(ID, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_PARAMETERS_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING,
                                        NAME, TEST_STRING)));

        verify(mockConfigApiService).loadParametersFromScript(ID, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, pipelineConfiguration, ConfigurationCreatorUtils.PIPELINE_CONFIGURATION_TYPE);
    }

    @Test
    public void shouldFailGetPipelineParametersForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_PARAMETERS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineLanguage() throws Exception {
        doReturn(pipelineConfiguration).when(mockConfigApiService)
                .loadParametersFromScript(ID, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_LANGUAGE_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING,
                                        NAME, TEST_STRING)));

        verify(mockConfigApiService).loadParametersFromScript(ID, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, TEST_STRING, CommonCreatorConstants.STRING_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailGetPipelineLanguageForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_LANGUAGE_URL, ID)));
    }
}

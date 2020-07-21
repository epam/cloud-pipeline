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

package com.epam.pipeline.manager.configuration;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationWithEntitiesVO;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.StopServerlessRunManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ServerlessConfigurationManagerTest {

    private static final String TEST_NAME = "test";
    private static final String ANOTHER_TEST_NAME = "another";
    private static final String TEST_URL = "https://external.api/";
    private static final String TEST_QUERY = "query=1";
    private static final String TEST_APP_PATH = "app/path";
    private static final Long PIPELINE_ID = 1L;
    private static final Long RUN_ID = 2L;
    private static final Long CONFIGURATION_ID = 3L;

    private final RunConfigurationManager runConfigurationManager = mock(RunConfigurationManager.class);
    private final ConfigurationRunner configurationRunner = mock(ConfigurationRunner.class);
    private final AbstractRunConfigurationMapper runConfigurationMapper = mock(AbstractRunConfigurationMapper.class);
    private final PipelineRunManager runManager = mock(PipelineRunManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final StopServerlessRunManager stopServerlessRunManager = mock(StopServerlessRunManager.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final ServerlessConfigurationManager serverlessConfigurationManager =
            spy(new ServerlessConfigurationManager(
                    runConfigurationManager,
                    configurationRunner,
                    runConfigurationMapper,
                    runManager,
                    preferenceManager,
                    stopServerlessRunManager,
                    new JsonMapper(),
                    authManager));

    @Test
    public void shouldRunServerlessConfiguration() {
        final RunConfigurationEntry entry = runConfigurationEntry(TEST_NAME, true);
        final RunConfiguration configuration = runConfiguration(TEST_NAME, entry);
        final RunConfigurationWithEntitiesVO runConfigurationVO = runConfigurationWithEntitiesVO(TEST_NAME, entry);
        final PipelineRun pipelineRun = pipelineRun();
        pipelineRun.setServiceUrl(String.format("[{\"url\": \"%s\"}]", TEST_URL));
        final PagedResult<List<PipelineRun>> activeRuns = new PagedResult<>(Collections.emptyList(), 0);

        when(runConfigurationManager.load(any())).thenReturn(configuration);
        when(runConfigurationMapper.toRunConfigurationWithEntitiesVO(any())).thenReturn(runConfigurationVO);
        when(configurationRunner.runConfiguration(any(), any(), any()))
                .thenReturn(Collections.singletonList(pipelineRun));
        when(runManager.searchPipelineRuns(any(), anyBoolean())).thenReturn(activeRuns);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_WAIT_COUNT)).thenReturn(1);
        when(runManager.loadPipelineRun(any())).thenReturn(pipelineRun);
        doReturn(StringUtils.EMPTY).when(serverlessConfigurationManager).sendRequest(any(), any());
        when(stopServerlessRunManager.loadByRunId(any())).thenReturn(Optional.empty());

        serverlessConfigurationManager.run(CONFIGURATION_ID, TEST_NAME, mockRequest());
        verifyEndpoint();
        verify(configurationRunner).runConfiguration(any(), any(), any());
        verify(stopServerlessRunManager).createServerlessRun(any());
        verify(stopServerlessRunManager).updateServerlessRun(any());
    }

    @Test
    public void shouldRunServerlessConfigurationWithEndpointName() {
        final RunConfigurationEntry entry = runConfigurationEntry(TEST_NAME, true);
        entry.setEndpointName(TEST_NAME);
        final RunConfiguration configuration = runConfiguration(TEST_NAME, entry);
        final RunConfigurationWithEntitiesVO runConfigurationVO = runConfigurationWithEntitiesVO(TEST_NAME, entry);
        final PipelineRun pipelineRun = pipelineRun();
        pipelineRun.setServiceUrl(String.format("[{\"url\": \"%s\", \"name\": \"%s\"}]", TEST_URL, TEST_NAME));
        final PagedResult<List<PipelineRun>> activeRuns = new PagedResult<>(Collections.emptyList(), 0);

        when(runConfigurationManager.load(any())).thenReturn(configuration);
        when(runConfigurationMapper.toRunConfigurationWithEntitiesVO(any())).thenReturn(runConfigurationVO);
        when(configurationRunner.runConfiguration(any(), any(), any()))
                .thenReturn(Collections.singletonList(pipelineRun));
        when(runManager.searchPipelineRuns(any(), anyBoolean())).thenReturn(activeRuns);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_WAIT_COUNT)).thenReturn(1);
        when(runManager.loadPipelineRun(any())).thenReturn(pipelineRun);
        doReturn(StringUtils.EMPTY).when(serverlessConfigurationManager).sendRequest(any(), any());
        when(stopServerlessRunManager.loadByRunId(any())).thenReturn(Optional.empty());

        serverlessConfigurationManager.run(CONFIGURATION_ID, TEST_NAME, mockRequest());
        verifyEndpoint();
        verify(configurationRunner).runConfiguration(any(), any(), any());
        verify(stopServerlessRunManager).createServerlessRun(any());
        verify(stopServerlessRunManager).updateServerlessRun(any());
    }

    @Test
    public void shouldSendRequestIfConfigurationAlreadyRun() {
        final RunConfigurationEntry entry = runConfigurationEntry(TEST_NAME, true);
        entry.setEndpointName(TEST_NAME);
        final RunConfiguration configuration = runConfiguration(TEST_NAME, entry);
        final PipelineRun pipelineRun = pipelineRun();
        pipelineRun.setServiceUrl(String.format("[{\"url\": \"%s\", \"name\": \"%s\"}]", TEST_URL, TEST_NAME));
        final PagedResult<List<PipelineRun>> activeRuns = new PagedResult<>(
                Collections.singletonList(pipelineRun), 1);

        when(runConfigurationManager.load(any())).thenReturn(configuration);
        when(runManager.searchPipelineRuns(any(), anyBoolean())).thenReturn(activeRuns);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_WAIT_COUNT)).thenReturn(1);
        when(runManager.loadPipelineRun(any())).thenReturn(pipelineRun);
        doReturn(StringUtils.EMPTY).when(serverlessConfigurationManager).sendRequest(any(), any());
        when(stopServerlessRunManager.loadByRunId(any())).thenReturn(Optional.of(
                StopServerlessRun.builder()
                        .runId(pipelineRun.getId())
                        .lastUpdate(LocalDateTime.now())
                        .build()));

        serverlessConfigurationManager.run(CONFIGURATION_ID, TEST_NAME, mockRequest());
        verifyEndpoint();
        verify(configurationRunner, times(0)).runConfiguration(any(), any(), any());
        verify(stopServerlessRunManager, times(0)).createServerlessRun(any());
        verify(stopServerlessRunManager, times(2)).updateServerlessRun(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfConfigNameNotFound() {
        final RunConfigurationEntry entry = runConfigurationEntry(TEST_NAME, true);
        final RunConfiguration configuration = runConfiguration(TEST_NAME, entry);

        when(runConfigurationManager.load(any())).thenReturn(configuration);

        serverlessConfigurationManager.run(CONFIGURATION_ID, ANOTHER_TEST_NAME, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfInitializationTimeExceeded() {
        final RunConfigurationEntry entry = runConfigurationEntry(TEST_NAME, true);
        final RunConfiguration configuration = runConfiguration(TEST_NAME, entry);
        final PipelineRun pipelineRun = pipelineRun();
        final PagedResult<List<PipelineRun>> activeRuns = new PagedResult<>(
                Collections.singletonList(pipelineRun), 1);

        when(runConfigurationManager.load(any())).thenReturn(configuration);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_WAIT_COUNT)).thenReturn(1);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_TASK_STATUS_UPDATE_RATE)).thenReturn(1);
        when(runManager.loadPipelineRun(any())).thenReturn(pipelineRun);
        when(runManager.searchPipelineRuns(any(), anyBoolean())).thenReturn(activeRuns);
        when(stopServerlessRunManager.loadByRunId(any())).thenReturn(Optional.empty());

        serverlessConfigurationManager.run(CONFIGURATION_ID, TEST_NAME, mockRequest());
    }

    @Test
    public void shouldGenerateServerelessUrl() {
        final RunConfigurationEntry defaultEntry = runConfigurationEntry(TEST_NAME, true);
        final RunConfigurationEntry entry = runConfigurationEntry(ANOTHER_TEST_NAME, false);
        final RunConfiguration configuration = ObjectCreatorUtils.createConfiguration(TEST_NAME, null, null,
                null, Arrays.asList(entry, defaultEntry));

        when(preferenceManager.getPreference(SystemPreferences.BASE_API_HOST_EXTERNAL)).thenReturn(TEST_URL);
        when(runConfigurationManager.load(any())).thenReturn(configuration);

        final String result = serverlessConfigurationManager.generateUrl(CONFIGURATION_ID, ANOTHER_TEST_NAME);
        assertEquals(TEST_URL + "serverless/" + CONFIGURATION_ID + "/" + ANOTHER_TEST_NAME, result);
    }

    @Test
    public void shouldGenerateServerelessUrlFromDefaultConfiguration() {
        final RunConfigurationEntry defaultEntry = runConfigurationEntry(TEST_NAME, true);
        final RunConfigurationEntry entry = runConfigurationEntry(ANOTHER_TEST_NAME, false);
        final RunConfiguration configuration = ObjectCreatorUtils.createConfiguration(TEST_NAME, null, null,
                null, Arrays.asList(entry, defaultEntry));

        when(preferenceManager.getPreference(SystemPreferences.BASE_API_HOST_EXTERNAL)).thenReturn(TEST_URL);
        when(runConfigurationManager.load(any())).thenReturn(configuration);

        final String result = serverlessConfigurationManager.generateUrl(CONFIGURATION_ID, null);
        assertEquals(TEST_URL + "serverless/" + CONFIGURATION_ID + "/" + TEST_NAME, result);
    }

    private RunConfigurationEntry runConfigurationEntry(final String name, final boolean isDefault) {
        return ObjectCreatorUtils.createConfigEntry(name, isDefault, null);
    }

    private RunConfiguration runConfiguration(final String name, final RunConfigurationEntry entry) {
        return ObjectCreatorUtils.createConfiguration(name, null, null,
                null, Collections.singletonList(entry));
    }

    private RunConfigurationWithEntitiesVO runConfigurationWithEntitiesVO(final String name,
                                                                          final RunConfigurationEntry entry) {
        return ObjectCreatorUtils.createRunConfigurationWithEntitiesVO(name,
                        null, null, Collections.singletonList(entry));
    }

    private PipelineRun pipelineRun() {
        return ObjectCreatorUtils.createPipelineRun(RUN_ID, PIPELINE_ID, null, null);
    }

    private MockHttpServletRequest mockRequest() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setPathInfo(String.format("/serverless/%d/%s/%s", CONFIGURATION_ID, TEST_NAME, TEST_APP_PATH));
        request.setQueryString(TEST_QUERY);
        return request;
    }

    private void verifyEndpoint() {
        final ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
        verify(serverlessConfigurationManager).sendRequest(any(), endpointCaptor.capture());
        assertEquals(String.format("%s%s?%s", TEST_URL, TEST_APP_PATH, TEST_QUERY), endpointCaptor.getValue());
    }
}

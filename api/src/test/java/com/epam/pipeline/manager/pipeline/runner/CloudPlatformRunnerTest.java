/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline.runner;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.ResolvedConfiguration;
import com.epam.pipeline.exception.quota.InsufficientUsageCreditsException;
import com.epam.pipeline.manager.credits.PlatformUsageCreditsLaunchService;
import com.epam.pipeline.manager.pipeline.ParameterMapper;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CloudPlatformRunnerTest {

    private static final String MASTER_INSTANCE = "m5.large";
    private static final String WORKER_INSTANCE = "p3.xlarge";
    private static final long MASTER_RUN_ID = 1L;
    private static final int LAUNCH_MAX_SCHEDULED_NUMBER = 100;

    private final ParameterMapper parameterMapper = mock(ParameterMapper.class);
    private final PipelineConfigurationManager configManager = mock(PipelineConfigurationManager.class);
    private final PipelineManager pipelineManager = mock(PipelineManager.class);
    private final PipelineRunManager runManager = mock(PipelineRunManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PlatformUsageCreditsLaunchService creditsLaunchService =
            mock(PlatformUsageCreditsLaunchService.class);

    private final CloudPlatformRunner runner = new CloudPlatformRunner(
            parameterMapper, configManager, pipelineManager, runManager,
            preferenceManager, messageHelper, creditsLaunchService);

    @Before
    public void setUp() {
        doReturn(LAUNCH_MAX_SCHEDULED_NUMBER).when(preferenceManager)
                .getPreference(SystemPreferences.LAUNCH_MAX_SCHEDULED_NUMBER);
        doReturn("credits message").when(messageHelper).getMessage(anyString());
        doReturn("credits message").when(messageHelper).getMessage(anyString(), any(Object[].class));

        final PipelineRun masterRun = new PipelineRun();
        masterRun.setId(MASTER_RUN_ID);
        doReturn(masterRun).when(runManager)
                .launchPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        doReturn(false).when(configManager).hasNFSParameter(any());
        doReturn(new PipelineConfiguration()).when(configManager).generateMasterConfiguration(any(), anyBoolean());
        doReturn(new PipelineConfiguration()).when(configManager)
                .generateWorkerConfiguration(any(), any(), any(), anyBoolean(), anyBoolean());

        doNothing().when(creditsLaunchService)
                .checkHeterogeneousClusterCredits(any(), any(int.class), any(), any());
    }

    private void mockResolvedConfig(final List<RunConfigurationEntry> entries) {
        final Map<String, PipelineConfiguration> configMap = new HashMap<>();
        for (final RunConfigurationEntry e : entries) {
            configMap.put(e.getName(), e.getConfiguration());
        }
        final ResolvedConfiguration resolved = new ResolvedConfiguration(null, configMap);
        doReturn(Collections.singletonList(resolved)).when(parameterMapper).resolveConfigurations(any());
    }

    // --- heterogeneous cluster credit checks ---

    @Test
    public void heterogeneousClusterCallsCreditsServiceWithCorrectOwner() {
        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 0, true);
        final RunConfigurationEntry workerEntry = entry(WORKER_INSTANCE, 2, false);
        final List<RunConfigurationEntry> entries = Arrays.asList(masterEntry, workerEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));

        verify(creditsLaunchService).checkHeterogeneousClusterCredits(any(), any(int.class), any(), any());
    }

    @Test
    public void heterogeneousClusterPassesCorrectMasterNodeCount() {
        // masterEntry.workerCount=1 → masterNodeCount=1 passed to service
        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 1, true);
        final RunConfigurationEntry workerEntry = entry(WORKER_INSTANCE, 2, false);
        final List<RunConfigurationEntry> entries = Arrays.asList(masterEntry, workerEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));

        final ArgumentCaptor<Integer> masterNodeCountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(creditsLaunchService).checkHeterogeneousClusterCredits(
                any(), masterNodeCountCaptor.capture(), any(), any());
        assertThat(masterNodeCountCaptor.getValue(), is(1));
    }

    @Test(expected = InsufficientUsageCreditsException.class)
    public void heterogeneousClusterBlockedWhenInsufficientCredits() {
        doThrow(new InsufficientUsageCreditsException("Insufficient credits"))
                .when(creditsLaunchService)
                .checkHeterogeneousClusterCredits(any(), any(int.class), any(), any());

        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 0, true);
        final RunConfigurationEntry workerEntry = entry(WORKER_INSTANCE, 3, false);
        final List<RunConfigurationEntry> entries = Arrays.asList(masterEntry, workerEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));
    }

    @Test
    public void singleEntryCallsCreditsServiceWithZeroMasterNodeCount() {
        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 0, true);
        final List<RunConfigurationEntry> entries = Collections.singletonList(masterEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));

        final ArgumentCaptor<Integer> masterNodeCountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(creditsLaunchService).checkHeterogeneousClusterCredits(
                any(), masterNodeCountCaptor.capture(), any(), any());
        assertThat(masterNodeCountCaptor.getValue(), is(0));
    }

    // --- helpers ---

    private static RunConfigurationEntry entry(final String instanceType, final int workerCount,
                                               final boolean isDefault) {
        final PipelineConfiguration config = new PipelineConfiguration();
        config.setInstanceType(instanceType);
        config.setNodeCount(workerCount == 0 ? null : workerCount);

        final RunConfigurationEntry entry = new RunConfigurationEntry();
        entry.setName(instanceType + "-entry");
        entry.setDefaultConfiguration(isDefault);
        entry.setConfiguration(config);
        return entry;
    }

    private static AnalysisConfiguration<RunConfigurationEntry> analysisConfig(
            final List<RunConfigurationEntry> entries) {
        return AnalysisConfiguration.<RunConfigurationEntry>builder()
                .entries(entries)
                .notifications(Collections.emptyList())
                .build();
    }
}

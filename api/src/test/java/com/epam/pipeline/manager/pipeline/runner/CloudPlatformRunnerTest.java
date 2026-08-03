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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.ResolvedConfiguration;
import com.epam.pipeline.exception.quota.InsufficientUsageCreditsException;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.credits.ClusterReplicaGroup;
import com.epam.pipeline.manager.credits.CreditsCheckResult;
import com.epam.pipeline.manager.pipeline.ParameterMapper;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CloudPlatformRunnerTest {

    private static final String OWNER = "user1";
    private static final String MASTER_INSTANCE = "m5.large";
    private static final String WORKER_INSTANCE = "p3.xlarge";
    private static final long MASTER_RUN_ID = 1L;

    private final ParameterMapper parameterMapper = mock(ParameterMapper.class);
    private final PipelineConfigurationManager configManager = mock(PipelineConfigurationManager.class);
    private final PipelineManager pipelineManager = mock(PipelineManager.class);
    private final PipelineRunManager runManager = mock(PipelineRunManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final InstanceOfferManager instanceOfferManager = mock(InstanceOfferManager.class);

    private final CloudPlatformRunner runner = new CloudPlatformRunner(
            parameterMapper, configManager, pipelineManager, runManager,
            preferenceManager, messageHelper, authManager, instanceOfferManager);

    @Before
    public void setUp() {
        doReturn(OWNER).when(authManager).getAuthorizedUser();
        doReturn(100).when(preferenceManager).getPreference(SystemPreferences.LAUNCH_MAX_SCHEDULED_NUMBER);
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
    public void heterogeneousClusterAllowedWhenSufficientCredits() {
        final InstanceOffer masterOffer = offer(MASTER_INSTANCE, 4, 0);
        final InstanceOffer workerOffer = offer(WORKER_INSTANCE, 0, 1);
        doReturn(Optional.of(masterOffer)).when(instanceOfferManager).findOffer(eq(MASTER_INSTANCE), any());
        doReturn(Optional.of(workerOffer)).when(instanceOfferManager).findOffer(eq(WORKER_INSTANCE), any());
        doReturn(CreditsCheckResult.allowed()).when(runManager).checkClusterLaunchCredits(eq(OWNER), any());

        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 0, true);
        final RunConfigurationEntry workerEntry = entry(WORKER_INSTANCE, 2, false);
        final List<RunConfigurationEntry> entries = Arrays.asList(masterEntry, workerEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));

        verify(runManager).checkClusterLaunchCredits(eq(OWNER), any());
    }

    @Test(expected = InsufficientUsageCreditsException.class)
    public void heterogeneousClusterBlockedWhenInsufficientCredits() {
        final InstanceOffer masterOffer = offer(MASTER_INSTANCE, 2, 0);
        final InstanceOffer workerOffer = offer(WORKER_INSTANCE, 0, 1);
        doReturn(Optional.of(masterOffer)).when(instanceOfferManager).findOffer(eq(MASTER_INSTANCE), any());
        doReturn(Optional.of(workerOffer)).when(instanceOfferManager).findOffer(eq(WORKER_INSTANCE), any());
        doReturn(new CreditsCheckResult(false, 302, 200, 0))
                .when(runManager).checkClusterLaunchCredits(eq(OWNER), any());
        doReturn("Insufficient credits").when(messageHelper)
                .getMessage(eq(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_INSUFFICIENT), any(Object[].class));

        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 0, true);
        final RunConfigurationEntry workerEntry = entry(WORKER_INSTANCE, 3, false);
        final List<RunConfigurationEntry> entries = Arrays.asList(masterEntry, workerEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));
    }

    @Test
    public void heterogeneousClusterGroupsContainCorrectCounts() {
        // masterNodeCount=1 → master group replicas = 1+1 = 2
        // child nodeCount=2 → copies = getNodeCount(2,1) = 3 → worker group replicas = 3
        final InstanceOffer masterOffer = offer(MASTER_INSTANCE, 4, 0);
        final InstanceOffer workerOffer = offer(WORKER_INSTANCE, 0, 1);
        doReturn(Optional.of(masterOffer)).when(instanceOfferManager).findOffer(eq(MASTER_INSTANCE), any());
        doReturn(Optional.of(workerOffer)).when(instanceOfferManager).findOffer(eq(WORKER_INSTANCE), any());
        doReturn(CreditsCheckResult.allowed()).when(runManager).checkClusterLaunchCredits(eq(OWNER), any());

        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 1, true);
        final RunConfigurationEntry workerEntry = entry(WORKER_INSTANCE, 2, false);
        final List<RunConfigurationEntry> entries = Arrays.asList(masterEntry, workerEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));

        final ArgumentCaptor<List<ClusterReplicaGroup>> groupsCaptor = ArgumentCaptor.forClass(
                (Class<List<ClusterReplicaGroup>>) (Class<?>) List.class);
        verify(runManager).checkClusterLaunchCredits(eq(OWNER), groupsCaptor.capture());

        final List<ClusterReplicaGroup> groups = groupsCaptor.getValue();
        final ClusterReplicaGroup masterGroup = groups.stream()
                .filter(g -> g.getOffer().equals(masterOffer))
                .findFirst().orElseThrow(AssertionError::new);
        assertThat(masterGroup.getReplicas(), is(2)); // masterNodeCount(1) + 1

        final ClusterReplicaGroup workerGroup = groups.stream()
                .filter(g -> g.getOffer().equals(workerOffer))
                .findFirst().orElseThrow(AssertionError::new);
        assertThat(workerGroup.getReplicas(), is(3)); // getNodeCount(nodeCount=2, basicValue=1) = 2+1 = 3
    }

    @Test
    public void singleEntryHasOneMasterGroup() {
        final InstanceOffer masterOffer = offer(MASTER_INSTANCE, 4, 0);
        doReturn(Optional.of(masterOffer)).when(instanceOfferManager).findOffer(eq(MASTER_INSTANCE), any());
        doReturn(CreditsCheckResult.allowed()).when(runManager).checkClusterLaunchCredits(eq(OWNER), any());

        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 0, true);
        final List<RunConfigurationEntry> entries = Collections.singletonList(masterEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));

        final ArgumentCaptor<List<ClusterReplicaGroup>> groupsCaptor = ArgumentCaptor.forClass(
                (Class<List<ClusterReplicaGroup>>) (Class<?>) List.class);
        verify(runManager).checkClusterLaunchCredits(eq(OWNER), groupsCaptor.capture());
        assertThat(groupsCaptor.getValue().size(), is(1));
        assertThat(groupsCaptor.getValue().get(0).getReplicas(), is(1)); // masterNodeCount=0 → 0+1
    }

    @Test
    public void missingChildOfferGroupSkipped() {
        // Master offer found, child offer not found → only 1 group passed to checkClusterLaunchCredits
        final InstanceOffer masterOffer = offer(MASTER_INSTANCE, 4, 0);
        doReturn(Optional.of(masterOffer)).when(instanceOfferManager).findOffer(eq(MASTER_INSTANCE), any());
        doReturn(Optional.empty()).when(instanceOfferManager).findOffer(eq(WORKER_INSTANCE), any());
        doReturn(CreditsCheckResult.allowed()).when(runManager).checkClusterLaunchCredits(eq(OWNER), any());

        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 0, true);
        final RunConfigurationEntry workerEntry = entry(WORKER_INSTANCE, 2, false);
        final List<RunConfigurationEntry> entries = Arrays.asList(masterEntry, workerEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));

        final ArgumentCaptor<List<ClusterReplicaGroup>> groupsCaptor = ArgumentCaptor.forClass(
                (Class<List<ClusterReplicaGroup>>) (Class<?>) List.class);
        verify(runManager).checkClusterLaunchCredits(eq(OWNER), groupsCaptor.capture());
        assertThat(groupsCaptor.getValue().size(), is(1));
    }

    @Test
    public void noOfferAtAllSkipsCreditsCheck() {
        doReturn(Optional.empty()).when(instanceOfferManager).findOffer(anyString(), any());

        final RunConfigurationEntry masterEntry = entry(MASTER_INSTANCE, 0, true);
        final RunConfigurationEntry workerEntry = entry(WORKER_INSTANCE, 1, false);
        final List<RunConfigurationEntry> entries = Arrays.asList(masterEntry, workerEntry);
        mockResolvedConfig(entries);

        runner.runAnalysis(analysisConfig(entries));

        verify(runManager, never()).checkClusterLaunchCredits(any(), any());
    }

    // --- helpers ---

    private static InstanceOffer offer(final String instanceType, final int vcpu, final int gpu) {
        final InstanceOffer o = new InstanceOffer();
        o.setInstanceType(instanceType);
        o.setVCPU(vcpu);
        o.setGpu(gpu);
        return o;
    }

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

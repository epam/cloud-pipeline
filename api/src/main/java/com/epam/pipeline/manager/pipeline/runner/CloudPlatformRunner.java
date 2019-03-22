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

package com.epam.pipeline.manager.pipeline.runner;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.ResolvedConfiguration;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.manager.pipeline.ParameterMapper;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.epam.pipeline.entity.configuration.RunConfigurationUtils.getNodeCount;
import static com.epam.pipeline.manager.pipeline.PipelineConfigurationManager.NFS_CLUSTER_ROLE;
import static com.epam.pipeline.manager.pipeline.PipelineConfigurationManager.WORKER_CMD_TEMPLATE;

/**
 * Launches execution of {@link com.epam.pipeline.entity.configuration.RunConfigurationEntry}
 * in Cloud Platform environment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudPlatformRunner implements ExecutionRunner<RunConfigurationEntry> {

    private final ParameterMapper parameterMapper;
    private final PipelineConfigurationManager pipelineConfigurationManager;
    private final PipelineManager pipelineManager;
    private final PipelineRunManager pipelineRunManager;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    @Override
    public List<PipelineRun> runAnalysis(AnalysisConfiguration<RunConfigurationEntry> configuration) {
        checkRunsNumber(configuration.getEntries(), configuration.getEntitiesIds());
        return parameterMapper.resolveConfigurations(configuration)
                .stream()
                .map(conf -> runConfiguration(configuration.getConfigurationId(), configuration.getEntries(), conf))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private void checkRunsNumber(List<RunConfigurationEntry> entries, List<Long> entitiesIds) {
        int entitiesIdsCount = CollectionUtils.isEmpty(entitiesIds) ? 1 : entitiesIds.size();
        int totalNodeCount = entries.stream()
                .map(AbstractRunConfigurationEntry::getWorkerCount)
                .mapToInt(nodeCount -> getNodeCount(nodeCount, 1))
                .sum();
        int numberOfRuns = totalNodeCount * entitiesIdsCount;

        int maxRunsNumber = preferenceManager.getPreference(SystemPreferences.LAUNCH_MAX_SCHEDULED_NUMBER);

        log.debug("Allowed runs count - {}, actual - {}", maxRunsNumber, numberOfRuns);
        Assert.isTrue(numberOfRuns < maxRunsNumber, messageHelper.getMessage(
                MessageConstants.ERROR_EXCEED_MAX_RUNS_COUNT, maxRunsNumber, numberOfRuns));
    }

    private List<PipelineRun> runConfiguration(Long configurationId,
                                               List<RunConfigurationEntry> entries,
                                               ResolvedConfiguration resolvedConfigurations) {

        SplitConfig splitConfig = new SplitConfig(entries);
        RunConfigurationEntry mainEntry = splitConfig.getMain();
        List<RunConfigurationEntry> childEntries = splitConfig.getChildEntries();

        boolean isMasterNFSServer =
                pipelineConfigurationManager.hasNFSParameter(mainEntry.getConfiguration()) ||
                        childEntries.stream()
                                .noneMatch(entry -> pipelineConfigurationManager
                                        .hasNFSParameter(entry.getConfiguration()));
        boolean nfsStarted = isMasterNFSServer;

        PipelineConfiguration mainConfiguration = resolvedConfigurations.getConfiguration(mainEntry.getName());

        List<PipelineConfiguration> childConfigurations = childEntries
                .stream()
                .map(entry -> resolvedConfigurations.getConfiguration(entry.getName()))
                .collect(Collectors.toList());

        int masterNodeCount = getNodeCount(mainConfiguration.getNodeCount(), 0);

        int totalNodes = childConfigurations.stream()
                .map(PipelineConfiguration::getNodeCount)
                .mapToInt(nodeCount -> getNodeCount(nodeCount, 1))
                .sum();

        totalNodes += masterNodeCount;

        log.debug("Running total {} nodes", totalNodes + 1);
        mainConfiguration.setNodeCount(totalNodes);

        //create master run
        List<PipelineRun> masterRun =
                runConfigurationEntry(mainEntry, mainConfiguration, 1, null,
                        isMasterNFSServer, resolvedConfigurations.getAllAssociatedIds(), configurationId);
        List<PipelineRun> launched = new ArrayList<>(masterRun);
        String clusterId = String.valueOf(masterRun.get(0).getId());
        //create master workers
        if (masterNodeCount > 0) {
            mainEntry.getConfiguration().setWorkerCmd(WORKER_CMD_TEMPLATE);
            launched.addAll(
                    runConfigurationEntry(mainEntry, mainConfiguration,
                            masterNodeCount, clusterId, false,
                            resolvedConfigurations.getAllAssociatedIds(), configurationId));
        }
        //create all other workers
        for (int i = 0; i < childConfigurations.size(); i++) {
            PipelineConfiguration childConfig = childConfigurations.get(i);

            boolean startNFS = !nfsStarted && pipelineConfigurationManager.hasNFSParameter(childConfig);
            nfsStarted = nfsStarted || startNFS;
            int copies = getNodeCount(childConfig.getNodeCount(), 1);
            launched.addAll(
                    runConfigurationEntry(childEntries.get(i),
                            childConfig, copies, clusterId, startNFS,
                            resolvedConfigurations.getAllAssociatedIds(), configurationId));
        }
        return launched;
    }

    private List<PipelineRun> runConfigurationEntry(RunConfigurationEntry entry,
                                                    PipelineConfiguration configuration,
                                                    int copies, String clusterId, boolean startNFS,
                                                    List<Long> entityIds, Long configurationId) {

        PipelineStart startVO = entry.toPipelineStart();
        if (!StringUtils.hasText(clusterId)) {
            log.debug("Launching master entry {}", entry.getName());
            pipelineConfigurationManager.updateMasterConfiguration(configuration, startNFS);
        } else {
            log.debug("Launching worker entry {}", entry.getName());
            pipelineConfigurationManager.updateWorkerConfiguration(clusterId, startVO, configuration, startNFS, true);
        }
        Pipeline pipeline = entry.getPipelineId() != null ? pipelineManager.load(entry.getPipelineId()) : null;
        List<PipelineRun> result = new ArrayList<>();
        log.debug("Launching total {} copies of entry {}", copies, entry.getName());
        for (int i = 0; i < copies; i++) {
            //only first node may be a NFS server
            if (i != 0) {
                configuration.setCmdTemplate(WORKER_CMD_TEMPLATE);
                configuration.getParameters().remove(NFS_CLUSTER_ROLE);
                configuration.buildEnvVariables();
            }
            result.add(pipelineRunManager.launchPipeline(configuration, pipeline, entry.getPipelineVersion(),
                    startVO.getInstanceType(), startVO.getParentNodeId(),
                    startVO.getConfigurationName(), clusterId, null, entityIds, configurationId, startVO.getRunSids()));
        }
        return result;
    }

    @Data
    private static class SplitConfig {

        private RunConfigurationEntry main;
        private List<RunConfigurationEntry> childEntries;

        SplitConfig(List<RunConfigurationEntry> entries) {
            Assert.state(CollectionUtils.isNotEmpty(entries), "Empty entries");
            this.main = entries.stream()
                    .filter(RunConfigurationEntry::getDefaultConfiguration)
                    .findFirst()
                    .orElse(entries.get(0));
            this.childEntries = entries.stream()
                    .filter(entry -> !entry.equals(this.main))
                    .collect(Collectors.toList());
        }
    }

}

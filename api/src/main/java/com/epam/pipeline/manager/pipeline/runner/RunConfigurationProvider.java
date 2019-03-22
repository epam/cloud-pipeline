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
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.CloudPlatformPreferences;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.PermissionsHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RunConfigurationProvider implements ConfigurationProvider<RunConfigurationEntry,
        CloudPlatformPreferences> {
    private final PipelineManager pipelineManager;
    private final ToolManager toolManager;
    private final PermissionsHelper permissionsHelper;
    private final PipelineConfigurationManager pipelineConfigurationManager;
    private final InstanceOfferManager instanceOfferManager;
    private final MessageHelper messageHelper;
    private final CloudPlatformRunner runner;

    @Override
    public ExecutionEnvironment getExecutionEnvironment() {
        return ExecutionEnvironment.CLOUD_PLATFORM;
    }

    /**
     * Method will check permission {@link Pipeline} if it's specified or {@link Tool}
     * if {@link Pipeline} is not specified.
     * @param entry configuration entry
     * @param permissionName the name of permission
     * @return true if permission is not granted
     */
    @Override
    public boolean hasNoPermission(RunConfigurationEntry entry, String permissionName) {
        if (entry.getPipelineId() != null) {
            Pipeline pipeline = pipelineManager.load(entry.getPipelineId(), false);
            return !permissionsHelper.isAllowed(permissionName, pipeline);
        } else if (entry.getConfiguration().getDockerImage() != null) {
            Tool tool = toolManager.loadByNameOrId(entry.getConfiguration().getDockerImage());
            return !permissionsHelper.isAllowed(permissionName, tool);
        }
        return false;
    }

    /**
     * Overrides a db registered configuration entries if user has provided a new ones.
     * @param entries user provided configuration entries
     * @param dbEntries db registered configuration entries
     * @return merged entries
     */
    @Override
    public List<RunConfigurationEntry> mergeConfigurationEntries(List<RunConfigurationEntry> entries,
                                                                 List<RunConfigurationEntry> dbEntries) {
        Map<String, RunConfigurationEntry> userProvidedEntries =
                entries.stream().collect(Collectors.toMap(RunConfigurationEntry::getName, Function.identity()));
        if (userProvidedEntries.isEmpty()) {
            return dbEntries;
        }
        return dbEntries.stream()
                .filter(entry -> userProvidedEntries.containsKey(entry.getName()))
                .peek(entry -> {
                    RunConfigurationEntry updated = userProvidedEntries.get(entry.getName());
                    if (entry.getConfiguration() == null) {
                        entry.setConfiguration(updated.getConfiguration());
                    } else {
                        entry.setConfiguration(pipelineConfigurationManager.mergeParameters(
                                updated.toPipelineStart(), entry.getConfiguration()));
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public void validateEntry(RunConfigurationEntry entry) {
        if (entry.getPipelineId() != null) {
            pipelineManager.load(entry.getPipelineId());
        } else if (entry.getConfiguration() != null) {
            validateEntryConfiguration(entry.getConfiguration());
        }
    }

    @Override
    public List<PipelineRun> runAnalysis(AnalysisConfiguration<RunConfigurationEntry> configuration) {
        return runner.runAnalysis(configuration);
    }

    @Override
    public boolean stop(Long runId, CloudPlatformPreferences executionPreferences) {
        return false;
    }

    private void validateEntryConfiguration(final PipelineConfiguration configuration) {
        if (configuration.getInstanceType() != null) {
            final ContextualPreferenceExternalResource resource = retrieveResource(configuration);
            Assert.isTrue(instanceOfferManager.isToolInstanceAllowed(configuration.getInstanceType(),
                    resource),
                    messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED,
                            configuration.getInstanceType()));
        }
    }

    private ContextualPreferenceExternalResource retrieveResource(final PipelineConfiguration configuration) {
        final String dockerImage = configuration.getDockerImage();
        if (dockerImage != null) {
            final Tool tool = toolManager.loadByNameOrId(dockerImage);
            return new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, tool.getId().toString());
        } else {
            return null;
        }
    }
}

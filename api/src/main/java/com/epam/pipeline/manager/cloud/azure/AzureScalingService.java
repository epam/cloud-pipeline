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

package com.epam.pipeline.manager.cloud.azure;


import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cloud.CloudScalingService;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.cloud.commands.AbstractClusterCommand;
import com.epam.pipeline.manager.cloud.commands.ClusterCommandService;
import com.epam.pipeline.manager.cloud.commands.NodeUpCommand;
import com.epam.pipeline.manager.cloud.commands.RunIdArgCommand;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class AzureScalingService implements CloudScalingService<AzureRegion> {

    private static final String AZURE_AUTH_LOCATION = "AZURE_AUTH_LOCATION";
    private static final String AZURE_RESOURCE_GROUP = "AZURE_RESOURCE_GROUP";
    private final CommonCloudInstanceService instanceService;
    private final ClusterCommandService commandService;
    private final PreferenceManager preferenceManager;
    private final CloudRegionManager cloudRegionManager;
    private final ParallelExecutorService executorService;
    private final CmdExecutor cmdExecutor = new CmdExecutor();
    private final String nodeUpScript;
    private final String nodeDownScript;
    private final String nodeReassignScript;
    private final String nodeTerminateScript;

    public AzureScalingService(final CommonCloudInstanceService instanceService,
                               final ClusterCommandService commandService,
                               final PreferenceManager preferenceManager,
                               final CloudRegionManager regionManager,
                               final ParallelExecutorService executorService,
                               @Value("${cluster.azure.nodeup.script:}") final String nodeUpScript,
                               @Value("${cluster.azure.nodedown.script:}") final String nodeDownScript,
                               @Value("${cluster.azure.reassign.script:}") final String nodeReassignScript,
                               @Value("${cluster.azure.node.terminate.script:}") final String nodeTerminateScript) {
        this.instanceService = instanceService;
        this.commandService = commandService;
        this.cloudRegionManager = regionManager;
        this.preferenceManager = preferenceManager;
        this.executorService = executorService;
        this.nodeUpScript = nodeUpScript;
        this.nodeDownScript = nodeDownScript;
        this.nodeReassignScript = nodeReassignScript;
        this.nodeTerminateScript = nodeTerminateScript;
    }

    @Override
    public RunInstance scaleUpNode(final AzureRegion region,
                                   final Long runId,
                                   final RunInstance instance) {
        final String command = buildNodeUpCommand(region, String.valueOf(runId), instance, Collections.emptyMap());
        return instanceService.runNodeUpScript(cmdExecutor, runId, instance, command, buildScriptAzureEnvVars(region));
    }

    @Override
    public RunInstance scaleUpPoolNode(final AzureRegion region,
                                       final String nodeId,
                                       final NodePool nodePool) {
        final RunInstance instance = nodePool.toRunInstance();
        final String command = buildNodeUpCommand(region, nodeId, instance, getPoolLabels(nodePool));
        return instanceService.runNodeUpScript(cmdExecutor, null, instance, command, buildScriptAzureEnvVars(region));
    }

    @Override
    public void scaleDownNode(final AzureRegion region, final Long runId) {
        final String command = buildNodeDownCommand(String.valueOf(runId));
        final Map<String, String> envVars = buildScriptAzureEnvVars(region);
        CompletableFuture.runAsync(() -> instanceService.runNodeDownScript(cmdExecutor, command, envVars),
                executorService.getExecutorService());
    }

    @Override
    public void scaleDownPoolNode(final AzureRegion region, final String nodeLabel) {
        final String command = buildNodeDownCommand(nodeLabel);
        final Map<String, String> envVars = buildScriptAzureEnvVars(region);
        CompletableFuture.runAsync(() -> instanceService.runNodeDownScript(cmdExecutor, command, envVars),
                                   executorService.getExecutorService());
    }

    @Override
    public boolean reassignNode(final AzureRegion region, final Long oldId, final Long newId) {
        final String command = commandService.buildNodeReassignCommand(
                nodeReassignScript, oldId, newId, getProvider().name());
        return instanceService.runNodeReassignScript(cmdExecutor, command, oldId, newId,
                buildScriptAzureEnvVars(region));
    }

    @Override
    public boolean reassignPoolNode(final AzureRegion region, final String nodeLabel, final Long newId) {
        final String command = commandService.
            buildNodeReassignCommand(nodeReassignScript, nodeLabel, newId, getProvider().name());
        return instanceService.runNodeReassignScript(cmdExecutor, command, nodeLabel,
                                                     String.valueOf(newId), buildScriptAzureEnvVars(region));
    }

    @Override
    public void terminateNode(final AzureRegion region, final String internalIp, final String nodeName) {
        final String command = commandService.buildTerminateNodeCommand(nodeTerminateScript, internalIp, nodeName,
                getProviderName());
        final Map<String, String> envVars = buildScriptAzureEnvVars(region);
        CompletableFuture.runAsync(() -> instanceService.runTerminateNodeScript(command, cmdExecutor, envVars),
                executorService.getExecutorService());
    }

    @Override
    public LocalDateTime getNodeLaunchTime(final AzureRegion region, final Long runId) {
        return instanceService.getNodeLaunchTimeFromKube(runId);
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(final AzureRegion region) {
        final AzureRegionCredentials credentials = cloudRegionManager.loadCredentials(region);
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(SystemParams.CLOUD_REGION_PREFIX + region.getId(), region.getRegionCode());
        envVars.put(SystemParams.CLOUD_ACCOUNT_PREFIX + region.getId(), region.getStorageAccount());
        envVars.put(SystemParams.CLOUD_ACCOUNT_KEY_PREFIX + region.getId(), credentials.getStorageAccountKey());
        envVars.put(SystemParams.CLOUD_PROVIDER_PREFIX + region.getId(), region.getProvider().name());
        return envVars;
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    private Map<String, String> buildScriptAzureEnvVars(final AzureRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        if (StringUtils.isNotBlank(region.getAuthFile())) {
            envVars.put(AZURE_AUTH_LOCATION, region.getAuthFile());
        }
        envVars.put(AZURE_RESOURCE_GROUP, region.getResourceGroup());
        return envVars;
    }

    private String buildNodeUpCommand(final AzureRegion region, final String nodeLabel, final RunInstance instance,
                                      final Map<String, String> labels) {
        final NodeUpCommand.NodeUpCommandBuilder commandBuilder =
            commandService.buildNodeUpCommand(nodeUpScript, region, nodeLabel, instance, null, labels)
                .sshKey(region.getSshPublicKeyPath());

        final Boolean clusterSpotStrategy = instance.getSpot() == null
                ? preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT)
                : instance.getSpot();

        if (BooleanUtils.isTrue(clusterSpotStrategy)) {
            commandBuilder.isSpot(true);
        }
        return commandBuilder.build().getCommand();
    }

    private String buildNodeDownCommand(final String nodeLabel) {
        return RunIdArgCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeDownScript)
                .runId(nodeLabel)
                .build()
                .getCommand();
    }
}

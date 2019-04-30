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

package com.epam.pipeline.manager.cloud.azure;

import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.exception.cloud.azure.AzureException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cloud.CloudInstanceService;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.cloud.commands.AbstractClusterCommand;
import com.epam.pipeline.manager.cloud.commands.NodeUpCommand;
import com.epam.pipeline.manager.cloud.commands.RunIdArgCommand;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.network.NetworkInterface;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Service
@Slf4j
public class AzureInstanceService implements CloudInstanceService<AzureRegion> {

    private static final String AZURE_AUTH_LOCATION = "AZURE_AUTH_LOCATION";
    private static final String AZURE_RESOURCE_GROUP = "AZURE_RESOURCE_GROUP";
    private final CommonCloudInstanceService instanceService;
    private final AzureVMService vmService;
    private final KubernetesManager kubernetesManager;
    private final CloudRegionManager cloudRegionManager;
    private final ParallelExecutorService executorService;
    private final CmdExecutor cmdExecutor = new CmdExecutor();
    private final String nodeUpScript;
    private final String nodeDownScript;
    private final String nodeReassignScript;
    private final String nodeTerminateScript;
    private final String kubeMasterIP;
    private final String kubeToken;

    public AzureInstanceService(final CommonCloudInstanceService instanceService,
                                final AzureVMService vmService,
                                final KubernetesManager kubernetesManager,
                                final CloudRegionManager regionManager,
                                final ParallelExecutorService executorService,
                                @Value("${cluster.azure.nodeup.script:}") final String nodeUpScript,
                                @Value("${cluster.azure.nodedown.script:}") final String nodeDownScript,
                                @Value("${cluster.azure.reassign.script:}") final String nodeReassignScript,
                                @Value("${cluster.azure.node.terminate.script:}") final String nodeTerminateScript,
                                @Value("${kube.master.ip}") final String kubeMasterIP,
                                @Value("${kube.kubeadm.token}") final String kubeToken) {
        this.instanceService = instanceService;
        this.cloudRegionManager = regionManager;
        this.vmService = vmService;
        this.kubernetesManager = kubernetesManager;
        this.executorService = executorService;
        this.nodeUpScript = nodeUpScript;
        this.nodeDownScript = nodeDownScript;
        this.nodeReassignScript = nodeReassignScript;
        this.nodeTerminateScript = nodeTerminateScript;
        this.kubeMasterIP = kubeMasterIP;
        this.kubeToken = kubeToken;
    }

    @Override
    public RunInstance scaleUpNode(final AzureRegion region,
                                   final Long runId,
                                   final RunInstance instance) {
        final String command = buildNodeUpCommand(region, runId, instance);
        final Map<String, String> envVars = buildScriptAzureEnvVars(region);
        return instanceService.runNodeUpScript(cmdExecutor, runId, instance, command, envVars);
    }

    @Override
    public void scaleDownNode(final AzureRegion region, final Long runId) {
        final String command = buildNodeDownCommand(runId);
        final Map<String, String> envVars = buildScriptAzureEnvVars(region);
        CompletableFuture.runAsync(() -> instanceService.runNodeDownScript(cmdExecutor, command, envVars),
                executorService.getExecutorService());
    }

    //TODO: This code won't work for current scripts
    @Override
    public void scaleUpFreeNode(final AzureRegion region, final String nodeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void terminateNode(final AzureRegion region, final String internalIp, final String nodeName) {
        final String command = instanceService.buildTerminateNodeCommand(internalIp, nodeName, nodeTerminateScript);
        final Map<String, String> envVars = buildScriptAzureEnvVars(region);
        CompletableFuture.runAsync(() -> instanceService.runTerminateNodeScript(command, cmdExecutor, envVars),
                executorService.getExecutorService());
    }

    @Override
    public void startInstance(final AzureRegion region, final String instanceId) {
        vmService.startInstance(region, instanceId);
    }

    @Override
    public void stopInstance(final AzureRegion region, final String instanceId) {
        vmService.stopInstance(region, instanceId);
    }

    @Override
    public void terminateInstance(final AzureRegion region, final String instanceId) {
        vmService.terminateInstance(region, instanceId);
    }

    @Override
    public boolean instanceExists(final AzureRegion region, final String instanceId) {
        return vmService.findVmByName(region, instanceId).isPresent();
    }

    @Override
    public LocalDateTime getNodeLaunchTime(final AzureRegion region, final Long runId) {
        return kubernetesManager.findNodeByRunId(String.valueOf(runId))
                .map(node -> node.getMetadata().getCreationTimestamp())
                .filter(StringUtils::isNotBlank)
                .map(timestamp -> {
                    try {
                        return ZonedDateTime.parse(timestamp, KubernetesConstants.KUBE_DATE_FORMATTER)
                                .toLocalDateTime();
                    } catch (DateTimeParseException e) {
                        log.error("Failed to parse date from Kubernetes API: {}", timestamp);
                        return null;
                    }
                }).orElse(null);
    }

    @Override
    public RunInstance describeInstance(final AzureRegion region, final String nodeLabel, final RunInstance instance) {
        return describeInstance(region, nodeLabel, instance, () -> vmService.getRunningVMByRunId(region, nodeLabel));
    }

    @Override
    public RunInstance describeAliveInstance(final AzureRegion region, final String nodeLabel,
                                             final RunInstance instance) {
        return describeInstance(region, nodeLabel, instance, () -> vmService.getAliveVMByRunId(region, nodeLabel));
    }

    private RunInstance describeInstance(final AzureRegion region,
                                         final String nodeLabel,
                                         final RunInstance instance,
                                         final Supplier<VirtualMachine> supplier) {
        try {
            final VirtualMachine vm = supplier.get();
            instance.setNodeId(vm.vmId());
            final NetworkInterface networkInterface = vmService.getVMNetworkInterface(region.getAuthFile(), vm);
            instance.setNodeName(networkInterface.primaryIPConfiguration().privateIPAddress());
            instance.setNodeIP(networkInterface.internalFqdn());
            return instance;
        } catch (AzureException e) {
            log.error("An error while getting instance description {}", nodeLabel);
            return null;
        }
    }

    @Override
    public boolean reassignNode(final AzureRegion region, final Long oldId, final Long newId) {
        return instanceService.runNodeReassignScript(
                oldId, newId, cmdExecutor, nodeReassignScript, buildScriptAzureEnvVars(region));
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
    public Optional<InstanceTerminationState> getInstanceTerminationState(final AzureRegion region,
                                                                          final String instanceId) {
        return vmService.getFailingVMStatus(region, instanceId).map(status -> InstanceTerminationState.builder()
                .instanceId(instanceId)
                .stateCode(status.code())
                .stateMessage(status.message())
                .build());
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    private Map<String, String> buildScriptAzureEnvVars(final AzureRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(AZURE_AUTH_LOCATION, region.getAuthFile());
        envVars.put(AZURE_RESOURCE_GROUP, region.getResourceGroup());
        return envVars;
    }

    private String buildNodeUpCommand(final AzureRegion region, final Long runId, final RunInstance instance) {

        NodeUpCommand.NodeUpCommandBuilder commandBuilder = NodeUpCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeUpScript)
                .runId(String.valueOf(runId))
                .sshKey(region.getSshPublicKeyPath())
                .instanceImage(instance.getNodeImage())
                .instanceType(instance.getNodeType())
                .instanceDisk(String.valueOf(instance.getEffectiveNodeDisk()))
                .kubeIP(kubeMasterIP)
                .kubeToken(kubeToken)
                .region(region.getRegionCode());

        return commandBuilder.build().getCommand();
    }

    private String buildNodeDownCommand(final Long runId) {
        return RunIdArgCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeDownScript)
                .runId(String.valueOf(runId))
                .build()
                .getCommand();
    }
}

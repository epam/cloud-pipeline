/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.InstanceImage;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.cloud.gcp.GCPException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cloud.CloudInstanceService;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.cloud.commands.ClusterCommandService;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.google.api.services.compute.model.Instance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GCPInstanceService implements CloudInstanceService<GCPRegion> {

    private static final String GOOGLE_PROJECT_ID = "GOOGLE_PROJECT_ID";
    protected static final String GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";

    private final ClusterCommandService commandService;
    private final CommonCloudInstanceService instanceService;
    private final GCPVMService vmService;
    private final String nodeUpScript;
    private final String nodeDownScript;
    private final String nodeReassignScript;
    private final String nodeTerminateScript;
    private final PreferenceManager preferenceManager;
    private final ParallelExecutorService executorService;
    private final CmdExecutor cmdExecutor = new CmdExecutor();

    public GCPInstanceService(final ClusterCommandService commandService,
                              final CommonCloudInstanceService instanceService,
                              final GCPVMService vmService,
                              final PreferenceManager preferenceManager,
                              final ParallelExecutorService executorService,
                              @Value("${cluster.gcp.nodeup.script}") final String nodeUpScript,
                              @Value("${cluster.gcp.nodedown.script}") final String nodeDownScript,
                              @Value("${cluster.gcp.reassign.script}") final String nodeReassignScript,
                              @Value("${cluster.gcp.node.terminate.script}") final String nodeTerminateScript) {
        this.commandService = commandService;
        this.instanceService = instanceService;
        this.vmService = vmService;
        this.preferenceManager = preferenceManager;
        this.executorService = executorService;
        this.nodeUpScript = nodeUpScript;
        this.nodeDownScript = nodeDownScript;
        this.nodeReassignScript = nodeReassignScript;
        this.nodeTerminateScript = nodeTerminateScript;
    }

    @Override
    public RunInstance scaleUpNode(final GCPRegion region, final Long runId, final RunInstance instance,
                                   final Map<String, String> runtimeParameters, final Map<String, String> customTags) {
        final String command = buildNodeUpCommand(region, String.valueOf(runId), instance, Collections.emptyMap(),
                runtimeParameters);
        return instanceService.runNodeUpScript(cmdExecutor, runId, instance, command, buildScriptGCPEnvVars(region));
    }

    @Override
    public RunInstance scaleUpPoolNode(final GCPRegion region, final String nodeId, final NodePool node) {
        final RunInstance instance = node.toRunInstance();
        final String command = buildNodeUpCommand(region, nodeId, instance, getPoolLabels(node),
                Collections.emptyMap());
        return instanceService.runNodeUpScript(cmdExecutor, null, instance, command,
                buildScriptGCPEnvVars(region));
    }

    @Override
    public void scaleDownNode(final GCPRegion region, final Long runId) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, runId, getProviderName());
        final Map<String, String> envVars = buildScriptGCPEnvVars(region);
        instanceService.runNodeDownScript(cmdExecutor, command, envVars);
    }

    @Override
    public void scaleDownPoolNode(final GCPRegion region, final String nodeLabel) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, nodeLabel, getProviderName());
        instanceService.runNodeDownScript(cmdExecutor, command, buildScriptGCPEnvVars(region));
    }

    @Override
    public boolean reassignNode(final GCPRegion region, final Long oldId, final Long newId,
                                final Map<String, String> customTags) {
        final String command = commandService.buildNodeReassignCommand(
                nodeReassignScript, oldId, newId, getProviderName(), customTags);
        return instanceService.runNodeReassignScript(cmdExecutor, command, oldId, newId,
                buildScriptGCPEnvVars(region));
    }

    @Override
    public boolean reassignPoolNode(final GCPRegion region, final String nodeLabel, final Long newId,
                                    final Map<String, String> customTags) {
        final String command = commandService
            .buildNodeReassignCommand(nodeReassignScript, nodeLabel, String.valueOf(newId), getProviderName(),
                    customTags);
        return instanceService.runNodeReassignScript(cmdExecutor, command, nodeLabel, String.valueOf(newId),
                                                     buildScriptGCPEnvVars(region));
    }

    @Override
    public void terminateNode(final GCPRegion region, final String internalIp, final String nodeName) {
        final String command = commandService.buildTerminateNodeCommand(nodeTerminateScript, internalIp,
                nodeName, getProviderName());
        final Map<String, String> envVars = buildScriptGCPEnvVars(region);
        CompletableFuture.runAsync(() -> instanceService.runTerminateNodeScript(command, cmdExecutor, envVars),
                executorService.getExecutorService());
    }

    @Override
    public CloudInstanceOperationResult startInstance(final GCPRegion region, final String instanceId) {
        return vmService.startInstance(region, instanceId);
    }

    @Override
    public void stopInstance(final GCPRegion region, final String instanceId) {
        vmService.stopInstance(region, instanceId);
    }

    @Override
    public void terminateInstance(final GCPRegion region, final String instanceId) {
        vmService.terminateInstance(region, instanceId);
    }

    @Override
    public boolean instanceExists(final GCPRegion region, final String instanceId) {
        return vmService.instanceExists(region, instanceId);
    }

    @Override
    public RunInstance describeAliveInstance(final GCPRegion region, final String nodeLabel,
                                             final RunInstance instance) {
        try {
            return fillRunInstanceFromGcpVm(instance, vmService.getAliveInstance(region, nodeLabel));
        } catch (GCPException e) {
            log.error("An error while getting instance description {}", nodeLabel);
            return null;
        }
    }

    @Override
    public RunInstance describeInstance(final GCPRegion region, final String nodeLabel, final RunInstance instance) {
        try {
            return fillRunInstanceFromGcpVm(instance, vmService.getRunningInstanceByRunId(region, nodeLabel));
        } catch (GCPException e) {
            log.error("An error while getting instance description {}", nodeLabel);
            return null;
        }
    }

    @Override
    public LocalDateTime getNodeLaunchTime(final GCPRegion region, final Long runId) {
        return instanceService.getNodeLaunchTimeFromKube(runId);
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(final GCPRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(SystemParams.CLOUD_REGION_PREFIX + region.getId(), region.getRegionCode());
        envVars.put(SystemParams.CLOUD_PROVIDER_PREFIX + region.getId(), region.getProvider().name());
        final String credentialsFile = getCredentialsFilePath(region);
        if (!StringUtils.isEmpty(credentialsFile)) {
            try {
                final String credentials = String.join(StringUtils.EMPTY,
                        Files.readAllLines(Paths.get(credentialsFile)));
                envVars.put(SystemParams.CLOUD_CREDENTIALS_FILE_CONTENT_PREFIX + region.getId(), credentials);
            } catch (IOException | InvalidPathException e) {
                log.error("Cannot read credentials file {} for region {}", region.getName(), credentialsFile);
            }
        }
        return envVars;
    }

    @Override
    public Optional<InstanceTerminationState> getInstanceTerminationState(final GCPRegion region,
                                                                          final String instanceId) {
        return vmService.getTerminationState(region, instanceId);
    }

    @Override
    public void attachDisk(final GCPRegion region, final Long runId, final DiskAttachRequest request) {
        throw new UnsupportedOperationException("Disk attaching doesn't work with GCP provider yet.");
    }

    @Override
    public InstanceDNSRecord getOrCreateInstanceDNSRecord(final GCPRegion region,
                                                          final InstanceDNSRecord dnsRecord) {
        throw new UnsupportedOperationException("Creation of DNS record doesn't work with GCP provider yet.");
    }

    @Override
    public InstanceDNSRecord deleteInstanceDNSRecord(final GCPRegion region,
                                                     final InstanceDNSRecord dnsRecord) {
        throw new UnsupportedOperationException("Deletion of DNS record doesn't work with GCP provider yet.");
    }

    @Override
    public InstanceImage getInstanceImageDescription(final GCPRegion region, final String imageName) {
        return InstanceImage.EMPTY;
    }

    @Override
    public void adjustOfferRequest(final InstanceOfferRequestVO requestVO) {
    }

    @Override
    public void deleteInstanceTags(final GCPRegion region, final String runId, final Set<String> tagNames) {

    }

    @Override
    public List<InstanceDisk> loadDisks(final GCPRegion region, final Long runId) {
        return vmService.getAliveInstance(region, String.valueOf(runId)).getDisks().stream()
                .map(disk -> disk.get("diskSizeGb"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(Long::valueOf)
                .map(InstanceDisk::new)
                .collect(Collectors.toList());
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

    @Override
    public CloudInstanceState getInstanceState(final GCPRegion region, final String nodeLabel) {
        try {
            final Instance instance = vmService.findInstanceByNameTag(region, nodeLabel);
            final GCPInstanceStatus instanceStatus = GCPInstanceStatus.valueOf(instance.getStatus());
            if (GCPInstanceStatus.getWorkingStatuses().contains(instanceStatus)) {
                return CloudInstanceState.RUNNING;
            }
            if (GCPInstanceStatus.getStopStatuses().contains(instanceStatus)) {
                return CloudInstanceState.STOPPED;
            }
            if (GCPInstanceStatus.STOPPING.equals(instanceStatus)) {
                return CloudInstanceState.STOPPING;
            }
            return CloudInstanceState.TERMINATED;
        } catch (IOException | GCPException e) {
            log.error(e.getMessage(), e);
            return CloudInstanceState.TERMINATED;
        }
    }

    private String buildNodeUpCommand(final GCPRegion region, final String nodeLabel, final RunInstance instance,
                                      final Map<String, String> labels, final Map<String, String> runtimeParameters) {
        return commandService
            .buildNodeUpCommand(nodeUpScript, region, nodeLabel, instance, getProviderName(), runtimeParameters)
            .sshKey(region.getSshPublicKeyPath())
            .isSpot(Optional.ofNullable(instance.getSpot())
                        .orElse(false))
            .bidPrice(StringUtils.EMPTY)
            .additionalLabels(labels)
            .build()
            .getCommand();
    }

    private String getCredentialsFilePath(GCPRegion region) {
        return StringUtils.isEmpty(region.getAuthFile())
                ? System.getenv(GOOGLE_APPLICATION_CREDENTIALS)
                : region.getAuthFile();
    }

    private RunInstance fillRunInstanceFromGcpVm(final RunInstance instance, final Instance vm) {
        instance.setNodeId(vm.getName());
        // According to https://cloud.google.com/compute/docs/instances/custom-hostname-vm and
        // https://cloud.google.com/compute/docs/internal-dns#about_internal_dns
        // gcloud create internal dns name with form: [INSTANCE_NAME].[ZONE].c.[PROJECT_ID].internal
        instance.setNodeName(vm.getName());
        instance.setNodeIP(vm.getNetworkInterfaces().get(0).getNetworkIP());
        return instance;
    }

    private Map<String, String> buildScriptGCPEnvVars(final GCPRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        if (StringUtils.isNotBlank(region.getAuthFile())) {
            envVars.put(GOOGLE_APPLICATION_CREDENTIALS, region.getAuthFile());
        }
        envVars.put(GOOGLE_PROJECT_ID, region.getProject());
        envVars.put(SystemParams.GLOBAL_DISTRIBUTION_URL.name(), getGlobalDistributionUrl(region));
        return envVars;
    }

    private String getGlobalDistributionUrl(final AbstractCloudRegion region) {
        return Optional.ofNullable(region.getGlobalDistributionUrl())
                .orElseGet(() -> preferenceManager.getPreference(SystemPreferences.BASE_GLOBAL_DISTRIBUTION_URL));
    }
}

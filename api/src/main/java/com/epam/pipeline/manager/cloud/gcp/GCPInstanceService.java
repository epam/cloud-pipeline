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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.cloud.gcp.GCPException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cloud.CloudInstanceService;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.cloud.commands.ClusterCommandService;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    private final ParallelExecutorService executorService;
    private final CmdExecutor cmdExecutor = new CmdExecutor();

    public GCPInstanceService(final ClusterCommandService commandService,
                              final CommonCloudInstanceService instanceService,
                              final GCPVMService vmService,
                              final ParallelExecutorService executorService,
                              @Value("${cluster.gcp.nodeup.script}") final String nodeUpScript,
                              @Value("${cluster.gcp.nodedown.script}") final String nodeDownScript,
                              @Value("${cluster.gcp.reassign.script}") final String nodeReassignScript,
                              @Value("${cluster.gcp.node.terminate.script}") final String nodeTerminateScript) {
        this.commandService = commandService;
        this.instanceService = instanceService;
        this.vmService = vmService;
        this.nodeUpScript = nodeUpScript;
        this.executorService = executorService;
        this.nodeDownScript = nodeDownScript;
        this.nodeReassignScript = nodeReassignScript;
        this.nodeTerminateScript = nodeTerminateScript;
    }

    @Override
    public RunInstance scaleUpNode(final GCPRegion region, final Long runId, final RunInstance instance) {

        final String command = commandService.buildNodeUpCommand(nodeUpScript, region, runId, instance,
                getProviderName()).sshKey(region.getSshPublicKeyPath())
                .isSpot(Optional.ofNullable(instance.getSpot())
                        .orElse(false))
                .bidPrice(StringUtils.EMPTY)
                .build()
                .getCommand();
        final Map<String, String> envVars = buildScriptGCPEnvVars(region);
        return instanceService.runNodeUpScript(cmdExecutor, runId, instance, command, envVars);
    }

    @Override
    public void scaleDownNode(final GCPRegion region, final Long runId) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, runId, getProviderName());
        final Map<String, String> envVars = buildScriptGCPEnvVars(region);
        instanceService.runNodeDownScript(cmdExecutor, command, envVars);
    }

    @Override
    public void scaleUpFreeNode(final GCPRegion region, final String nodeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean reassignNode(final GCPRegion region, final Long oldId, final Long newId) {
        final String command = commandService.buildNodeReassignCommand(
                nodeReassignScript, oldId, newId, getProviderName());
        return instanceService.runNodeReassignScript(cmdExecutor, command, oldId, newId,
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
        try {
            vmService.terminateInstance(region, instanceId);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }

    @Override
    public boolean instanceExists(final GCPRegion region, final String instanceId) {
        try {
            return vmService.instanceExists(region, instanceId);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }

    @Override
    public RunInstance describeAliveInstance(final GCPRegion region, final String nodeLabel,
                                             final RunInstance instance) {
        try {
            return fillRunInstanceFromGcpVm(instance, vmService.getAliveInstance(region, nodeLabel));
        } catch (IOException e) {
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
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
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
        return envVars;
    }
}

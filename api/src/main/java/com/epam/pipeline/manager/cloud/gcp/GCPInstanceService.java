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

import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.cloud.gcp.GCPException;
import com.epam.pipeline.manager.cloud.AbstractProviderInstanceService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GCPInstanceService extends AbstractProviderInstanceService<GCPRegion> {

    private static final String GOOGLE_PROJECT_ID = "GOOGLE_PROJECT_ID";
    protected static final String GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";

    private final GCPVMService vmService;
    private final String nodeUpScript;

    public GCPInstanceService(final ClusterCommandService commandService,
                              final CommonCloudInstanceService instanceService,
                              final GCPVMService vmService,
                              final ParallelExecutorService executorService,
                              @Value("${cluster.gcp.nodeup.script}") final String nodeUpScript,
                              @Value("${cluster.gcp.nodedown.script}") final String nodeDownScript,
                              @Value("${cluster.gcp.reassign.script}") final String nodeReassignScript,
                              @Value("${cluster.gcp.node.terminate.script}") final String nodeTerminateScript) {
        super(commandService, instanceService, executorService, nodeDownScript, nodeReassignScript,
              nodeTerminateScript);
        this.vmService = vmService;
        this.nodeUpScript = nodeUpScript;
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
            return CloudInstanceState.TERMINATED;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return CloudInstanceState.TERMINATED;
        }
    }

    @Override
    protected Map<String, String> buildScriptEnvVars(GCPRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        if (StringUtils.isNotBlank(region.getAuthFile())) {
            envVars.put(GOOGLE_APPLICATION_CREDENTIALS, region.getAuthFile());
        }
        envVars.put(GOOGLE_PROJECT_ID, region.getProject());
        return envVars;
    }

    @Override
    protected String buildNodeUpCommand(final GCPRegion region, final String nodeLabel, final RunInstance instance,
                                      final Map<String, String> labels) {
        return commandService
            .buildNodeUpCommand(nodeUpScript, region, nodeLabel, instance, getProviderName())
            .sshKey(region.getSshPublicKeyPath())
            .isSpot(Optional.ofNullable(instance.getSpot())
                        .orElse(false))
            .bidPrice(StringUtils.EMPTY)
            .additionalLabels(labels)
            .prePulledImages(instance.getPrePulledDockerImages())
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
}

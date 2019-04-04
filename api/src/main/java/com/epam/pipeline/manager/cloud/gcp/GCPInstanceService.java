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
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.cloud.gcp.GCPException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cloud.CloudInstanceService;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.execution.SystemParams;
import com.google.api.services.compute.model.Instance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class GCPInstanceService implements CloudInstanceService<GCPRegion> {

    private static final String GOOGLE_PROJECT_ID = "GOOGLE_PROJECT_ID";
    private static final String GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";

    private final CommonCloudInstanceService instanceService;
    private final GCPVMService vmService;
    private final CmdExecutor cmdExecutor = new CmdExecutor();

    public GCPInstanceService(final CommonCloudInstanceService instanceService,
                                final GCPVMService vmService) {
        this.instanceService = instanceService;
        this.vmService = vmService;
    }

    @Override
    public RunInstance scaleUpNode(final GCPRegion region, final Long runId, final RunInstance instance) {

        final String command = instanceService.buildNodeUpCommonCommand(region, runId, instance,
                CloudProvider.GCP.name()).sshKey(region.getSshPublicKeyPath())
                .isSpot(Optional.ofNullable(instance.getSpot())
                        .orElse(false))
                .build()
                .getCommand();
        final Map<String, String> envVars = buildScriptGCPEnvVars(region);
        return instanceService.runNodeUpScript(cmdExecutor, runId, instance, command, envVars);
    }

    @Override
    public void scaleDownNode(final GCPRegion region, final Long runId) {
        final String command = instanceService.buildNodeDownCommand(runId, CloudProvider.GCP.name());
        final Map<String, String> envVars = buildScriptGCPEnvVars(region);
        instanceService.runNodeDownScript(cmdExecutor, command, envVars);
    }

    @Override
    public void scaleUpFreeNode(final GCPRegion region, final String nodeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void terminateNode(final GCPRegion region, final String internalIp, final String nodeName) {
        final String command = instanceService.buildTerminateNodeCommand(internalIp,
                nodeName, CloudProvider.GCP.name());
        final Map<String, String> envVars = buildScriptGCPEnvVars(region);
        instanceService.runTerminateNodeScript(command, cmdExecutor, envVars);
    }

    @Override
    public void startInstance(final GCPRegion region, final String instanceId) {
        vmService.startInstance(region, instanceId);
    }

    @Override
    public void stopInstance(final GCPRegion region, final String instanceId) {
        vmService.stopInstance(region, instanceId);
    }

    @Override
    public LocalDateTime getNodeLaunchTime(final GCPRegion region, final Long runId) {
        return instanceService.getNodeLaunchTimeFromKube(runId);
    }

    @Override
    public RunInstance describeInstance(final GCPRegion region, final String nodeLabel, final RunInstance instance) {
        try {
            final Instance vm = vmService.getRunningInstanceByRunId(region, nodeLabel);
            instance.setNodeId(vm.getName());
            // According to https://cloud.google.com/compute/docs/instances/custom-hostname-vm and
            // https://cloud.google.com/compute/docs/internal-dns#about_internal_dns
            // gcloud create internal dns name with form: [INSTANCE_NAME].[ZONE].c.[PROJECT_ID].internal
            instance.setNodeName(vm.getName());
            instance.setNodeIP(vm.getNetworkInterfaces().get(0).getNetworkIP());
            return instance;
        } catch (GCPException e) {
            log.error("An error while getting instance description {}", nodeLabel);
            return null;
        }
    }

    @Override
    public boolean reassignNode(final GCPRegion region, final Long oldId, final Long newId) {
        return instanceService.runNodeReassignScript(oldId, newId,
                CloudProvider.GCP.name(), cmdExecutor, buildScriptGCPEnvVars(region));
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(final GCPRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(SystemParams.CLOUD_REGION_PREFIX + region.getId(), region.getRegionCode());
        envVars.put(SystemParams.CLOUD_PROVIDER_PREFIX + region.getId(), region.getProvider().name());
        //TODO add creds for gcloud to be able to work with storages in the container
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

    private Map<String, String> buildScriptGCPEnvVars(final GCPRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        if (StringUtils.isNotBlank(region.getAuthFile())) {
            envVars.put(GOOGLE_APPLICATION_CREDENTIALS, region.getAuthFile());
        }
        envVars.put(GOOGLE_PROJECT_ID, region.getProject());
        return envVars;
    }
}

/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud.local;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.InstanceImage;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.LocalRegion;
import com.epam.pipeline.manager.cloud.CloudInstanceService;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LocalInstanceService implements CloudInstanceService<LocalRegion> {

    private final MessageHelper messageHelper;
    private final KubernetesManager kubernetesManager;
    @Override
    public CloudProvider getProvider() {
        return CloudProvider.LOCAL;
    }

    @Override
    public RunInstance scaleUpNode(final LocalRegion region,
                                   final Long runId,
                                   final RunInstance instance,
                                   final Map<String, String> runtimeParameters,
                                   final Map<String, String> customTags) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public RunInstance scaleUpPoolNode(final LocalRegion region,
                                       final String nodeId,
                                       final NodePool node) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public void scaleDownNode(final LocalRegion region, final Long runId) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public void scaleDownPoolNode(LocalRegion region, String nodeLabel) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public void terminateNode(LocalRegion region, String internalIp, String nodeName) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public CloudInstanceOperationResult startInstance(LocalRegion region, String instanceId) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public void stopInstance(LocalRegion region, String instanceId) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public void terminateInstance(LocalRegion region, String instanceId) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public boolean instanceExists(LocalRegion region, String instanceId) {
        return false;
    }

    @Override
    public LocalDateTime getNodeLaunchTime(LocalRegion region, Long runId) {
        return null;
    }

    @Override
    public RunInstance describeInstance(LocalRegion region, String nodeLabel, RunInstance instance) {
        return null;
    }

    @Override
    public RunInstance describeAliveInstance(LocalRegion region, String nodeLabel, RunInstance instance) {
        return null;
    }

    @Override
    public boolean reassignNode(final LocalRegion region, final Long oldId, final Long newId,
                                final Map<String, String> customTags) {
        return reassignKubeNode(String.valueOf(oldId), String.valueOf(newId));
    }

    @Override
    public boolean reassignPoolNode(final LocalRegion region, final String nodeLabel, final Long newId,
                                    final Map<String, String> customTags) {
        return reassignKubeNode(nodeLabel, String.valueOf(newId));
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(LocalRegion region) {
        return new HashMap<>();
    }

    @Override
    public Optional<InstanceTerminationState> getInstanceTerminationState(LocalRegion region, String instanceId) {
        return Optional.empty();
    }

    @Override
    public void attachDisk(LocalRegion region, Long runId, DiskAttachRequest request) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_SCALING_LOCAL_CLUSTER));
    }

    @Override
    public List<InstanceDisk> loadDisks(final LocalRegion region, final Long runId) {
        return Collections.emptyList();
    }

    @Override
    public CloudInstanceState getInstanceState(LocalRegion region, String nodeLabel) {
        return null;
    }

    @Override
    public InstanceDNSRecord getOrCreateInstanceDNSRecord(LocalRegion region, InstanceDNSRecord record) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_DNS_LOCAL_CLUSTER));
    }

    @Override
    public InstanceDNSRecord deleteInstanceDNSRecord(LocalRegion region, InstanceDNSRecord record) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_DNS_LOCAL_CLUSTER));
    }

    @Override
    public InstanceImage getInstanceImageDescription(LocalRegion region, String imageName) {
        return null;
    }

    @Override
    public void adjustOfferRequest(InstanceOfferRequestVO requestVO) {
        //pass
    }

    @Override
    public void deleteInstanceTags(final LocalRegion region, final String instanceId, final Set<String> tagNames) {

    }

    private boolean reassignKubeNode(final String oldLabel, final String newLabel) {
        return kubernetesManager.findNodeByRunId(oldLabel)
                .map(node -> {
                    kubernetesManager.addNodeLabel(
                            node.getMetadata().getName(), KubernetesConstants.RUN_ID_LABEL, newLabel);
                    return true;
                })
                .orElse(false);
    }
}

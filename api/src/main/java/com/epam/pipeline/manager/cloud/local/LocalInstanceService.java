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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LocalInstanceService implements CloudInstanceService<LocalRegion> {

    private final MessageHelper messageHelper;
    @Override
    public CloudProvider getProvider() {
        return CloudProvider.LOCAL;
    }

    @Override
    public RunInstance scaleUpNode(final LocalRegion region,
                                   final Long runId,
                                   final RunInstance instance,
                                   final Map<String, String> runtimeParameters) {
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
    public boolean reassignNode(LocalRegion region, Long oldId, Long newId) {
        return false;
    }

    @Override
    public boolean reassignPoolNode(LocalRegion region, String nodeLabel, Long newId) {
        return false;
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(LocalRegion region) {
        return Collections.emptyMap();
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
    public List<InstanceDisk> loadDisks(LocalRegion region, Long runId) {
        return null;
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
}

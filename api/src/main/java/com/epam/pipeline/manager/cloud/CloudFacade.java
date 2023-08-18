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

package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.InstanceImage;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.NodeRegionLabels;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CloudFacade {
    RunInstance scaleUpNode(Long runId, RunInstance instance, Map<String, String> runtimeParameters);

    RunInstance scaleUpPoolNode(String nodeId, NodePool node);

    void scaleDownNode(Long runId);

    void scaleDownPoolNode(String nodeLabel);

    void terminateNode(AbstractCloudRegion region, String internalIp, String nodeName);

    boolean isNodeExpired(Long runId);

    boolean reassignNode(Long oldId, Long newId);

    boolean reassignPoolNode(String nodeLabel, Long newId);

    /**
     * Fills in provider related data for running instance associated with run,
     * otherwise returns {@code null}.
     */
    RunInstance describeInstance(Long runId, RunInstance instance);

    /**
     * Fills in provider related data for running or paused instance associated with run,
     * otherwise returns {@code null}.
     */
    RunInstance describeAliveInstance(Long runId, RunInstance instance);

    RunInstance describeDefaultInstance(String nodeLabel, RunInstance instance);

    void stopInstance(Long regionId, String instanceId);

    CloudInstanceOperationResult startInstance(Long regionId, String instanceId);

    void terminateInstance(Long regionId, String instanceId);

    boolean instanceExists(Long regionId, String instanceId);

    boolean instanceExists(NodeRegionLabels nodeRegion, String instanceId);

    Map<String, String> buildContainerCloudEnvVars(Long regionId);

    List<InstanceOffer> refreshPriceListForRegion(Long regionId);

    double getPriceForDisk(Long regionId, List<InstanceOffer> diskOffers, int instanceDisk, String instanceType,
                           boolean spot);

    double getSpotPrice(Long regionId, String instanceType);

    Optional<InstanceTerminationState> getInstanceTerminationState(Long regionId, String instanceId);

    Optional<InstanceTerminationState> getInstanceTerminationState(NodeRegionLabels nodeRegion, String instanceId);

    List<InstanceType> getAllInstanceTypes(Long regionId, boolean spot);

    /**
     * Creates and attaches new disk by the given request to an instance associated with run.
     */
    void attachDisk(Long regionId, Long runId, DiskAttachRequest request);

    /**
     * Loads all disks attached to an instance associated with run including os, data and swap disks.
     */
    List<InstanceDisk> loadDisks(Long regionId, Long runId);

    CloudInstanceState getInstanceState(Long runId);

    InstanceDNSRecord createDNSRecord(Long regionId, InstanceDNSRecord record);

    InstanceDNSRecord removeDNSRecord(Long regionId, InstanceDNSRecord record);

    InstanceImage getInstanceImageDescription(Long regionId, String imageId);
}

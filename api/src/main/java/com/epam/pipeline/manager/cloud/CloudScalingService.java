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

package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.cluster.autoscale.AutoscalerServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

public interface CloudScalingService<T extends AbstractCloudRegion> extends CloudAwareService {

    Logger log = LoggerFactory.getLogger(AutoscalerServiceImpl.class);

    /**
     * Creates new instance using specified cloud and adds it to cluster
     * @param runId
     * @param region
     * @param instance
     * @return
     */
    RunInstance scaleUpNode(T region, Long runId, RunInstance instance);
    RunInstance scaleUpPoolNode(T region, String nodeId, NodePool node);

    /**
     * Terminates instances in the cloud and removes it from cluster
     * @param region
     * @param runId
     */
    void scaleDownNode(T region, Long runId);
    void scaleDownPoolNode(T region, String nodeLabel);

    /**
     * Terminates instances in the cloud and removes it from the cluster
     * @param internalIp
     * @param nodeName
     */
    void terminateNode(T region, String internalIp, String nodeName);

    /**
     * Returns date time of node launch
     * @param region
     * @param runId
     * @return
     */
    LocalDateTime getNodeLaunchTime(T region, Long runId);

    /**
     * Reassigns node from one run to a new one
     * @param oldId
     * @param newId
     * @return {@code true} if operation was successful
     */
    boolean reassignNode(T region, Long oldId, Long newId);
    boolean reassignPoolNode(T region, String nodeLabel, Long newId);

    /**
     * Builds environment variables required for running a container in provided region
     * @param region
     */
    Map<String, String> buildContainerCloudEnvVars(T region);
}

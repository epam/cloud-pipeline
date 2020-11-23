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

package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.cluster.AutoscalerServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CloudInstanceService<T extends AbstractCloudRegion>
        extends CloudAwareService {

    Logger log = LoggerFactory.getLogger(AutoscalerServiceImpl.class);
    int TIME_DELIMITER = 60;
    int TIME_TO_SHUT_DOWN_NODE = 1;

    /**
     * Creates new instance using specified cloud and adds it to cluster
     * @param runId
     * @param region
     * @param instance
     * @return
     */
    RunInstance scaleUpNode(T region, Long runId, RunInstance instance);

    RunInstance scaleUpPersistentNode(T region, String nodeId, PersistentNode node);

    /**
     * Terminates instances in the cloud and removes it from cluster
     * @param region
     * @param runId
     */
    void scaleDownNode(T region, Long runId);

    /**
     * Terminates instances in the cloud and removes it from the cluster
     * @param internalIp
     * @param nodeName
     */
    void terminateNode(T region, String internalIp, String nodeName);

    /**
     * Starts previously stopped cloud instance
     * @param instanceId
     * @return CloudInstanceOperationResult object as result of operation. This object contains status and massage.
     */
    CloudInstanceOperationResult startInstance(T region, String instanceId);

    /**
     * Pauses cloud instance
     * @param instanceId
     */
    void stopInstance(T region, String instanceId);

    /**
     * Terminates cloud instance
     * @param region
     * @param instanceId
     */
    void terminateInstance(T region, String instanceId);

    /**
     * Checks if cloud instance exists
     * @param region
     * @param instanceId
     * @return
     */
    boolean instanceExists(T region, String instanceId);

    /**
     * Returns date time of node launch
     * @param region
     * @param runId
     * @return
     */
    LocalDateTime getNodeLaunchTime(T region, Long runId);

    /**
     * Fills in provider related data for running instance associated with label,
     * if it exists, otherwise returns {@code null}
     * @param region
     * @param nodeLabel
     * @param instance
     * @return
     */
    RunInstance describeInstance(T region, String nodeLabel, RunInstance instance);

    /**
     * Fills in provider related data for running or paused instance associated with label,
     * if it exists, otherwise returns {@code null}
     * @param region
     * @param nodeLabel
     * @param instance
     * @return
     */
    RunInstance describeAliveInstance(T region, String nodeLabel, RunInstance instance);

    /**
     * Reassigns node from one run to a new one
     * @param oldId
     * @param newId
     * @return {@code true} if operation was successful
     */
    boolean reassignNode(T region, Long oldId, Long newId);

    /**
     * Builds environment variables required for running a container in provided region
     * @param region
     */
    Map<String, String> buildContainerCloudEnvVars(T region);

    // TODO: this logic is moved to NodeExpirationService class, remove this method
    //  if it is not needed anymore
    default boolean isNodeExpired(T region, Long runId, Integer keepAliveMinutes) {
        if (keepAliveMinutes == null) {
            return true;
        }
        try {
            log.debug("Getting node launch time.");
            LocalDateTime launchTime = getNodeLaunchTime(region, runId);
            if (launchTime == null) {
                return true;
            }
            log.debug("Node {} launch time {}.", runId, launchTime);
            LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
            long aliveTime = Duration.between(launchTime, now).getSeconds() / TIME_DELIMITER;
            log.debug("Node {} is alive for {} minutes.", runId, aliveTime);
            long minutesToWholeHour = aliveTime % TIME_DELIMITER;
            long minutesLeft = TIME_DELIMITER - minutesToWholeHour;
            log.debug("Node {} has {} minutes left until next hour.", runId, minutesLeft);
            return minutesLeft <= keepAliveMinutes && minutesLeft > TIME_TO_SHUT_DOWN_NODE;
        } catch (DateTimeParseException e) {
            log.error(e.getMessage(), e);
            return true;
        }
    }

    /**
     * @param region
     * @param instanceId
     * @return description and reason for current instance state,
     *  mainly for terminated states
     */
    Optional<InstanceTerminationState> getInstanceTerminationState(T region, String instanceId);

    /**
     * Creates and attaches new disk by the given request to cloud instance.
     * @param region
     * @param runId
     * @param request
     */
    void attachDisk(T region, Long runId, DiskAttachRequest request);

    /**
     * Loads all disks attached to cloud instance.
     * @param region
     * @param runId
     * @return
     */
    List<InstanceDisk> loadDisks(T region, Long runId);

    CloudInstanceState getInstanceState(T region, String nodeLabel);
}

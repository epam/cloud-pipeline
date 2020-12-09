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

import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;

import java.util.List;
import java.util.Optional;

public interface CloudInstanceService<T extends AbstractCloudRegion> extends CloudAwareService {


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

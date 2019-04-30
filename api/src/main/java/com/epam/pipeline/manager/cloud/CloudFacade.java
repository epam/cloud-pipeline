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

import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CloudFacade {
    RunInstance scaleUpNode(Long runId, RunInstance instance);

    void scaleUpFreeNode(String nodeId);

    void scaleDownNode(Long runId);

    void terminateNode(AbstractCloudRegion region, String internalIp, String nodeName);

    boolean isNodeExpired(Long runId);

    boolean reassignNode(Long oldId, Long newId);

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

    void startInstance(Long regionId, String instanceId);

    void terminateInstance(Long regionId, String instanceId);

    boolean instanceExists(Long regionId, String instanceId);

    Map<String, String> buildContainerCloudEnvVars(Long regionId);

    List<InstanceOffer> refreshPriceListForRegion(Long regionId);

    double getPriceForDisk(Long regionId, List<InstanceOffer> diskOffers, int instanceDisk, String instanceType);

    double getSpotPrice(Long regionId, String instanceType);

    Optional<InstanceTerminationState> getInstanceTerminationState(Long regionId, String instanceId);
}

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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.RunInstance;

public interface ClusterManager {

    RunInstance scaleUp(String runId, RunInstance instance);
    void scaleUpFreeNode(String nodeToCreate);
    void scaleDown(String runId);
    boolean isNodeExpired(String runId);

    /**
     * Return true if operation succeeded
     * @param oldId
     * @param newId
     * @return
     */
    boolean reassignNode(String oldId, String newId);
    boolean requirementsMatch(RunInstance instanceOld, RunInstance instanceNew);
    RunInstance getDefaultInstance();
    RunInstance configurationToInstance(PipelineConfiguration configuration);
    RunInstance describeInstance(String runId, RunInstance instance);
    RunInstance fillInstance(RunInstance instance);
    void stopInstance(String instanceId, String awsRegion);
    void startInstance(String instanceId, String awsRegion);
}

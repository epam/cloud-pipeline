/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.autoscale;

import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.RunInstance;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;
import java.util.Optional;

/**
 * Provides helper nethods for {@link AutoscaleManager}
 */
public interface AutoscalerService {

    boolean requirementsMatch(RunningInstance instanceOld, InstanceRequest instanceNew);
    boolean requirementsMatchWithImages(RunningInstance instanceOld, InstanceRequest instanceNew);
    RunInstance configurationToInstance(PipelineConfiguration configuration);
    RunInstance fillInstance(RunInstance instance);
    RunningInstance getPreviousRunInstance(String nodeLabel, KubernetesClient client);
    void adjustRunPrices(long runId, List<InstanceDisk> disks);
    Optional<NodePool> findPool(String nodeLabel, KubernetesClient client);
    void registerDisks(Long runId, RunInstance instance);
}

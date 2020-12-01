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

import com.epam.pipeline.entity.cluster.DiskRegistrationRequest;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.NodeDiskManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class AutoscalerServiceImpl implements AutoscalerService {

    private final PreferenceManager preferenceManager;
    private final CloudRegionManager cloudRegionManager;
    private final PipelineRunManager pipelineRunManager;
    private final NodePoolManager nodePoolManager;
    private final CloudFacade cloudFacade;
    private final NodeDiskManager nodeDiskManager;

    @Override
    public boolean requirementsMatch(final RunningInstance instanceOld,
                                     final InstanceRequest instanceNew) {
        if (instanceOld == null || instanceNew == null) {
            return false;
        }
        final Integer diskDelta = preferenceManager.getPreference(SystemPreferences.CLUSTER_REASSIGN_DISK_DELTA);
        return instanceOld.getInstance().requirementsMatch(instanceNew.getInstance(), diskDelta);
    }

    @Override
    public boolean requirementsMatchWithImages(final RunningInstance instanceOld,
                                               final InstanceRequest instanceNew) {
        return requirementsMatch(instanceOld, instanceNew) && instanceOld.getPrePulledImages()
                .contains(instanceNew.getRequestedImage());
    }

    @Override
    public RunInstance configurationToInstance(PipelineConfiguration configuration) {
        RunInstance instance = new RunInstance();
        if (configuration.getInstanceType() == null) {
            instance.setNodeType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        } else {
            instance.setNodeType(configuration.getInstanceType());
        }
        if (configuration.getInstanceDisk() == null) {
            instance.setNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        } else {
            instance.setNodeDisk(Integer.parseInt(configuration.getInstanceDisk()));
        }
        instance.setEffectiveNodeDisk(instance.getNodeDisk());
        instance.setNodeImage(configuration.getInstanceImage());
        instance.setCloudRegionId(Optional.ofNullable(configuration.getCloudRegionId())
                .map(cloudRegionManager::load)
                .orElse(cloudRegionManager.loadDefaultRegion())
                .getId());
        return instance;
    }

    @Override
    public RunInstance fillInstance(RunInstance instance) {
        if (instance.getNodeType() == null) {
            instance.setNodeType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        }
        if (instance.getNodeDisk() == null) {
            instance.setNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
            instance.setEffectiveNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        }
        if (instance.getCloudRegionId() == null) {
            instance.setCloudRegionId(cloudRegionManager.loadDefaultRegion().getId());
        }
        return instance;
    }

    public RunningInstance getPreviousRunInstance(final String nodeLabel, final KubernetesClient client) {
        return findPool(nodeLabel, client)
                .map(NodePool::toRunningInstance)
                .orElseGet(() -> {
                    try {
                        final PipelineRun run = pipelineRunManager.loadPipelineRun(Long.parseLong(nodeLabel));
                        final RunningInstance runningInstance = new RunningInstance();
                        runningInstance.setInstance(run.getInstance());
                        runningInstance.setPrePulledImages(Collections.singleton(run.getActualDockerImage()));
                        return runningInstance;
                    } catch (IllegalArgumentException e) {
                        log.trace(e.getMessage(), e);
                        return null;
                    }
                });
    }

    public Optional<NodePool> findPool(final String nodeLabel, final KubernetesClient client) {
        return findPoolNodeId(nodeLabel, client)
                .flatMap(nodePoolManager::find);
    }

    public Optional<Long> findPoolNodeId(final String nodeLabel, final KubernetesClient client) {
        final NodeList nodes = client.nodes()
                .withLabel(KubernetesConstants.RUN_ID_LABEL, nodeLabel)
                .withoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
                .withLabel(KubernetesConstants.NODE_POOL_ID_LABEL)
                .list();
        return ListUtils.emptyIfNull(nodes.getItems())
                .stream()
                .map(node -> MapUtils.emptyIfNull(node.getMetadata().getLabels())
                        .get(KubernetesConstants.NODE_POOL_ID_LABEL))
                .filter(NumberUtils::isDigits)
                .map(Long::parseLong)
                .findFirst();
    }

    public void adjustRunPrices(final long runId, final List<InstanceDisk> disks) {
        pipelineRunManager.adjustRunPricePerHourToDisks(runId, disks);
    }

    public void registerDisks(final Long runId, final RunInstance instance) {
        List<InstanceDisk> disks = cloudFacade.loadDisks(instance.getCloudRegionId(), runId);
        registerNodeDisks(runId, disks);
        adjustRunPrices(runId, disks);
    }

    private void registerNodeDisks(long runId, List<InstanceDisk> disks) {
        PipelineRun run = pipelineRunManager.loadPipelineRun(runId);
        String nodeId = run.getInstance().getNodeId();
        LocalDateTime creationDate = DateUtils.convertDateToLocalDateTime(run.getStartDate());
        List<DiskRegistrationRequest> requests = DiskRegistrationRequest.from(disks);
        nodeDiskManager.register(nodeId, creationDate, requests);
    }
}

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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.NodeRegionLabels;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@SuppressWarnings("unchecked")
public class CloudFacadeImpl implements CloudFacade {

    private final MessageHelper messageHelper;
    private final CloudRegionManager regionManager;
    private final PreferenceManager preferenceManager;
    private final PipelineRunManager pipelineRunManager;
    private final KubernetesManager kubernetesManager;
    private final Map<CloudProvider, CloudInstanceService> instanceServices;
    private final Map<CloudProvider, CloudInstancePriceService> instancePriceServices;

    public CloudFacadeImpl(final MessageHelper messageHelper,
                           final CloudRegionManager regionManager,
                           final PreferenceManager preferenceManager,
                           final PipelineRunManager pipelineRunManager,
                           final KubernetesManager kubernetesManager,
                           final List<CloudInstanceService> instanceServices,
                           final List<CloudInstancePriceService> instancePriceServices) {
        this.messageHelper = messageHelper;
        this.regionManager = regionManager;
        this.preferenceManager = preferenceManager;
        this.pipelineRunManager = pipelineRunManager;
        this.kubernetesManager = kubernetesManager;
        this.instanceServices = CommonUtils.groupByCloudProvider(instanceServices);
        this.instancePriceServices = CommonUtils.groupByCloudProvider(instancePriceServices);
    }

    @Override
    public RunInstance scaleUpNode(final Long runId, final RunInstance instance) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(instance.getCloudRegionId());
        return getInstanceService(region).scaleUpNode(region, runId, instance);
    }

    @Override
    public void scaleUpFreeNode(final String nodeId) {
        AbstractCloudRegion defaultRegion = regionManager.loadDefaultRegion();
        getInstanceService(defaultRegion).scaleUpFreeNode(defaultRegion, nodeId);
    }

    @Override
    public void scaleDownNode(final Long runId) {
        final AbstractCloudRegion region = getRegionByRunId(runId);
        getInstanceService(region).scaleDownNode(region, runId);
    }

    @Override
    public void terminateNode(final AbstractCloudRegion region, final String internalIp, final String nodeName) {
        getInstanceService(region).terminateNode(region, internalIp, nodeName);
    }

    @Override
    public boolean isNodeExpired(final Long runId) {
        final AbstractCloudRegion region = getRegionByRunId(runId);
        return getInstanceService(region).isNodeExpired(region, runId,
                preferenceManager.getPreference(SystemPreferences.CLUSTER_KEEP_ALIVE_MINUTES));
    }

    @Override
    public boolean reassignNode(final Long oldId, final Long newId) {
        final AbstractCloudRegion region = getRegionByRunId(oldId);
        return getInstanceService(region).reassignNode(region, oldId, newId);
    }

    @Override
    public RunInstance describeInstance(final Long runId, final RunInstance instance) {
        final AbstractCloudRegion region = getRegionByRunId(runId);
        return getInstanceService(region)
                .describeInstance(region, String.valueOf(runId), instance);
    }

    @Override
    public RunInstance describeAliveInstance(final Long runId, final RunInstance instance) {
        final AbstractCloudRegion region = getRegionByRunId(runId);
        return getInstanceService(region).describeAliveInstance(region, String.valueOf(runId), instance);
    }

    @Override
    public RunInstance describeDefaultInstance(final String nodeLabel, final RunInstance instance) {
        final AbstractCloudRegion region = regionManager.loadDefaultRegion();
        return getInstanceService(region).describeInstance(region, nodeLabel, instance);
    }

    @Override
    public void stopInstance(final Long regionId, final String instanceId) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        getInstanceService(region).stopInstance(region, instanceId);
    }

    @Override
    public CloudInstanceOperationResult startInstance(final Long regionId, final String instanceId) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstanceService(region).startInstance(region, instanceId);
    }

    @Override
    public void terminateInstance(final Long regionId, final String instanceId) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        getInstanceService(region).terminateInstance(region, instanceId);
    }

    @Override
    public boolean instanceExists(final Long regionId, final String instanceId) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstanceService(region).instanceExists(region, instanceId);
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(final Long regionId) {
        return regionManager.loadAll().stream()
                .map(r -> {
                    final Map<String, String> envVars = getInstanceService(r)
                            .buildContainerCloudEnvVars(r);
                    if (r.getId().equals(regionId)) {
                        envVars.put(SystemParams.CLOUD_PROVIDER.name(), r.getProvider().name());
                        envVars.put(SystemParams.CLOUD_REGION.name(), r.getRegionCode());
                        envVars.put(SystemParams.CLOUD_REGION_ID.name(), String.valueOf(r.getId()));
                    }
                    return envVars;
                })
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2));
    }

    @Override
    public Optional<InstanceTerminationState> getInstanceTerminationState(
            final Long regionId, final String instanceId) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstanceService(region).getInstanceTerminationState(region, instanceId);
    }

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final Long regionId) {
        final AbstractCloudRegion region = regionManager.load(regionId);
        return getInstancePriceService(region).refreshPriceListForRegion(region);
    }

    @Override
    public double getPriceForDisk(final Long regionId, final List<InstanceOffer> diskOffers, final int instanceDisk,
                                 final String instanceType) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstancePriceService(region).getPriceForDisk(diskOffers, instanceDisk, instanceType, region);
    }

    @Override
    public double getSpotPrice(final Long regionId, final String instanceType) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstancePriceService(region).getSpotPrice(instanceType, region);
    }


    private AbstractCloudRegion getRegionByRunId(final Long runId) {
        try {
            final PipelineRun run = pipelineRunManager.loadPipelineRun(runId);
            return regionManager
                    .load(run.getInstance().getCloudRegionId());
        } catch (IllegalArgumentException e) {
            log.trace(e.getMessage(), e);
            log.debug("RunID {} was not found. Trying to get instance details from Node", runId);
            final NodeRegionLabels nodeRegion = kubernetesManager.getNodeRegion(String.valueOf(runId));
            return regionManager.load(nodeRegion.getCloudProvider(), nodeRegion.getRegionCode());
        }
    }

    private CloudInstanceService getInstanceService(final AbstractCloudRegion region) {
        return Optional.ofNullable(MapUtils.emptyIfNull(instanceServices).get(region.getProvider()))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(
                                MessageConstants.ERROR_CLOUD_PROVIDER_NOT_SUPPORTED, region.getProvider())));
    }

    private CloudInstancePriceService getInstancePriceService(final AbstractCloudRegion region) {
        return Optional.ofNullable(MapUtils.emptyIfNull(instancePriceServices).get(region.getProvider()))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(
                                MessageConstants.ERROR_CLOUD_PROVIDER_NOT_SUPPORTED, region.getProvider())));
    }
}

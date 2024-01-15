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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.ClusterKeepAlivePolicy;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.NodeRegionLabels;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.alive.policy.NodeExpirationService;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final PipelineRunCRUDService runCRUDService;
    private final KubernetesManager kubernetesManager;
    private final Map<CloudProvider, CloudInstanceService> instanceServices;
    private final Map<CloudProvider, CloudInstancePriceService> instancePriceServices;
    private final Map<ClusterKeepAlivePolicy, NodeExpirationService> expirationServices;

    public CloudFacadeImpl(final MessageHelper messageHelper,
                           final CloudRegionManager regionManager,
                           final PreferenceManager preferenceManager,
                           final PipelineRunCRUDService runCRUDService,
                           final KubernetesManager kubernetesManager,
                           final List<CloudInstanceService> instanceServices,
                           final List<CloudInstancePriceService> instancePriceServices,
                           final List<NodeExpirationService> expirationServices) {
        this.messageHelper = messageHelper;
        this.regionManager = regionManager;
        this.preferenceManager = preferenceManager;
        this.runCRUDService = runCRUDService;
        this.kubernetesManager = kubernetesManager;
        this.instanceServices = CommonUtils.groupByCloudProvider(instanceServices);
        this.instancePriceServices = CommonUtils.groupByCloudProvider(instancePriceServices);
        this.expirationServices = CommonUtils.groupByKey(expirationServices, NodeExpirationService::policy);
    }

    @Override
    public RunInstance scaleUpNode(final Long runId, final RunInstance instance,
                                   final Map<String, String> runtimeParameters) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(instance.getCloudRegionId());
        return getInstanceService(region).scaleUpNode(region, runId, instance, runtimeParameters);
    }

    @Override
    public RunInstance scaleUpPoolNode(final String nodeId, final NodePool node) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(node.getRegionId());
        return getInstanceService(region).scaleUpPoolNode(region, nodeId, node);
    }

    @Override
    public void scaleDownNode(final Long runId) {
        final AbstractCloudRegion region = getRegionByRunId(runId);
        getInstanceService(region).scaleDownNode(region, runId);
    }

    @Override
    public void scaleDownPoolNode(final String nodeLabel) {
        final AbstractCloudRegion region = loadRegionFromNodeLabels(nodeLabel);
        getInstanceService(region).scaleDownPoolNode(region, nodeLabel);
    }

    @Override
    public void terminateNode(final AbstractCloudRegion region, final String internalIp, final String nodeName) {
        getInstanceService(region).terminateNode(region, internalIp, nodeName);
    }

    @Override
    public boolean isNodeExpired(final Long runId) {
        final String preference = preferenceManager.getPreference(SystemPreferences.CLUSTER_KEEP_ALIVE_POLICY);
        final ClusterKeepAlivePolicy clusterKeepAlivePolicy = CommonUtils.getEnumValueOrDefault(
                preference, ClusterKeepAlivePolicy.MINUTES_TILL_HOUR);
        final AbstractCloudRegion region = getRegionByRunId(runId);
        final LocalDateTime nodeLaunchTime = getInstanceService(region).getNodeLaunchTime(region, runId);
        return Optional.ofNullable(MapUtils.emptyIfNull(expirationServices)
                .get(clusterKeepAlivePolicy))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_KEEP_ALIVE_POLICY_NOT_SUPPORTED,
                                clusterKeepAlivePolicy.name())))
                .isNodeExpired(runId, nodeLaunchTime);
    }

    @Override
    public boolean reassignNode(final Long oldId, final Long newId) {
        final AbstractCloudRegion region = getRegionByRunId(oldId);
        return getInstanceService(region).reassignNode(region, oldId, newId);
    }

    @Override
    public boolean reassignPoolNode(final String nodeLabel, final Long newId) {
        final AbstractCloudRegion region = loadRegionFromNodeLabels(nodeLabel);
        return getInstanceService(region).reassignPoolNode(region, nodeLabel, newId);
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
                        envVars.put(SystemParams.GLOBAL_DISTRIBUTION_URL.name(), getGlobalDistributionUrl(r));
                    }
                    return envVars;
                })
                .flatMap(map -> map.entrySet().stream())
                .filter(entry -> {
                    if (entry.getValue() == null) {
                        log.warn("Cloud environment variable {} is null.", entry.getKey());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2));
    }

    private String getGlobalDistributionUrl(final AbstractCloudRegion region) {
        return Optional.ofNullable(region.getGlobalDistributionUrl())
                .orElseGet(() -> preferenceManager.getPreference(SystemPreferences.BASE_GLOBAL_DISTRIBUTION_URL));
    }

    @Override
    public Optional<InstanceTerminationState> getInstanceTerminationState(
            final Long regionId, final String instanceId) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstanceService(region).getInstanceTerminationState(region, instanceId);
    }

    @Override
    public List<InstanceType> getAllInstanceTypes(final Long regionId, final boolean spot) {
        if (regionId == null) {
            return loadInstancesForAllRegions(spot);
        } else {
            final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
            return getInstancePriceService(region).getAllInstanceTypes(region.getId(), spot);
        }
    }

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final Long regionId) {
        final AbstractCloudRegion region = regionManager.load(regionId);
        return getInstancePriceService(region).refreshPriceListForRegion(region);
    }

    @Override
    public double getPriceForDisk(final Long regionId, final List<InstanceOffer> diskOffers, final int instanceDisk,
                                  final String instanceType, final boolean spot) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstancePriceService(region).getPriceForDisk(diskOffers, instanceDisk, instanceType, spot, region);
    }

    @Override
    public double getSpotPrice(final Long regionId, final String instanceType) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstancePriceService(region).getSpotPrice(instanceType, region);
    }

    @Override
    public void attachDisk(final Long regionId, final Long runId, final DiskAttachRequest request) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        getInstanceService(region).attachDisk(region, runId, request);
    }

    @Override
    public List<InstanceDisk> loadDisks(final Long regionId, final Long runId) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstanceService(region).loadDisks(region, runId);
    }

    @Override
    public CloudInstanceState getInstanceState(final Long runId) {
        final AbstractCloudRegion region = getRegionByRunId(runId);
        return getInstanceService(region).getInstanceState(region, String.valueOf(runId));
    }

    @Override
    public InstanceDNSRecord createDNSRecord(final Long regionId, final InstanceDNSRecord record) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstanceService(region).getOrCreateInstanceDNSRecord(region, record);
    }

    @Override
    public InstanceDNSRecord removeDNSRecord(final Long regionId, final InstanceDNSRecord record) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return getInstanceService(region).deleteInstanceDNSRecord(region, record);
    }

    @Override
    public boolean reassignKubeNode(final String previousNodeId, final String newNodeId) {
        return kubernetesManager.findNodeByRunId(previousNodeId)
                .map(node -> {
                    kubernetesManager.addNodeLabel(
                            node.getMetadata().getName(), KubernetesConstants.RUN_ID_LABEL, newNodeId);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public boolean instanceScalingSupported(final Long regionId) {
        final AbstractCloudRegion region = regionManager.loadOrDefault(regionId);
        return region.getProvider() != CloudProvider.LOCAL;
    }

    private AbstractCloudRegion getRegionByRunId(final Long runId) {
        try {
            final PipelineRun run = runCRUDService.loadRunById(runId);
            return regionManager
                    .load(run.getInstance().getCloudRegionId());
        } catch (IllegalArgumentException e) {
            log.trace(e.getMessage(), e);
            log.debug("RunID {} was not found. Trying to get instance details from Node", runId);
            return loadRegionFromNodeLabels(String.valueOf(runId));
        }
    }

    private AbstractCloudRegion loadRegionFromNodeLabels(final String nodeLabel) {
        final NodeRegionLabels nodeRegion = kubernetesManager.getNodeRegion(nodeLabel);
        return regionManager.load(nodeRegion.getCloudProvider(), nodeRegion.getRegionCode());
    }

    private List<InstanceType> loadInstancesForAllRegions(final Boolean spot) {
        return (List<InstanceType>) instancePriceServices.values()
                .stream()
                .map(priceService -> priceService.getAllInstanceTypes(null, spot))
                .flatMap(cloudInstanceTypes -> cloudInstanceTypes.stream())
                .collect(Collectors.toList());
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

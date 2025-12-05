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

package com.epam.pipeline.acl.cluster;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MachineType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.NodeResources;
import com.epam.pipeline.entity.cluster.PodDescription;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMetricsGranularity;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.platform.network.NetworkEventFilter;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramBin;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramType;
import com.epam.pipeline.entity.pipeline.run.RunInfo;
import com.epam.pipeline.manager.cluster.EdgeServiceManager;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.MonitoringReportType;
import com.epam.pipeline.manager.cluster.NodeDiskManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.PodsManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_GENERAL_USER;
import static com.epam.pipeline.security.acl.AclExpressions.CLOUD_NODE_READ;
import static com.epam.pipeline.security.acl.AclExpressions.NODE_READ;
import static com.epam.pipeline.security.acl.AclExpressions.NODE_READ_FILTER;
import static com.epam.pipeline.security.acl.AclExpressions.NODE_STOP;
import static com.epam.pipeline.security.acl.AclExpressions.OR;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ADMIN_ONLY;

@Service
@RequiredArgsConstructor
public class ClusterApiService {

    private final NodesManager nodesManager;
    private final NodeDiskManager nodeDiskManager;
    private final UsageMonitoringManager usageMonitoringManager;
    private final InstanceOfferManager instanceOfferManager;
    private final EdgeServiceManager edgeServiceManager;
    private final PodsManager podsManager;

    @PostFilter(NODE_READ_FILTER)
    public List<NodeInstance> getNodes(final MachineType machineType) {
        return nodesManager.getNodes(machineType);
    }

    @PostFilter(NODE_READ_FILTER)
    @AclMaskList
    public List<NodeInstance> filterNodes(final FilterNodesVO filterNodesVO, final MachineType machineType) {
        return nodesManager.filterNodes(filterNodesVO, machineType);
    }

    @PreAuthorize(CLOUD_NODE_READ)
    @AclMask
    public NodeInstance getNode(final String name, final MachineType machineType, final Long regionId) {
        return nodesManager.getKubeOrCloudNode(name, machineType, regionId);
    }

    @PreAuthorize(NODE_READ)
    public RunInfo loadRunIdForNode(final String name) {
        return nodesManager.loadRunIdForNode(name);
    }

    @PreAuthorize(NODE_READ)
    @AclMask
    public NodeInstance getNode(final String name, final FilterPodsRequest request) {
        return nodesManager.getNode(name, request);
    }

    @PreAuthorize(NODE_STOP)
    @AclMask
    public NodeInstance terminateNode(final String name, final MachineType machineType, final Long regionId) {
        return nodesManager.terminateKubeOrCloudNode(name, machineType, regionId);
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER + OR + RUN_ADMIN_ONLY)
    public List<MonitoringStats> getStatsForNode(final String name,
                                                 final LocalDateTime from,
                                                 final LocalDateTime to,
                                                 final Long runId) {
        return usageMonitoringManager.getStatsForNode(name, from, to, runId);
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER + OR + RUN_ADMIN_ONLY)
    public GpuMonitoringStats getGpuStatsForNode(final String name,
                                                 final LocalDateTime from,
                                                 final LocalDateTime to,
                                                 final List<GpuMetricsGranularity> granularity,
                                                 final boolean squashCharts,
                                                 final Long runId) {
        return usageMonitoringManager.getGpuStatsForNode(name, from, to, granularity, squashCharts, runId);
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER + OR + RUN_ADMIN_ONLY)
    public InputStream getUsageStatisticsFile(final String name, final LocalDateTime from, final LocalDateTime to,
                                              final Duration interval, final MonitoringReportType type,
                                              final Long runId) {
        return usageMonitoringManager.getStatsForNodeAsInputStream(name, from, to, interval, type, runId);
    }

    public List<InstanceType> getAllowedInstanceTypes(final Long regionId, final Boolean spot) {
        return instanceOfferManager.getAllowedInstanceTypes(regionId, spot);
    }

    public List<InstanceType> getAllowedToolInstanceTypes(final Long regionId, final Boolean spot) {
        return instanceOfferManager.getAllowedToolInstanceTypes(regionId, spot);
    }

    public AllowedInstanceAndPriceTypes getAllowedInstanceAndPriceTypes(final Long toolId, final Long regionId,
                                                                        final Boolean spot) {
        return instanceOfferManager.getAllowedInstanceAndPriceTypes(toolId, regionId, spot);
    }

    public List<MasterNode> getMasterNodes() {
        return nodesManager.getMasterNodes();
    }

    @PreAuthorize(NODE_READ)
    public List<NodeDisk> loadNodeDisks(final String name) {
        return nodeDiskManager.loadByNodeId(name);
    }

    public String buildEdgeExternalUrl(final String region) {
        return edgeServiceManager.buildEdgeExternalUrl(region);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<PodInstance> getCorePods() {
        return podsManager.getCorePods();
    }

    @PreAuthorize(ADMIN_ONLY + OR + RUN_ADMIN_ONLY)
    public List<PodInstance> getPodsByLabels(final Map<String, String> labels) {
        return podsManager.getPodsByLabels(labels);
    }

    @PreAuthorize(ADMIN_ONLY + OR + RUN_ADMIN_ONLY)
    public PodDescription getPodDescription(final String podId, final boolean detailed) {
        return podsManager.describePod(podId, detailed);
    }

    @PreAuthorize(ADMIN_ONLY + OR + RUN_ADMIN_ONLY)
    public String getContainerLogs(final String podId, final String containerId, final Integer limit) {
        return podsManager.getContainerLogs(podId, containerId, limit);
    }

    @PreAuthorize(ADMIN_ONLY + OR + RUN_ADMIN_ONLY)
    public NetworkEventFilter getPlatformNetworkEventFilter() {
        return usageMonitoringManager.getPlatformNetworkStatsFilters();
    }

    @PreAuthorize(ADMIN_ONLY + OR + RUN_ADMIN_ONLY)
    public List<HistogramBin> filterPlatformNetworkEvents(final HistogramType histogramType,
                                                          final LocalDateTime from, final LocalDateTime to,
                                                          final Integer intervals,
                                                          final NetworkEventFilter filter) {
        return usageMonitoringManager.getPlatformNetworkStats(histogramType, from, to, intervals, filter);
    }

    public InstanceType loadInstanceType(final String instanceType) {
        return instanceOfferManager.loadInstanceType(instanceType);
    }

    public List<NodeResources> loadNodeAvailableResource(final Map<String, String> labels) {
        return nodesManager.loadNodeAvailableResources(labels);
    }
}

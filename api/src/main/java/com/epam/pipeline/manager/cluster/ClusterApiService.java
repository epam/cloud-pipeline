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

import java.util.List;

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.cluster.performancemonitoring.CAdvisorMonitoringManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ESMonitoringManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ClusterApiService {

    private static final String ELASTIC = "elastic";
    private static final String CADVISOR = "cadvisor";

    private final NodesManager nodesManager;
    private final UsageMonitoringManager usageMonitoringManager;
    private final InstanceOfferManager instanceOfferManager;

    public ClusterApiService(final NodesManager nodesManager,
                             final InstanceOfferManager instanceOfferManager,
                             final CAdvisorMonitoringManager cAdvisorMonitoringManager,
                             final ESMonitoringManager esMonitoringManager,
                             @Value("${monitoring.backend?:" + CADVISOR + "}") final String backend) {
        this.nodesManager = nodesManager;
        this.instanceOfferManager = instanceOfferManager;
        if (StringUtils.isBlank(backend) || backend.equals(CADVISOR)) {
            this.usageMonitoringManager = cAdvisorMonitoringManager;
        } else if (backend.equals(ELASTIC)) {
            this.usageMonitoringManager = esMonitoringManager;
        } else {
            throw new IllegalArgumentException(String.format("Required monitoring backend '%s' is not available. " +
                    "Use either %s or %s.", backend, CADVISOR, ELASTIC));
        }
    }

    @PostFilter("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(filterObject, 'READ')")
    public List<NodeInstance> getNodes() {
        return nodesManager.getNodes();
    }

    @PostFilter("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(filterObject, 'READ')")
    public List<NodeInstance> filterNodes(FilterNodesVO filterNodesVO) {
        return nodesManager.filterNodes(filterNodesVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(#name, 'READ')")
    @AclMask
    public NodeInstance getNode(String name) {
        return nodesManager.getNode(name);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(#name, 'READ')")
    @AclMask
    public NodeInstance getNode(String name, FilterPodsRequest request) {
        return nodesManager.getNode(name, request);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodeStopPermission(#name, 'EXECUTE')")
    @AclMask
    public NodeInstance terminateNode(String name) {
        return nodesManager.terminateNode(name);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.nodePermission(#nodeName, 'READ')")
    public List<MonitoringStats> getStatsForNode(String nodeName) {
        return usageMonitoringManager.getStatsForNode(nodeName);
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
}

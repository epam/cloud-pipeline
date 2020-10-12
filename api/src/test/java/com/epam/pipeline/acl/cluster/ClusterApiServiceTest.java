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

package com.epam.pipeline.acl.cluster;

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.NodeDiskManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.UsageMonitoringManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.cluster.NodeCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class ClusterApiServiceTest extends AbstractAclTest {

    @Autowired
    private ClusterApiService clusterApiService;

    @Autowired
    private NodesManager mockNodesManager;

    @Autowired
    private PipelineRunManager mockPipelineRunManager;

    @Autowired
    private ContextualPreferenceManager mockContextualPreferenceManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Autowired
    private UsageMonitoringManager mockUsageMonitoringManager;

    @Autowired
    private NodeDiskManager mockNodeDiskManager;

    @Autowired
    private InstanceOfferManager mockInstanceOfferManager;

    private final FilterPodsRequest filterPodsRequest = NodeCreatorUtils.getDefaultFilterPodsRequest();

    private final NodeInstance nodeInstance = NodeCreatorUtils.getDefaultNodeInstance();

    private final NodeInstance nodeInstanceWithoutPermission = NodeCreatorUtils.getDefaultNodeInstance();

    private final PipelineRun pipelineRun = NodeCreatorUtils.getPipelineRun();

    private final ContextualPreference contextualPreference = NodeCreatorUtils.getContextualPreference();

    private final FilterNodesVO filterNodesVO = NodeCreatorUtils.getDefaultFilterNodesVO();

    private final NodeDisk nodeDisk = NodeCreatorUtils.getDefaultNodeDisk();

    private final MonitoringStats monitoringStats = NodeCreatorUtils.getMonitoringStats();

    private final InstanceType instanceType = NodeCreatorUtils.getDefaultInstanceType();

    private InputStream inputStream;

    private List<NodeDisk> nodeDisks;

    private List<InstanceType> instanceTypes;

    private List<MonitoringStats> statsList;

    private List<NodeInstance> singleNodeInstance;

    private List<NodeInstance> twoNodeInstances;

    @Before
    public void setUp() {
        statsList = Collections.singletonList(monitoringStats);

        instanceTypes = Collections.singletonList(instanceType);

        nodeDisks = Collections.singletonList(nodeDisk);

        pipelineRun.setId(1L);
        pipelineRun.setOwner(SIMPLE_USER);

        final PipelineRun pipelineRunWithoutPermission = NodeCreatorUtils.getPipelineRun();
        pipelineRunWithoutPermission.setId(2L);
        pipelineRunWithoutPermission.setOwner(SIMPLE_USER_2);
        pipelineRunWithoutPermission.setName(TEST_NAME_2);

        nodeInstance.setId(1L);
        nodeInstance.setOwner(OWNER_USER);
        nodeInstance.setPipelineRun(pipelineRun);
        nodeInstance.setName(TEST_NAME);

        nodeInstanceWithoutPermission.setId(2L);
        nodeInstanceWithoutPermission.setOwner(SIMPLE_USER_2);
        nodeInstanceWithoutPermission.setPipelineRun(pipelineRunWithoutPermission);
        nodeInstanceWithoutPermission.setName(TEST_NAME_2);

        singleNodeInstance = new ArrayList<>();
        singleNodeInstance.add(nodeInstance);

        twoNodeInstances = new ArrayList<>();
        twoNodeInstances.add(nodeInstance);
        twoNodeInstances.add(nodeInstanceWithoutPermission);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnListWithNodeInstancesForAdmin() {
        doReturn(singleNodeInstance).when(mockNodesManager).getNodes();

        List<NodeInstance> nodes = clusterApiService.getNodes();

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnListWithNodeInstancesWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER_ROLE, AclPermission.READ.getMask())));
        doReturn(contextualPreference).when(mockContextualPreferenceManager).search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));
        doReturn(singleNodeInstance).when(mockNodesManager).getNodes();

        List<NodeInstance> nodes = clusterApiService.getNodes();

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnListWithNodeInstanceWhichPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        initAclEntity(nodeInstanceWithoutPermission,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask())));

        doReturn(contextualPreference).when(mockContextualPreferenceManager).search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));

        doReturn(twoNodeInstances).when(mockNodesManager).getNodes();

        List<NodeInstance> nodes = clusterApiService.getNodes();

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnEmptyNodeInstanceListWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstanceWithoutPermission);
        doReturn(singleNodeInstance).when(mockNodesManager).getNodes();

        List<NodeInstance> nodes = clusterApiService.getNodes();

        assertThat(nodes).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnFilteredListWithNodeInstancesForAdmin() {
        doReturn(singleNodeInstance).when(mockNodesManager).filterNodes(filterNodesVO);

        List<NodeInstance> nodes = clusterApiService.filterNodes(filterNodesVO);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnFilteredNodeInstanceListWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(contextualPreference).when(mockContextualPreferenceManager).search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));
        doReturn(singleNodeInstance).when(mockNodesManager).filterNodes(filterNodesVO);

        List<NodeInstance> nodes = clusterApiService.filterNodes(filterNodesVO);

        assertThat(nodes.size()).isEqualTo(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnFilteredListWithNodeInstanceWhichPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        initAclEntity(nodeInstanceWithoutPermission,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask())));
        doReturn(contextualPreference).when(mockContextualPreferenceManager).search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));
        doReturn(twoNodeInstances).when(mockNodesManager).filterNodes(filterNodesVO);

        List<NodeInstance> nodes = clusterApiService.filterNodes(filterNodesVO);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnEmptyFilteredNodeInstanceListWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstanceWithoutPermission);
        doReturn(contextualPreference).when(mockContextualPreferenceManager).search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));
        doReturn(singleNodeInstance).when(mockNodesManager).filterNodes(filterNodesVO);

        List<NodeInstance> nodes = clusterApiService.filterNodes(filterNodesVO);

        assertThat(nodes).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnNodeInstanceForAdmin() {
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());

        NodeInstance node = clusterApiService.getNode(nodeInstance.getName());

        assertThat(node).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnNodeInstanceWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        NodeInstance node = clusterApiService.getNode(nodeInstance.getName());

        assertThat(node).isEqualTo(nodeInstance);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToNode() {
        initAclEntity(nodeInstance);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        clusterApiService.getNode(nodeInstance.getName());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnNodeThroughRequestForAdmin() {
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);

        NodeInstance node = clusterApiService.getNode(nodeInstance.getName(), filterPodsRequest);

        assertThat(node).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnNodeThroughRequestWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        NodeInstance node = clusterApiService.getNode(nodeInstance.getName(), filterPodsRequest);

        assertThat(node).isEqualTo(nodeInstance);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToNodeThroughRequest() {
        initAclEntity(nodeInstance);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        clusterApiService.getNode(nodeInstance.getName());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldTerminateNodeForAdmin() {
        doReturn(nodeInstance).when(mockNodesManager).terminateNode(nodeInstance.getName());

        NodeInstance resultNode = clusterApiService.terminateNode(nodeInstance.getName());

        assertThat(resultNode).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldTerminateNodeWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.EXECUTE.getMask())));
        doReturn(nodeInstance).when(mockNodesManager).terminateNode(nodeInstance.getName());
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        NodeInstance resultNode = clusterApiService.terminateNode(nodeInstance.getName());

        assertThat(resultNode).isEqualTo(nodeInstance);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToNodeTerminationWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstance);
        doReturn(nodeInstance).when(mockNodesManager).terminateNode(nodeInstance.getName());
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        clusterApiService.terminateNode(nodeInstance.getName());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnStatsForNodeForAdmin() {
        final MonitoringStats monitoringStats = NodeCreatorUtils.getMonitoringStats();
        statsList = Collections.singletonList(monitoringStats);
        doReturn(statsList).when(mockUsageMonitoringManager)
                .getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);

        List<MonitoringStats> resultStatsList =
                clusterApiService.getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);

        assertThat(resultStatsList.size()).isEqualTo(1);
        assertThat(resultStatsList.get(0)).isEqualTo(monitoringStats);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnStatsWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(statsList).when(mockUsageMonitoringManager)
                .getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        List<MonitoringStats> resultStatsList =
                clusterApiService.getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);

        assertThat(resultStatsList.size()).isEqualTo(1);
        assertThat(resultStatsList.get(0)).isEqualTo(monitoringStats);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToStatsWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstance);
        doReturn(statsList).when(mockUsageMonitoringManager)
                .getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        clusterApiService.getStatsForNode(nodeInstance.getName(), LocalDateTime.MIN, LocalDateTime.MAX);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnUsageStatisticsFileForAdmin() {
        doReturn(inputStream).when(mockUsageMonitoringManager)
                .getStatsForNodeAsInputStream(nodeInstance.getName(),
                        LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);

        InputStream resultInputStream =
                clusterApiService.getUsageStatisticsFile(nodeInstance.getName(),
                        LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);

        assertThat(resultInputStream).isEqualTo(inputStream);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnUsageStatisticsFileWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(inputStream).when(mockUsageMonitoringManager)
                .getStatsForNodeAsInputStream(nodeInstance.getName(),
                        LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        InputStream resultInputStream =
                clusterApiService.getUsageStatisticsFile(nodeInstance.getName(),
                        LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);

        assertThat(resultInputStream).isEqualTo(inputStream);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToUsageStatisticsFileWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstance);
        doReturn(inputStream).when(mockUsageMonitoringManager)
                .getStatsForNodeAsInputStream(nodeInstance.getName(),
                        LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeInstance.getName());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        clusterApiService.getUsageStatisticsFile(nodeInstance.getName(),
                LocalDateTime.MIN, LocalDateTime.MAX, Duration.ZERO);
    }

    @Test
    public void shouldReturnInstanceTypes() {
        doReturn(instanceTypes).when(mockInstanceOfferManager).getAllowedInstanceTypes(1L, true);

        List<InstanceType> resultInstanceTypesList = clusterApiService.getAllowedInstanceTypes(1L, true);

        assertThat(resultInstanceTypesList).isEqualTo(instanceTypes);
    }

    @Test
    public void shouldReturnToolInstanceTypes() {
        doReturn(instanceTypes).when(mockInstanceOfferManager).getAllowedToolInstanceTypes(1L, true);

        List<InstanceType> resultInstanceTypesList = clusterApiService
                .getAllowedToolInstanceTypes(1L, true);

        assertThat(resultInstanceTypesList).isEqualTo(instanceTypes);
    }

    @Test
    public void shouldReturnAllowedInstanceAndPriceTypes() {
        final AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes =
                NodeCreatorUtils.getDefaultAllowedInstanceAndPriceTypes();
        doReturn(allowedInstanceAndPriceTypes).when(mockInstanceOfferManager)
                .getAllowedInstanceAndPriceTypes(1L, 2L, true);

        AllowedInstanceAndPriceTypes result = clusterApiService.getAllowedInstanceAndPriceTypes(1L, 2L, true);

        assertThat(result).isEqualTo(allowedInstanceAndPriceTypes);
    }

    @Test
    public void shouldReturnMasterNodes() {
        final MasterNode masterNode = NodeCreatorUtils.getMasterNodeWithEmptyNode();
        List<MasterNode> masterNodes = Collections.singletonList(masterNode);
        doReturn(masterNodes).when(mockNodesManager).getMasterNodes();

        List<MasterNode> resultMasterNodesList = clusterApiService.getMasterNodes();

        assertThat(resultMasterNodesList).isEqualTo(masterNodes);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnNodeDisksForAdmin() {
        doReturn(nodeDisks).when(mockNodeDiskManager).loadByNodeId(nodeDisk.getNodeId());

        List<NodeDisk> resultNodeDiskList = clusterApiService.loadNodeDisks(nodeDisk.getNodeId());

        assertThat(resultNodeDiskList.size()).isEqualTo(1);
        assertThat(resultNodeDiskList.get(0)).isEqualTo(nodeDisk);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnNodeDisksWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(nodeDisks).when(mockNodeDiskManager).loadByNodeId(nodeDisk.getNodeId());
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeDisk.getNodeId(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeDisk.getNodeId());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        List<NodeDisk> resultNodeDiskList = clusterApiService.loadNodeDisks(nodeDisk.getNodeId());

        assertThat(resultNodeDiskList.size()).isEqualTo(1);
        assertThat(resultNodeDiskList.get(0)).isEqualTo(nodeDisk);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAccessToNodeDisksWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstance);
        doReturn(nodeDisks).when(mockNodeDiskManager).loadByNodeId(nodeDisk.getNodeId());
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeDisk.getNodeId(), filterPodsRequest);
        doReturn(nodeInstance).when(mockNodesManager).getNode(nodeDisk.getNodeId());
        doReturn(pipelineRun).when(mockPipelineRunManager).loadPipelineRun(eq(pipelineRun.getId()));

        clusterApiService.loadNodeDisks(nodeDisk.getNodeId());
    }
}

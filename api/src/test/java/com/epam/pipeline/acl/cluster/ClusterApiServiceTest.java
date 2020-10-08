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

import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class ClusterApiServiceTest extends AbstractAclTest {

    @Autowired
    private ClusterApiService clusterApiService;

    @Autowired
    private NodesManager mockNodesManager;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private CheckPermissionHelper permissionHelper;

    @Autowired
    private ContextualPreferenceManager preferenceManager;

    private NodeInstance nodeInstance;

    private NodeInstance nodeInstanceWithoutPermission;

    private List<NodeInstance> singleNodeInstance;

    private List<NodeInstance> twoNodeInstances;

    private PipelineRun pipelineRun;

    private PipelineRun pipelineRunWithoutPermission;

    private ContextualPreference contextualPreference;

    private final ContextualPreferenceExternalResource resource =
            new ContextualPreferenceExternalResource(ContextualPreferenceLevel.USER, "1");

    @Before
    public void setUp() {
        contextualPreference = new ContextualPreference(
                "name", "0", PreferenceType.STRING, null, resource);

        pipelineRun = new PipelineRun();
        pipelineRun.setId(1L);
        pipelineRun.setPipelineId(1L);
        pipelineRun.setOwner(SIMPLE_USER_ROLE);

        pipelineRunWithoutPermission = new PipelineRun();
        pipelineRunWithoutPermission.setId(2L);
        pipelineRunWithoutPermission.setPipelineId(2L);
        pipelineRunWithoutPermission.setOwner(SIMPLE_USER_ROLE);

        nodeInstance = new NodeInstance();
        nodeInstance.setId(1L);
        nodeInstance.setOwner(SIMPLE_USER_ROLE);
        nodeInstance.setPipelineRun(pipelineRun);

        nodeInstanceWithoutPermission = new NodeInstance();
        nodeInstanceWithoutPermission.setId(2L);
        nodeInstanceWithoutPermission.setOwner(SIMPLE_USER_ROLE);
        nodeInstanceWithoutPermission.setPipelineRun(pipelineRunWithoutPermission);

        singleNodeInstance = new ArrayList<>();
        singleNodeInstance.add(nodeInstance);

        twoNodeInstances = new ArrayList<>();
        twoNodeInstances.add(nodeInstance);
        twoNodeInstances.add(nodeInstanceWithoutPermission);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnListWithNodeInstancesForAdmin() {
        initAclEntity(nodeInstance);
        doReturn(singleNodeInstance).when(mockNodesManager).getNodes();

        List<NodeInstance> nodes = clusterApiService.getNodes();

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER_ROLE)
    public void shouldReturnListWithNodeInstancesWhenPermissionIsGranted() {
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER_ROLE, AclPermission.READ.getMask())));

        doReturn(contextualPreference).when(preferenceManager).search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));
        doReturn(pipelineRun).when(pipelineRunManager).loadRunParent(pipelineRun);
        when(permissionHelper.isAllowed("READ", pipelineRun)).thenReturn(true);
        doReturn(singleNodeInstance).when(mockNodesManager).getNodes();

        List<NodeInstance> nodes = clusterApiService.getNodes();

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER_ROLE)
    public void shouldReturnListWithNodeInstanceWhichPermissionIsGranted() {
        initAclEntity(nodeInstance,
                Collections.singletonList(new UserPermission(SIMPLE_USER_ROLE, AclPermission.READ.getMask())));
        initAclEntity(nodeInstanceWithoutPermission,
                Collections.singletonList(new UserPermission(SIMPLE_USER_ROLE, AclPermission.NO_READ.getMask())));

        doReturn(contextualPreference).when(preferenceManager).search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));
        doReturn(pipelineRun).when(pipelineRunManager).loadRunParent(pipelineRun);
        when(permissionHelper.isAllowed("READ", pipelineRun)).thenReturn(true);
        doReturn(twoNodeInstances).when(mockNodesManager).getNodes();

        List<NodeInstance> nodes = clusterApiService.getNodes();

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isEqualTo(nodeInstance);
    }


    @Test
    @WithMockUser(username = SIMPLE_USER_ROLE)
    public void shouldReturnEmptyNodeInstanceListWhenPermissionIsNotGranted() {
        initAclEntity(nodeInstanceWithoutPermission,
                Collections.singletonList(new UserPermission(SIMPLE_USER_ROLE, AclPermission.NO_READ.getMask())));
        doReturn(contextualPreference).when(preferenceManager).search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));
        doReturn(pipelineRun).when(pipelineRunManager).loadRunParent(pipelineRun);
        doReturn(singleNodeInstance).when(mockNodesManager).getNodes();

        List<NodeInstance> nodes = clusterApiService.getNodes();

        assertThat(nodes).isEmpty();
    }
}

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

package com.epam.pipeline.acl.docker;

import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.READ_PERMISSION;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class ToolGroupApiServiceTest extends AbstractAclTest {

    private static final String TOOL_GROUP_MANAGER = "TOOL_GROUP_MANAGER";
    private final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
    private final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
    private final Tool tool = DockerCreatorUtils.getTool(ANOTHER_SIMPLE_USER);
    private final ToolGroupWithIssues toolGroupWithIssues = DockerCreatorUtils.getToolGroupWithIssues();


    @Autowired
    private ToolGroupApiService toolGroupApiService;

    @Autowired
    private ToolGroupManager mockToolGroupManager;

    @Autowired
    private DockerRegistryManager mockDockerRegistryManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateToolGroupForAdmin() {
        doReturn(toolGroup).when(mockToolGroupManager).create(toolGroup);

        assertThat(toolGroupApiService.create(toolGroup)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = TOOL_GROUP_MANAGER)
    public void shouldCreateToolGroupForManagerWhenPermissionGranted() {
        initAclEntity(dockerRegistry, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).create(toolGroup);

        assertThat(toolGroupApiService.create(toolGroup)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateToolGroupWithInvalidRole() {
        initAclEntity(dockerRegistry, AclPermission.WRITE);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.create(toolGroup));
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCreateToolGroupWhenPermissionIsNotGranted() {
        initAclEntity(dockerRegistry);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.create(toolGroup));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateToolGroupForAdmin() {
        doReturn(toolGroup).when(mockToolGroupManager).updateToolGroup(toolGroup);

        assertThat(toolGroupApiService.update(toolGroup)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateToolGroupWhenPermissionIsGranted() {
        initAclEntity(toolGroup, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).updateToolGroup(toolGroup);

        assertThat(toolGroupApiService.update(toolGroup)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldDenyUpdateWhenPermissionIsNotGranted() {
        initAclEntity(toolGroup);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.update(toolGroup));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolGroupsByRegistryIdForAdmin() {
        final List<ToolGroup> toolGroups = mutableListOf(toolGroup);
        doReturn(toolGroups).when(mockToolGroupManager).loadByRegistryId(ID);

        assertThat(toolGroupApiService.loadByRegistryId(ID)).isEqualTo(toolGroups);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolGroupsByRegistryIdWhenPermissionIsGranted() {
        initAclEntity(toolGroup, AclPermission.READ);
        final List<ToolGroup> toolGroups = mutableListOf(toolGroup);
        doReturn(toolGroups).when(mockToolGroupManager).loadByRegistryId(ID);

        final List<ToolGroup> returnedTollGroups = toolGroupApiService.loadByRegistryId(ID);

        assertThat(returnedTollGroups).isEqualTo(toolGroups);
        assertThat(returnedTollGroups.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolsGroupsByRegistryIdWhichPermissionIsGranted() {
        final ToolGroup anotherToolGroup = DockerCreatorUtils.getToolGroup(ID_2, ANOTHER_SIMPLE_USER);
        initAclEntity(toolGroup, AclPermission.READ);
        initAclEntity(anotherToolGroup);
        doReturn(mutableListOf(toolGroup, anotherToolGroup)).when(mockToolGroupManager).loadByRegistryId(ID);

        assertThat(toolGroupApiService.loadByRegistryId(ID)).hasSize(1).contains(toolGroup);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolGroupsByRegistryIdWhichPermissionIsNotGranted() {
        initAclEntity(toolGroup);

        assertThat(toolGroupApiService.loadByRegistryId(ID)).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolGroupsByRegistryNameOrIdForAdmin() {
        final List<ToolGroup> toolGroups = mutableListOf(toolGroup);
        doReturn(toolGroups).when(mockToolGroupManager).loadByRegistryNameOrId(TEST_STRING);

        assertThat(toolGroupApiService.loadByRegistryNameOrId(TEST_STRING)).isEqualTo(toolGroups);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolGroupsByRegistryNameOrIdWhenPermissionIsGranted() {
        initAclEntity(toolGroup, AclPermission.READ);
        final List<ToolGroup> toolGroups = mutableListOf(toolGroup);
        doReturn(toolGroups).when(mockToolGroupManager).loadByRegistryNameOrId(TEST_STRING);

        final List<ToolGroup> returnedTollGroups = toolGroupApiService.loadByRegistryNameOrId(TEST_STRING);

        assertThat(returnedTollGroups).isEqualTo(toolGroups);
        assertThat(returnedTollGroups.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolsGroupsByRegistryNameOrIdWhichPermissionIsGranted() {
        final ToolGroup anotherToolGroup = DockerCreatorUtils.getToolGroup(ID_2, ANOTHER_SIMPLE_USER);
        initAclEntity(toolGroup, AclPermission.READ);
        initAclEntity(anotherToolGroup);
        doReturn(mutableListOf(toolGroup, anotherToolGroup))
                .when(mockToolGroupManager).loadByRegistryNameOrId(TEST_STRING);

        assertThat(toolGroupApiService.loadByRegistryNameOrId(TEST_STRING)).hasSize(1).contains(toolGroup);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldDenyLoadToolGroupsByRegistryNameOrIdWhichPermissionIsNotGranted() {
        initAclEntity(toolGroup);

        assertThat(toolGroupApiService.loadByRegistryNameOrId(TEST_STRING)).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolGroupForAdmin() {
        doReturn(toolGroup).when(mockToolGroupManager).load(ID);

        assertThat(toolGroupApiService.load(ID)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolGroup() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(dockerRegistry);
        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        toolGroup.setParent(dockerRegistry);
        doReturn(toolGroup).when(mockToolGroupManager).load(ID);

        assertThat(toolGroupApiService.load(ID)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolGroupHierarchy() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        final Tool tool = DockerCreatorUtils.getTool(ANOTHER_SIMPLE_USER);
        final Tool toolWithoutPermission = DockerCreatorUtils.getTool(ID_2, ANOTHER_SIMPLE_USER);
        final List<Tool> tools = Arrays.asList(tool, toolWithoutPermission);
        toolGroup.setParent(dockerRegistry);
        toolGroup.setTools(tools);
        initAclEntity(dockerRegistry);
        initAclEntity(tool, AclPermission.READ);
        initAclEntity(toolWithoutPermission);
        doReturn(toolGroup).when(mockToolGroupManager).load(ID);

        final ToolGroup returnedToolGroup = toolGroupApiService.load(ID);

        assertThat(returnedToolGroup.getParent()).isEqualTo(dockerRegistry);
        assertThat(returnedToolGroup.getLeaves().size()).isEqualTo(1);
        assertThat(returnedToolGroup.getLeaves().get(0)).isEqualTo(tool);
    }

    @Test
    @WithMockUser
    public void shouldLoadToolsWithIssuesCount() {
        doReturn(toolGroupWithIssues).when(mockToolGroupManager).loadToolsWithIssuesCount(ID);

        assertThat(toolGroupApiService.loadToolsWithIssuesCount(ID)).isEqualTo(toolGroupWithIssues);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolsWithIssuesCountHierarchy() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        final ToolGroupWithIssues toolGroupWithIssues = DockerCreatorUtils.getToolGroupWithIssues();
        toolGroupWithIssues.setOwner(ANOTHER_SIMPLE_USER);
        toolGroupWithIssues.setId(ID_2);
        final Tool tool = DockerCreatorUtils.getTool(ANOTHER_SIMPLE_USER);
        final Tool toolWithoutPermission = DockerCreatorUtils.getTool(ID_2, ANOTHER_SIMPLE_USER);
        final List<Tool> tools = Arrays.asList(tool, toolWithoutPermission);
        toolGroupWithIssues.setParent(dockerRegistry);
        toolGroupWithIssues.setTools(tools);
        initAclEntity(dockerRegistry);
        initAclEntity(tool, AclPermission.READ);
        initAclEntity(toolWithoutPermission);
        doReturn(toolGroupWithIssues).when(mockToolGroupManager).loadToolsWithIssuesCount(ID);

        final ToolGroupWithIssues returnedToolGroup = toolGroupApiService.loadToolsWithIssuesCount(ID);

        assertThat(returnedToolGroup.getParent()).isEqualTo(dockerRegistry);
        assertThat(returnedToolGroup.getLeaves().size()).isEqualTo(1);
        assertThat(returnedToolGroup.getLeaves().get(0)).isEqualTo(tool);
    }

    @Test
    @WithMockUser
    public void shouldLoadByNameOrId() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(dockerRegistry);
        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        toolGroup.setParent(dockerRegistry);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        assertThat(toolGroupApiService.loadByNameOrId(TEST_STRING)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolGroupHierarchyByNameOrId() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        final Tool tool = DockerCreatorUtils.getTool(ANOTHER_SIMPLE_USER);
        final Tool toolWithoutPermission = DockerCreatorUtils.getTool(ID_2, ANOTHER_SIMPLE_USER);
        final List<Tool> tools = Arrays.asList(tool, toolWithoutPermission);
        toolGroup.setParent(dockerRegistry);
        toolGroup.setTools(tools);
        initAclEntity(dockerRegistry);
        initAclEntity(tool, AclPermission.READ);
        initAclEntity(toolWithoutPermission);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);

        final ToolGroup returnedToolGroup = toolGroupApiService.loadByNameOrId(TEST_STRING);

        assertThat(returnedToolGroup.getParent()).isEqualTo(dockerRegistry);
        assertThat(returnedToolGroup.getLeaves().size()).isEqualTo(1);
        assertThat(returnedToolGroup.getLeaves().get(0)).isEqualTo(tool);
    }

    @Test
    @WithMockUser
    public void shouldLoadPrivate() {
        doReturn(toolGroup).when(mockToolGroupManager).loadPrivate(ID);

        assertThat(toolGroupApiService.loadPrivate(ID)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreatePrivateForAdmin() {
        doReturn(toolGroup).when(mockToolGroupManager).createPrivate(ID);

        assertThat(toolGroupApiService.createPrivate(ID)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreatePrivateWhenPermissionIsGranted() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(toolGroup, AclPermission.READ);
        dockerRegistry.setGroups(mutableListOf(toolGroup));
        doReturn(dockerRegistry).when(mockDockerRegistryManager).getDockerRegistryTree(ID);
        doReturn(toolGroup).when(mockToolGroupManager).createPrivate(ID);

        assertThat(toolGroupApiService.createPrivate(ID)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreatePrivateWhenPermissionIsNotGranted() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(toolGroup);
        dockerRegistry.setGroups(mutableListOf(toolGroup));
        initAclEntity(dockerRegistry);
        doReturn(dockerRegistry).when(mockDockerRegistryManager).getDockerRegistryTree(ID);
        doReturn(toolGroup).when(mockToolGroupManager).createPrivate(ID);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.createPrivate(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteToolGroupForAdmin() {
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, false);

        assertThat(toolGroupApiService.delete(TEST_STRING)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER, username = SIMPLE_USER)
    public void shouldDeleteToolGroupForManagerWhenPermissionIsGranted() {
        initAclEntity(toolGroup, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, false);
        mockSecurityContext();

        assertThat(toolGroupApiService.delete(TEST_STRING)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteToolGroupWithInvalidRole() {
        initAclEntity(toolGroup, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, false);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.delete(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER)
    public void shouldDenyDeleteToolGroupWhenPermissionIsNotGranted() {
        initAclEntity(toolGroup);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, false);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.delete(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteForceToolGroupForAdmin() {
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, true);

        assertThat(toolGroupApiService.deleteForce(TEST_STRING)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteForceToolGroupWithInvalidRole() {
        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        toolGroup.setTools(mutableListOf(tool));
        initAclEntity(toolGroup, AclPermission.WRITE);
        initAclEntity(tool, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, true);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.deleteForce(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER, username = SIMPLE_USER)
    public void shouldDeleteForceToolGroupForManagerWhenChildPermissionIsGranted() {
        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        toolGroup.setTools(mutableListOf(tool));
        initAclEntity(toolGroup, AclPermission.WRITE);
        initAclEntity(tool, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, true);
        mockSecurityContext();

        assertThat(toolGroupApiService.deleteForce(TEST_STRING)).isEqualTo(toolGroup);
    }


    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER, username = SIMPLE_USER)
    public void shouldDenyDeleteForceToolGroupForManagerWhenChildPermissionIsNotGranted() {
        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        toolGroup.setTools(mutableListOf(tool));
        initAclEntity(toolGroup, AclPermission.WRITE);
        initAclEntity(tool);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, true);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.deleteForce(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER)
    public void shouldDenyDeleteForceToolGroupForManagerWhenPermissionIsNotGranted() {
        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        toolGroup.setTools(mutableListOf(tool));
        initAclEntity(toolGroup);
        initAclEntity(tool, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, true);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.deleteForce(TEST_STRING));
    }
}

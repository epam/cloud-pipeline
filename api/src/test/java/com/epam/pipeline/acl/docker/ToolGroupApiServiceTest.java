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
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import com.epam.pipeline.test.creator.docker.ToolCreatorUtils;
import com.epam.pipeline.test.creator.docker.ToolGroupCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class ToolGroupApiServiceTest extends AbstractAclTest {


    private static final String TOOL_GROUP_MANAGER = "TOOL_GROUP_MANAGER";
    private final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ID, SIMPLE_USER);
    private final ToolGroup anotherToolGroup = ToolGroupCreatorUtils.getToolGroup(ID, ANOTHER_SIMPLE_USER);
    private final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, SIMPLE_USER);
    private final Tool tool = ToolCreatorUtils.getTool(ANOTHER_SIMPLE_USER);

    @Autowired
    private ToolGroupApiService toolGroupApiService;

    @Autowired
    private ToolGroupManager mockToolGroupManager;

    @Autowired
    private AuthManager mockAuthManager;

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
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(toolGroup).when(mockToolGroupManager).create(toolGroup);

        assertThat(toolGroupApiService.create(toolGroup)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateToolGroupWithInvalidRole() {
        initAclEntity(dockerRegistry, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).create(toolGroup);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.create(toolGroup));
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER)
    public void shouldDenyCreateToolGroupWhenPermissionIsNotGranted() {
        initAclEntity(dockerRegistry);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(toolGroup).when(mockToolGroupManager).create(toolGroup);

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
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(toolGroup).when(mockToolGroupManager).updateToolGroup(toolGroup);

        assertThat(toolGroupApiService.update(toolGroup)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser
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
        initAclEntity(anotherToolGroup, AclPermission.READ);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        final List<ToolGroup> toolGroups = mutableListOf(anotherToolGroup);
        doReturn(toolGroups).when(mockToolGroupManager).loadByRegistryId(ID);

        final List<ToolGroup> returnedTollGroups = toolGroupApiService.loadByRegistryId(ID);

        assertThat(returnedTollGroups).isEqualTo(toolGroups);
        assertThat(returnedTollGroups.get(0).getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolsGroupsByRegistryIdWhichPermissionIsGranted() {
        final ToolGroup anotherToolGroup = ToolGroupCreatorUtils.getToolGroup(ID_2, ANOTHER_SIMPLE_USER);
        initAclEntity(toolGroup, AclPermission.READ);
        initAclEntity(anotherToolGroup);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(mutableListOf(toolGroup, anotherToolGroup)).when(mockToolGroupManager).loadByRegistryId(ID);

        assertThat(toolGroupApiService.loadByRegistryId(ID)).hasSize(1).contains(toolGroup);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadToolGroupsByRegistryIdWhichPermissionIsNotGranted() {
        initAclEntity(anotherToolGroup);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(mutableListOf(anotherToolGroup)).when(mockToolGroupManager).loadByRegistryId(ID);

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
        initAclEntity(anotherToolGroup, AclPermission.READ);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        final List<ToolGroup> toolGroups = mutableListOf(anotherToolGroup);
        doReturn(toolGroups).when(mockToolGroupManager).loadByRegistryNameOrId(TEST_STRING);

        final List<ToolGroup> returnedTollGroups = toolGroupApiService.loadByRegistryNameOrId(TEST_STRING);

        assertThat(returnedTollGroups).isEqualTo(toolGroups);
        assertThat(returnedTollGroups.get(0).getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolsGroupsByRegistryNameOrIdWhichPermissionIsGranted() {
        final ToolGroup anotherToolGroup = ToolGroupCreatorUtils.getToolGroup(ID_2, ANOTHER_SIMPLE_USER);
        initAclEntity(toolGroup, AclPermission.READ);
        initAclEntity(anotherToolGroup);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(mutableListOf(toolGroup, anotherToolGroup))
                .when(mockToolGroupManager).loadByRegistryNameOrId(TEST_STRING);

        assertThat(toolGroupApiService.loadByRegistryNameOrId(TEST_STRING)).hasSize(1).contains(toolGroup);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadToolGroupsByRegistryNameOrIdWhichPermissionIsNotGranted() {
        initAclEntity(anotherToolGroup);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(mutableListOf(anotherToolGroup)).when(mockToolGroupManager).loadByRegistryNameOrId(TEST_STRING);

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
        final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ID, ANOTHER_SIMPLE_USER);
        toolGroup.setParent(dockerRegistry);
        doReturn(toolGroup).when(mockToolGroupManager).load(ID);

        assertThat(toolGroupApiService.load(ID)).isEqualTo(toolGroup);
    }

    @Test
    @WithMockUser
    public void shouldLoadToolsWithIssuesCount() {
        final ToolGroupWithIssues toolGroupWithIssues = ToolGroupCreatorUtils.getToolGroupWithIssues();
        doReturn(toolGroupWithIssues).when(mockToolGroupManager).loadToolsWithIssuesCount(ID);

        assertThat(toolGroupApiService.loadToolsWithIssuesCount(ID)).isEqualTo(toolGroupWithIssues);
    }

    @Test
    @WithMockUser
    public void shouldLoadByNameOrId() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(dockerRegistry);
        final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ID, ANOTHER_SIMPLE_USER);
        toolGroup.setParent(dockerRegistry);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);

        assertThat(toolGroupApiService.loadByNameOrId(TEST_STRING)).isEqualTo(toolGroup);
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
        initAclEntity(anotherToolGroup, AclPermission.READ);
        dockerRegistry.setGroups(mutableListOf(anotherToolGroup));
        doReturn(dockerRegistry).when(mockDockerRegistryManager).getDockerRegistryTree(ID);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(anotherToolGroup).when(mockToolGroupManager).createPrivate(ID);

        assertThat(toolGroupApiService.createPrivate(ID)).isEqualTo(anotherToolGroup);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreatePrivateWhenPermissionIsNotGranted() {
        final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(anotherToolGroup);
        dockerRegistry.setGroups(mutableListOf(anotherToolGroup));
        initAclEntity(dockerRegistry);
        doReturn(dockerRegistry).when(mockDockerRegistryManager).getDockerRegistryTree(ID);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(anotherToolGroup).when(mockToolGroupManager).createPrivate(ID);

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
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, false);

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
        initAclEntity(anotherToolGroup);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();
        doReturn(anotherToolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(anotherToolGroup).when(mockToolGroupManager).delete(TEST_STRING, false);

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
        final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ID, SIMPLE_USER);
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
        final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ID, SIMPLE_USER);
        toolGroup.setTools(mutableListOf(tool));
        initAclEntity(toolGroup, AclPermission.WRITE);
        initAclEntity(tool, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, true);

        assertThat(toolGroupApiService.deleteForce(TEST_STRING)).isEqualTo(toolGroup);
    }


    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER, username = SIMPLE_USER)
    public void shouldDenyDeleteForceToolGroupForManagerWhenChildPermissionIsNotGranted() {
        final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ID, SIMPLE_USER);
        toolGroup.setTools(mutableListOf(tool));
        initAclEntity(toolGroup, AclPermission.WRITE);
        initAclEntity(tool);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, true);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.deleteForce(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER)
    public void shouldDenyDeleteForceToolGroupForManagerWhenPermissionIsNotGranted() {
        final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ID, ANOTHER_SIMPLE_USER);
        toolGroup.setTools(mutableListOf(tool));
        initAclEntity(toolGroup);
        initAclEntity(tool, AclPermission.WRITE);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(TEST_STRING);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();
        doReturn(toolGroup).when(mockToolGroupManager).delete(TEST_STRING, true);

        assertThrows(AccessDeniedException.class, () -> toolGroupApiService.deleteForce(TEST_STRING));
    }
}

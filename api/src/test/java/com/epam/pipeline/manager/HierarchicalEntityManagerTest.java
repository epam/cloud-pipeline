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

package com.epam.pipeline.manager;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ALL_PERMISSIONS;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.READ_PERMISSION;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.WRITE_PERMISSION;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getRunConfiguration;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getDockerRegistry;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getDockerRegistryList;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getToolGroup;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolder;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipeline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;

public class HierarchicalEntityManagerTest extends AbstractAclTest {

    private final Tool tool = getTool(ID, ANOTHER_SIMPLE_USER);
    private final ToolGroup toolGroup = getToolGroup(ID, ANOTHER_SIMPLE_USER, Collections.singletonList(tool));
    private final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
    private final Pipeline pipeline = getPipeline(ID, ANOTHER_SIMPLE_USER);
    private final UserContext userContext = new UserContext(ID, SIMPLE_USER);
    private final DockerRegistry registry =
            getDockerRegistry(ID, ANOTHER_SIMPLE_USER, Collections.singletonList(toolGroup));
    private final DockerRegistryList dockerRegistryList = getDockerRegistryList(ID, ANOTHER_SIMPLE_USER, registry);
    private final Folder folder = getFolder(ID, ANOTHER_SIMPLE_USER);
    private final Role role = UserCreatorUtils.getRole();

    @Autowired
    private HierarchicalEntityManager hierarchicalEntityManager;

    @Autowired
    private DockerRegistryManager mockRegistryManager;

    @Autowired
    private UserManager mockUserManager;

    @Autowired
    private GrantPermissionManager spyPermissionManager;

    @Autowired
    private FolderManager mockFolderManager;

    @Autowired
    private RoleManager spyRoleManager;

    @Test
    public void shouldInheritPermissionsFromRegistry() {
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        mockUserContext();
        mockSid();
        initAclEntity(dockerRegistryList);
        initAclEntity(registry, AclPermission.READ);
        initAclEntity(toolGroup);
        initAclEntity(tool);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(3);
        assertThat(available.get(AclClass.TOOL).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    public void shouldLoadOnlyEntityWithPermission() {
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        mockUserContext();
        mockSid();
        initAclEntity(dockerRegistryList);
        initAclEntity(registry);
        initAclEntity(toolGroup);
        initAclEntity(tool, AclPermission.READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.TOOL).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    public void shouldLoadByRoleSid() {
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        mockUserContext();
        mockSid();
        initAclEntity(dockerRegistryList);
        initAclEntity(folder);
        initAclEntity(registry);
        initAclEntity(toolGroup);
        initAclEntity(tool,
                Collections.singletonList(new UserPermission(DefaultRoles.ROLE_USER.getName(), READ_PERMISSION)));
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(DefaultRoles.ROLE_USER.getName(), READ_PERMISSION)));

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager.loadAvailable(
                new AclSid(DefaultRoles.ROLE_USER.getName(), true), null);

        assertThat(available.size()).isEqualTo(2);
        assertThat(available.get(AclClass.TOOL).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    public void shouldLoadByRoleSidWhenLoadForUser() {
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        mockUserContext();
        mockSid();
        initAclEntity(dockerRegistryList);
        initAclEntity(folder);
        initAclEntity(registry);
        initAclEntity(toolGroup);
        initAclEntity(tool, AclPermission.READ);
        initAclEntity(runConfiguration, AclPermission.READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager.loadAvailable(
                new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(2);
        assertThat(available.get(AclClass.TOOL).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    public void shouldLoadByGroupSidWhenLoadForUser() {
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        doReturn(role).when(spyRoleManager).loadRole(anyLong());
        spyRoleManager.assignRole(role.getId(), Collections.singletonList(ID));
        mockUserContext();
        mockSid();
        initAclEntity(dockerRegistryList);
        initAclEntity(folder);
        initAclEntity(registry);
        initAclEntity(toolGroup);
        initAclEntity(tool, AclPermission.READ);
        initAclEntity(runConfiguration, AclPermission.READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager.loadAvailable(
                new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(2);
        assertThat(available.get(AclClass.TOOL).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    public void shouldInheritPermissions() {
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        doReturn(folder).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        mockSid();
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(runConfiguration);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    public void shouldUseItsOwnPermissions() {
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        folder.setPipelines(Collections.singletonList(pipeline));
        doReturn(folder).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        mockSid();
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(pipeline);
        initAclEntity(runConfiguration, AclPermission.WRITE);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(2);
        assertThat(available.get(AclClass.PIPELINE).get(0).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask())
                .isEqualTo(READ_PERMISSION + WRITE_PERMISSION);
    }

    @Test
    public void shouldUseItOwnPermissionsIfParentRestricted() {
        final RunConfiguration runConfiguration = getRunConfiguration(ID, SIMPLE_USER);
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        doReturn(folder).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        mockSid();
        initAclEntity(folder, AclPermission.NO_READ);
        initAclEntity(runConfiguration);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask()).isEqualTo(ALL_PERMISSIONS);
    }

    @Test
    public void shouldUseItOwnRestrictedPermissionsIfParentAllowed() {
        final Folder childFolder = getFolder(ID_2, ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        childFolder.setConfigurations(Collections.singletonList(runConfiguration));
        folder.setChildFolders(Collections.singletonList(childFolder));
        doReturn(folder).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        mockSid();
        initAclEntity(folder);
        initAclEntity(childFolder, AclPermission.READ);
        initAclEntity(runConfiguration, AclPermission.NO_READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.FOLDER).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    public void shouldUseItOwnPermissionsIfParentRestrictedByInheritance() {
        final Folder childFolder = getFolder(ID_2, ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, SIMPLE_USER);
        childFolder.setConfigurations(Collections.singletonList(runConfiguration));
        folder.setChildFolders(Collections.singletonList(childFolder));
        doReturn(folder).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        mockSid();
        initAclEntity(folder, AclPermission.NO_READ);
        initAclEntity(childFolder);
        initAclEntity(runConfiguration);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask()).isEqualTo(ALL_PERMISSIONS);
    }

    @Test
    public void shouldReturnOneAclClassOnly() {
        final Folder folder = getFolder(ID, SIMPLE_USER);
        final Folder childFolder = getFolder(ID_2, SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, SIMPLE_USER);
        childFolder.setConfigurations(Collections.singletonList(runConfiguration));
        folder.setChildFolders(Collections.singletonList(childFolder));
        doReturn(folder).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        mockSid();
        initAclEntity(folder);
        initAclEntity(childFolder);
        initAclEntity(runConfiguration);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), AclClass.CONFIGURATION);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask()).isEqualTo(ALL_PERMISSIONS);
    }

    private void mockSid() {
        doReturn(0).when(spyPermissionManager).getPermissionsMask(any(), anyBoolean(), anyBoolean());
    }

    private void mockUserContext() {
        doReturn(userContext).when(mockUserManager).loadUserContext(any());
    }

    private void mockDockerRegistryList() {
        doReturn(getDockerRegistryList()).when(mockRegistryManager).loadAllRegistriesContent();
    }
}

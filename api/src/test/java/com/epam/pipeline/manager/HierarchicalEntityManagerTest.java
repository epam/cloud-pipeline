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
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

public class HierarchicalEntityManagerTest extends AbstractAclTest {

    private final DockerRegistryList dockerRegistryList = getDockerRegistryList();
    private final UserContext userContext = new UserContext(ID, SIMPLE_USER);

    @Autowired
    private HierarchicalEntityManager hierarchicalEntityManager;

    @Autowired
    private DockerRegistryManager mockRegistryManager;

    @Autowired
    private UserManager mockUserManager;

    @Autowired
    private FolderManager mockFolderManager;

    @Test
    @WithMockUser
    public void shouldInheritPermissionsFromRegistry() {
        final DockerRegistry registry = getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        final Tool tool = getTool(ID, ANOTHER_SIMPLE_USER);
        final ToolGroup toolGroup = getToolGroup(ID, ANOTHER_SIMPLE_USER);
        final DockerRegistryList dockerRegistryList = getDockerRegistryList(ID, ANOTHER_SIMPLE_USER, registry);
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        toolGroup.setTools(Collections.singletonList(tool));
        registry.setGroups(Collections.singletonList(toolGroup));
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        mockUserContext();
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
    @WithMockUser
    public void shouldLoadOnlyEntityWithPermission() {
        final DockerRegistry registry = getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        final Tool tool = getTool(ID, ANOTHER_SIMPLE_USER);
        final ToolGroup toolGroup = getToolGroup(ID, ANOTHER_SIMPLE_USER);
        final DockerRegistryList dockerRegistryList = getDockerRegistryList(ID, ANOTHER_SIMPLE_USER, registry);
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        toolGroup.setTools(Collections.singletonList(tool));
        registry.setGroups(Collections.singletonList(toolGroup));
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        mockUserContext();
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
    @WithMockUser
    public void shouldLoadByRoleSid() {
        final DockerRegistry registry = getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        final Tool tool = getTool(ID, ANOTHER_SIMPLE_USER);
        final ToolGroup toolGroup = getToolGroup(ID, ANOTHER_SIMPLE_USER);
        final DockerRegistryList dockerRegistryList = getDockerRegistryList(ID, ANOTHER_SIMPLE_USER, registry);
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        toolGroup.setTools(Collections.singletonList(tool));
        registry.setGroups(Collections.singletonList(toolGroup));
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        mockUserContext();
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
    @WithMockUser
    public void shouldLoadByRoleSidWhenLoadForUser() {
        final DockerRegistry registry = getDockerRegistry(ID, ANOTHER_SIMPLE_USER);
        final ToolGroup toolGroup = getToolGroup(ID, ANOTHER_SIMPLE_USER);
        final DockerRegistryList dockerRegistryList = getDockerRegistryList(ID, ANOTHER_SIMPLE_USER, registry);
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        final Tool tool = getTool(ID, ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        toolGroup.setTools(Collections.singletonList(tool));
        registry.setGroups(Collections.singletonList(toolGroup));
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
        doReturn(folder).when(mockFolderManager).loadTree();
        mockUserContextWithRole();
        initAclEntity(dockerRegistryList);
        initAclEntity(folder);
        initAclEntity(registry);
        initAclEntity(toolGroup);
        initAclEntity(tool, DefaultRoles.ROLE_USER.getName(), AclPermission.READ);
        initAclEntity(runConfiguration, DefaultRoles.ROLE_USER.getName(), AclPermission.READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager.loadAvailable(
                new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(2);
        assertThat(available.get(AclClass.TOOL).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldInheritPermissions() {
        final Folder root = new Folder();
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        root.setChildFolders(Collections.singletonList(folder));
        doReturn(root).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(runConfiguration);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(2);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldUseItsOwnPermissions() {
        final Folder root = new Folder();
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        root.setChildFolders(Collections.singletonList(folder));
        doReturn(root).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(runConfiguration, AclPermission.WRITE);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(2);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask())
                .isEqualTo(READ_PERMISSION + WRITE_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldUseItOwnPermissionsIfParentRestricted() {
        final Folder root = new Folder();
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        root.setChildFolders(Collections.singletonList(folder));
        doReturn(root).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        initAclEntity(folder);
        initAclEntity(runConfiguration, AclPermission.READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldUseItOwnRestrictedPermissionsIfParentAllowed() {
        final Folder root = new Folder();
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        folder.setConfigurations(Collections.singletonList(runConfiguration));
        root.setChildFolders(Collections.singletonList(folder));
        doReturn(root).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(runConfiguration, AclPermission.NO_READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.FOLDER).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldUseItOwnPermissionsIfParentRestrictedByInheritance() {
        final Folder root = new Folder();
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        final Folder childFolder = getFolder(ID_2, folder.getId(), ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID_2, ANOTHER_SIMPLE_USER);
        childFolder.setConfigurations(Collections.singletonList(runConfiguration));
        root.setChildFolders(Collections.singletonList(folder));
        folder.setChildFolders(Collections.singletonList(childFolder));
        doReturn(root).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        initAclEntity(folder);
        initAclEntity(runConfiguration, AclPermission.READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), null);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldReturnOneAclClassOnly() {
        final Folder root = new Folder();
        final Folder folder = getFolder(ANOTHER_SIMPLE_USER);
        final Folder childFolder = getFolder(ID_2, folder.getId(), ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfiguration = getRunConfiguration(ID_2, ANOTHER_SIMPLE_USER);
        childFolder.setConfigurations(Collections.singletonList(runConfiguration));
        root.setChildFolders(Collections.singletonList(folder));
        folder.setChildFolders(Collections.singletonList(childFolder));
        doReturn(root).when(mockFolderManager).loadTree();
        mockDockerRegistryList();
        mockUserContext();
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(runConfiguration, AclPermission.READ);

        final Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(SIMPLE_USER, true), AclClass.CONFIGURATION);

        assertThat(available.size()).isEqualTo(1);
        assertThat(available.get(AclClass.CONFIGURATION).get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    private void mockUserContext() {
        doReturn(userContext).when(mockUserManager).loadUserContext(any());
    }

    private void mockUserContextWithRole() {
        userContext.setGroups(Collections.singletonList(DefaultRoles.ROLE_USER.getName()));
        doReturn(userContext).when(mockUserManager).loadUserContext(any());
    }

    private void mockDockerRegistryList() {
        doReturn(dockerRegistryList).when(mockRegistryManager).loadAllRegistriesContent();
    }
}

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

package com.epam.pipeline.manager;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.pipeline.FolderCrudManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.manager.ObjectCreatorUtils.*;

@DirtiesContext
@Transactional
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class HierarchicalEntityManagerTest extends AbstractManagerTest {

    private static final String USER = "OWNER";
    private static final String USER2 = "VIEWER";

    private static final String TEST_NAME = "name";
    private static final int ALL_PERMISSIONS = AclPermission.READ.getMask()
                                                | AclPermission.WRITE.getMask()
                                                | AclPermission.EXECUTE.getMask();
    private static final int ALL_PERMISSIONS_SIMPLE = AclPermission.getBasicPermissions().stream()
            .map(AclPermission::getSimpleMask).reduce((m, m2) -> m | m2).get();


    @Autowired
    private HierarchicalEntityManager hierarchicalEntityManager;

    @Autowired
    private RunConfigurationManager configurationManager;

    @Autowired
    private DockerRegistryManager registryManager;

    @Autowired
    private ToolGroupManager toolGroupManager;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private UserManager userManager;

    @Autowired
    private GrantPermissionManager permissionManager;

    @Autowired
    private FolderCrudManager folderCrudManager;

    @MockBean
    private DockerClientFactory dockerClientFactory;

    @MockBean
    private KubernetesManager kubernetesManager;

    private DockerClient client = Mockito.mock(DockerClient.class);

    @Before
    public void setUp() {
        PipelineUserVO userVO = new PipelineUserVO();
        userVO.setRoleIds(Collections.singletonList(DefaultRoles.ROLE_USER.getId()));
        userVO.setUserName(USER);
        userManager.createUser(userVO);

        userVO.setUserName(USER2);
        userManager.createUser(userVO);

        Mockito.when(dockerClientFactory.getDockerClient(Mockito.any(), Mockito.any())).thenReturn(client);
    }

    @Test
    @WithMockUser(username = USER)
    public void testToolInheritPermissionsFromRegistry() {
        DockerRegistry registry = registryManager.create(createDockerRegistryVO(TEST_NAME, USER, USER));
        ToolGroup toolGroup = toolGroupManager.create(createToolGroup(TEST_NAME, registry.getId()));
        toolManager.create(createTool(TEST_NAME, toolGroup.getId()), false);
        grantPermission(registry.getId(), AclClass.DOCKER_REGISTRY, USER2, true, AclPermission.READ.getMask());

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(USER2, true));
        Assert.assertEquals(3, available.size());
        Assert.assertEquals((int) available.get(AclClass.TOOL).get(0).getMask(), AclPermission.READ.getMask());
    }

    @Test
    @WithMockUser(username = USER)
    public void testIfOnlyToolAllowedGroupAndRegistryDontPresetInResult() {
        DockerRegistry registry = registryManager.create(createDockerRegistryVO(TEST_NAME, USER, USER));
        ToolGroup toolGroup = toolGroupManager.create(createToolGroup(TEST_NAME, registry.getId()));
        Tool tool = toolManager.create(createTool(TEST_NAME, toolGroup.getId()), false);
        grantPermission(tool.getId(), AclClass.TOOL, USER2, true, AclPermission.READ.getMask());

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(USER2, true));
        Assert.assertEquals(1, available.size());
        Assert.assertEquals((int) available.get(AclClass.TOOL).get(0).getMask(), AclPermission.READ.getMask());
    }

    @Test
    @WithMockUser(username = USER)
    public void testLoadingByRoleSidWorks() {
        DockerRegistry registry = registryManager.create(createDockerRegistryVO(TEST_NAME, USER, USER));
        ToolGroup toolGroup = toolGroupManager.create(createToolGroup(TEST_NAME, registry.getId()));
        Tool tool = toolManager.create(createTool(TEST_NAME, toolGroup.getId()), false);
        grantPermission(tool.getId(), AclClass.TOOL, DefaultRoles.ROLE_USER.getName(),
                false, AclPermission.READ.getMask());

        Folder folder = createFolder(TEST_NAME, null);
        RunConfiguration runConfiguration = createRunConfiguration(TEST_NAME, folder.getId());
        grantPermission(runConfiguration.getId(), AclClass.CONFIGURATION, DefaultRoles.ROLE_USER.getName(),
                false, AclPermission.READ.getMask());

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager.loadAvailable(
                new AclSid(DefaultRoles.ROLE_USER.getName(), false));
        Assert.assertEquals(2, available.size());
        Assert.assertEquals((int) available.get(AclClass.TOOL).get(0).getMask(), AclPermission.READ.getMask());
    }

    @Test
    @WithMockUser(username = USER)
    public void testLoadingByRoleSidWorksWhenLoadForUser() {
        DockerRegistry registry = registryManager.create(createDockerRegistryVO(TEST_NAME, USER, USER));
        ToolGroup toolGroup = toolGroupManager.create(createToolGroup(TEST_NAME, registry.getId()));
        Tool tool = toolManager.create(createTool(TEST_NAME, toolGroup.getId()), false);
        grantPermission(tool.getId(), AclClass.TOOL, DefaultRoles.ROLE_USER.getName(),
                false, AclPermission.READ.getMask());

        Folder folder = createFolder(TEST_NAME, null);
        RunConfiguration runConfiguration = createRunConfiguration(TEST_NAME, folder.getId());
        grantPermission(runConfiguration.getId(), AclClass.CONFIGURATION, DefaultRoles.ROLE_USER.getName(),
                false, AclPermission.READ.getMask());

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(USER2, true));
        Assert.assertEquals(2, available.size());
        Assert.assertEquals((int) available.get(AclClass.TOOL).get(0).getMask(), AclPermission.READ.getMask());
    }

    @Test
    @WithMockUser(username = USER)
    public void testInheritPermissions() {
        Folder folder = createFolder(TEST_NAME, null);
        createRunConfiguration(TEST_NAME, folder.getId());

        grantPermission(folder.getId(), AclClass.FOLDER, USER2, true, AclPermission.READ.getMask());

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(USER2, true));
        Assert.assertEquals(2, available.size());
        Assert.assertEquals((int) available.get(AclClass.CONFIGURATION).get(0).getMask(), AclPermission.READ.getMask());
    }

    @Test
    @WithMockUser(username = USER)
    public void testUseItsOwnPermissions() {
        Folder folder = createFolder(TEST_NAME, null);
        RunConfiguration runConfiguration = createRunConfiguration(TEST_NAME, folder.getId());

        grantPermission(folder.getId(), AclClass.FOLDER, USER2, true, AclPermission.READ.getMask());

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(USER2, true));
        Assert.assertEquals(2, available.size());
        Assert.assertEquals((int) available.get(AclClass.CONFIGURATION).get(0).getMask(), AclPermission.READ.getMask());


        grantPermission(runConfiguration.getId(), AclClass.CONFIGURATION, USER2, true,
                ALL_PERMISSIONS);

        available = hierarchicalEntityManager.loadAvailable(new AclSid(USER2, true));
        Assert.assertEquals(2, available.size());
        Assert.assertEquals((int) available.get(AclClass.CONFIGURATION).get(0).getMask(), ALL_PERMISSIONS_SIMPLE);
    }

    @Test
    @WithMockUser(username = USER)
    public void testUseItOwnPermissionsIfParentRestricted() {
        Folder folder = createFolder(TEST_NAME, null);
        RunConfiguration runConfiguration = createRunConfiguration(TEST_NAME, folder.getId());

        grantPermission(folder.getId(), AclClass.FOLDER, USER2, true, AclPermission.NO_READ.getMask());
        grantPermission(runConfiguration.getId(), AclClass.CONFIGURATION, USER2, true,
                ALL_PERMISSIONS);

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(USER2, true));
        //folder should be filtered because it forbidden
        Assert.assertEquals(1, available.size());
        Assert.assertEquals((int) available.get(AclClass.CONFIGURATION).get(0).getMask(), ALL_PERMISSIONS_SIMPLE);
    }

    @Test
    @WithMockUser(username = USER)
    public void testUseItOwnRestrictedPermissionsIfParentAllowed() {
        Folder folder = createFolder(TEST_NAME, null);
        RunConfiguration runConfiguration = createRunConfiguration(TEST_NAME, folder.getId());

        grantPermission(folder.getId(), AclClass.FOLDER, USER2, true, ALL_PERMISSIONS);
        grantPermission(runConfiguration.getId(), AclClass.CONFIGURATION, USER2, true,
                AclPermission.NO_READ.getMask());

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(USER2, true));
        //folder should be filtered because it forbidden
        Assert.assertEquals(1, available.size());
        Assert.assertEquals((int) available.get(AclClass.FOLDER).get(0).getMask(), ALL_PERMISSIONS_SIMPLE);
    }

    @Test
    @WithMockUser(username = USER)
    public void testUseItOwnPermissionsIfParentRestrictedByInheritance() {
        Folder folder = createFolder(TEST_NAME, null);
        Folder subFolder = createFolder(TEST_NAME, folder.getId());
        RunConfiguration runConfiguration = createRunConfiguration(TEST_NAME, subFolder.getId());

        grantPermission(folder.getId(), AclClass.FOLDER, USER2, true, AclPermission.NO_READ.getMask());
        grantPermission(runConfiguration.getId(), AclClass.CONFIGURATION, USER2, true,
                ALL_PERMISSIONS);

        Map<AclClass, List<AbstractSecuredEntity>> available = hierarchicalEntityManager
                .loadAvailable(new AclSid(USER2, true));
        //folder should be filtered because it forbidden
        Assert.assertEquals(1, available.size());
        Assert.assertEquals((int) available.get(AclClass.CONFIGURATION).get(0).getMask(), ALL_PERMISSIONS_SIMPLE);
    }

    private void grantPermission(Long id, AclClass aclClass, String user, boolean principal, int mask) {
        PermissionGrantVO grantVO = new PermissionGrantVO();
        grantVO.setAclClass(aclClass);
        grantVO.setUserName(user);
        grantVO.setPrincipal(principal);
        grantVO.setMask(mask);
        grantVO.setId(id);
        permissionManager.setPermissions(grantVO);
    }

    private Folder createFolder(String name, Long parentId) {
        Folder folder = ObjectCreatorUtils.createFolder(name, parentId);
        folderCrudManager.create(folder);
        return folder;
    }

    private RunConfiguration createRunConfiguration(String name, Long parentId) {
        //create
        PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setCmdTemplate("sleep 10");
        RunConfigurationEntry entry =
                createConfigEntry("entry", true, pipelineConfiguration);

        RunConfigurationVO configuration =
                createRunConfigurationVO(name, null, parentId,
                        Collections.singletonList(entry));
        return configurationManager.create(configuration);
    }
}
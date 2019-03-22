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

package com.epam.pipeline.manager.pipeline;

import java.util.List;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.util.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class ToolGroupApiServiceTest extends AbstractManagerTest {
    private static final String TEST_REPO = "repository";
    private static final String TEST_USER = "test";
    private static final String TEST_GROUP_NAME = "test";
    private static final String TEST_USER2 = "USER2";
    private static final String TOOL_GROUP_MANAGER_ROLE = "TOOL_GROUP_MANAGER";

    @Autowired
    private ToolGroupApiService toolGroupApiService;

    @Autowired
    private AclTestDao aclTestDao;

    @Autowired
    private ToolApiService toolApiService;

    @Autowired
    private DockerRegistryDao registryDao;

    @Autowired
    private ToolGroupDao toolGroupDao;

    @Autowired
    private ToolDao toolDao;

    @MockBean
    private DockerClientFactory clientFactory;

    @Mock
    private DockerClient dockerClient;

    private DockerRegistry registry;
    private ToolGroup existingGroup;
    private Tool existingTool;

    private AclTestDao.AclSid testUserSid;
    private AclTestDao.AclSid user2Sid;
    private AclTestDao.AclSid userGroupSid;

    @Before
    public void setUp() throws Exception {
        TestUtils.configureDockerClientMock(dockerClient, clientFactory);

        registry = new DockerRegistry();
        registry.setPath(TEST_REPO);
        registry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(registry);

        existingGroup = new ToolGroup();
        existingGroup.setRegistryId(registry.getId());
        existingGroup.setName(TEST_GROUP_NAME);
        existingGroup.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(existingGroup);

        // Create SID for "test" user
        testUserSid = new AclTestDao.AclSid(true, TEST_USER);
        aclTestDao.createAclSid(testUserSid);

        user2Sid = new AclTestDao.AclSid(true, TEST_USER2);
        aclTestDao.createAclSid(user2Sid);

        // And for USER group, which all users are belong to
        userGroupSid = new AclTestDao.AclSid(false, "ROLE_USER");
        aclTestDao.createAclSid(userGroupSid);

        // Mock ACL stuff
        AclTestDao.AclClass groupAclClass = new AclTestDao.AclClass(ToolGroup.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(groupAclClass);

        AclTestDao.AclClass registryAclClass = new AclTestDao.AclClass(DockerRegistry.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(registryAclClass);

        AclTestDao.AclObjectIdentity registryIdentity = new AclTestDao.AclObjectIdentity(testUserSid, registry.getId(),
                                                                 registryAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(registryIdentity);

        AclTestDao.AclObjectIdentity groupIdentity = new AclTestDao.AclObjectIdentity(testUserSid,
                                              existingGroup.getId(), groupAclClass.getId(), registryIdentity, true);
        aclTestDao.createObjectIdentity(groupIdentity);

        // Make group private
        AclTestDao.AclEntry groupAclEntry = new AclTestDao.AclEntry(groupIdentity, 1, userGroupSid,
                                                        AclPermission.ALL_DENYING_PERMISSIONS.getMask(), false);
        aclTestDao.createAclEntry(groupAclEntry);

        // TEST_USER is allowed to write
        groupAclEntry = new AclTestDao.AclEntry(groupIdentity, 2, testUserSid, AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(groupAclEntry);

        // All Test users can write to registry
        AclTestDao.AclEntry registryAclEntry = new AclTestDao.AclEntry(registryIdentity, 1, testUserSid,
                                                                       AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(registryAclEntry);

        registryAclEntry.setSid(user2Sid);
        registryAclEntry.setOrder(2);
        aclTestDao.createAclEntry(registryAclEntry);

        existingTool = ToolApiServiceTest.generateTool();
        existingTool.setToolGroup(existingGroup.getName());
        existingTool.setToolGroupId(existingGroup.getId());
        existingTool.setRegistryId(registry.getId());
        existingTool.setOwner(TEST_USER);

        toolDao.createTool(existingTool);
    }


    /**
     * Should fail on attempt to add a tool to other user's private group
     */
    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2)
    public void testCreateToolInOtherPrivateGroupFails() {
        Tool tool = ToolApiServiceTest.generateTool();

        tool.setToolGroupId(existingGroup.getId());
        tool.setToolGroup(existingGroup.getName());

        toolApiService.create(tool);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2, roles = TOOL_GROUP_MANAGER_ROLE)
    @Test
    public void testCreateToolInOwnGroupOk() {
        ToolGroup privateGroup = toolGroupApiService.createPrivate(registry.getId());

        Tool tool = ToolApiServiceTest.generateTool();
        tool.setImage(privateGroup.getName() + "/" + tool.getImage());

        tool.setToolGroupId(privateGroup.getId());
        tool.setToolGroup(privateGroup.getName());

        toolApiService.create(tool);

        ToolGroup loadedPrivateGroup = toolGroupApiService.loadPrivate(registry.getId());
        Assert.assertEquals(privateGroup.getId(), loadedPrivateGroup.getId());
        Assert.assertEquals(privateGroup.getName(), loadedPrivateGroup.getName());
        Assert.assertEquals(privateGroup.getRegistryId(), loadedPrivateGroup.getRegistryId());
        Assert.assertFalse(loadedPrivateGroup.getTools().isEmpty());
        Assert.assertTrue(loadedPrivateGroup.getTools().stream().anyMatch(t -> t.getImage().equals(tool.getImage())));
        Assert.assertTrue(loadedPrivateGroup.isPrivateGroup());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2, roles = TOOL_GROUP_MANAGER_ROLE)
    @Test
    public void testPrivateFlagIsSet() {
        toolGroupApiService.createPrivate(registry.getId());
        createToolGroup();

        List<ToolGroup> groups = toolGroupApiService.loadByRegistryId(registry.getId());
        Assert.assertTrue(groups.size() > 1);
        Assert.assertTrue(groups.stream().anyMatch(g -> g.isPrivateGroup()));
        Assert.assertTrue(groups.stream().anyMatch(g -> !g.isPrivateGroup()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2, roles = TOOL_GROUP_MANAGER_ROLE)
    @Test
    public void testLoadOtherPrivateGroupFails() {
        ToolGroup privateGroup = toolGroupApiService.createPrivate(registry.getId());

        List<ToolGroup> groups = toolGroupApiService.loadByRegistryId(registry.getId());
        Assert.assertTrue(groups.stream().noneMatch(g -> g.getName().equals(existingGroup.getName())));
        Assert.assertTrue(groups.stream().anyMatch(g -> g.getName().equals(privateGroup.getName())));

        groups = toolGroupApiService.loadByRegistryNameOrId(registry.getPath());
        Assert.assertTrue(groups.stream().noneMatch(g -> g.getName().equals(existingGroup.getName())));
        Assert.assertTrue(groups.stream().anyMatch(g -> g.getName().equals(privateGroup.getName())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2, roles = TOOL_GROUP_MANAGER_ROLE)
    @Test()
    public void testLoadOtherPrivateGroupFails2() {
        ToolGroup group = toolGroupApiService.load(existingGroup.getId());
        Assert.assertTrue(group.getTools().isEmpty());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2, roles = TOOL_GROUP_MANAGER_ROLE)
    @Test()
    public void testCreateToolGroup() {
        ToolGroup group = createToolGroup();

        ToolGroup loaded = toolGroupApiService.load(group.getId());
        Assert.assertEquals(group.getName(), loaded.getName());
        Assert.assertEquals(group.getRegistryId(), loaded.getRegistryId());
        Assert.assertEquals(TEST_USER2, loaded.getOwner());
    }

    private ToolGroup createToolGroup() {
        ToolGroup group = new ToolGroup();
        group.setName("dev");
        group.setRegistryId(registry.getId());

        group = toolGroupApiService.create(group);
        return group;
    }
}

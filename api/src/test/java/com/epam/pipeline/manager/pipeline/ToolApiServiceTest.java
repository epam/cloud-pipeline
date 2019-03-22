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

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.util.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class ToolApiServiceTest extends AbstractManagerTest {
    private static final String TEST_REPO = "repository";
    private static final String TEST_USER = "TEST";
    private static final String TEST_IMAGE = "image";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TEST_GROUP_NAME = "test";

    @Autowired
    private ToolApiService toolApiService;

    @Autowired
    private DockerRegistryDao registryDao;

    @Autowired
    private ToolGroupManager toolGroupManager;

    @Autowired
    private GrantPermissionManager grantPermissionManager;

    @Autowired
    private AclTestDao aclTestDao;

    @MockBean
    private DockerClientFactory clientFactory;

    @Mock
    private DockerClient dockerClient;

    private DockerRegistry registry;
    private ToolGroup allowedGroup;

    private AclTestDao.AclSid testUserSid;

    @Before
    public void setUp() {
        TestUtils.configureDockerClientMock(dockerClient, clientFactory);

        registry = new DockerRegistry();
        registry.setPath(TEST_REPO);
        registry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(registry);

        // Create SID for "test" user
        testUserSid = new AclTestDao.AclSid(true, TEST_USER);
        aclTestDao.createAclSid(testUserSid);

        AclTestDao.AclClass registryAclClass = new AclTestDao.AclClass(DockerRegistry.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(registryAclClass);

        AclTestDao.AclObjectIdentity registryIdentity = new AclTestDao.AclObjectIdentity(testUserSid, registry.getId(),
                                                                 registryAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(registryIdentity);

        AclTestDao.AclEntry registryAclEntry = new AclTestDao.AclEntry(registryIdentity, 1, testUserSid,
                                                                       AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(registryAclEntry);

        allowedGroup = new ToolGroup();
        allowedGroup.setRegistryId(registry.getId());
        allowedGroup.setName(TEST_GROUP_NAME);
        toolGroupManager.create(allowedGroup);

        PermissionGrantVO grantVO = new PermissionGrantVO();
        grantVO.setAclClass(AclClass.TOOL_GROUP);
        grantVO.setId(allowedGroup.getId());
        grantVO.setMask(AclPermission.WRITE.getMask());
        grantVO.setPrincipal(true);
        grantVO.setUserName(TEST_USER);

        grantPermissionManager.setPermissions(grantVO);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER)
    public void testCreate() {
        Tool tool = generateTool();

        tool.setToolGroupId(allowedGroup.getId());
        tool.setToolGroup(allowedGroup.getName());

        toolApiService.create(tool);
    }

    static Tool generateTool() {
        Tool tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        return tool;
    }
}

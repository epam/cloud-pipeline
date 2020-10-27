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

package com.epam.pipeline.manager.configuration;

import com.epam.pipeline.acl.configuration.RunConfigurationApiService;
import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.dao.configuration.RunConfigurationDao;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.*;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.manager.ObjectCreatorUtils.*;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class RunConfigurationApiServiceTest extends AbstractManagerTest {
    private static final String TEST_USER_1 = "TEST_USER_1";
    private static final String TEST_USER_2 = "TEST_USER_2";
    private static final String TEST_NAME = "testName";
    private static final String TEST_DESCRIPTION = "testDescription";
    private static final String TEST_FOLDER_NAME = "testFolderName";
    private static final String TEST_CONFIGURATION_MANAGER_ROLE = "CONFIGURATION_MANAGER";
    private static final String TEST_IMAGE = "library/image";
    private static final String TOOL_GROUP_NAME = "repository/TEST_USER_1";
    private static final String TEST_REPO = "///";
    private static final String TEST_REPO_SSH = "git@test";

    @Autowired
    private RunConfigurationApiService runConfigurationApiService;

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private RunConfigurationDao runConfigurationDao;

    @Autowired
    private ToolDao toolDao;

    @Autowired
    private DockerRegistryDao registryDao;

    @Autowired
    private AclTestDao aclTestDao;

    @Autowired
    private ToolGroupDao toolGroupDao;

    private AclTestDao.AclSid testUserSid1;
    private AclTestDao.AclSid testUserSid2;
    private RunConfigurationVO runConfigurationVO;
    private RunConfigurationVO updatedRunConfigurationVO;
    private DockerRegistry dockerRegistry;
    private ToolGroup toolGroup;

    @Before
    public void setUp() {

        // Create SIDs for "test" users
        testUserSid1 = new AclTestDao.AclSid(true, TEST_USER_1);
        aclTestDao.createAclSid(testUserSid1);

        testUserSid2 = new AclTestDao.AclSid(true, TEST_USER_2);
        aclTestDao.createAclSid(testUserSid2);

        Folder sourceFolder = new Folder();
        sourceFolder.setName(TEST_FOLDER_NAME);
        sourceFolder.setOwner(TEST_USER_1);
        folderDao.createFolder(sourceFolder);
        Long folderId = folderDao.loadFolderByName(TEST_FOLDER_NAME).getId();

        AclTestDao.AclClass folderAclClass = new AclTestDao.AclClass(Folder.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(folderAclClass);
        AclTestDao.AclObjectIdentity folderIdentity = new AclTestDao.AclObjectIdentity(testUserSid1, folderId,
                folderAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(folderIdentity);

        AclTestDao.AclEntry folderAclEntry = new AclTestDao.AclEntry(folderIdentity, 1, testUserSid1,
                AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(folderAclEntry);

        Pipeline pipeline = constructPipeline(TEST_NAME, TEST_REPO, TEST_REPO_SSH, folderId);
        pipeline.setOwner(TEST_USER_1);
        pipelineDao.createPipeline(pipeline);
        Pipeline loadPipelineByName = pipelineDao.loadPipelineByName(TEST_NAME);

        AclTestDao.AclClass pipelineAclClass = new AclTestDao.AclClass(Pipeline.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(pipelineAclClass);
        AclTestDao.AclObjectIdentity pipelineIdentity = new AclTestDao.AclObjectIdentity(testUserSid1,
                loadPipelineByName.getId(), pipelineAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(pipelineIdentity);

        AclTestDao.AclEntry pipelineAclEntry = new AclTestDao.AclEntry(pipelineIdentity, 1, testUserSid1,
                AclPermission.EXECUTE.getMask(), true);
        aclTestDao.createAclEntry(pipelineAclEntry);

        PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        RunConfigurationEntry entryWithPipeline = createConfigEntry(TEST_NAME, true, pipelineConfiguration);
        entryWithPipeline.setPipelineId(pipeline.getId());
        entryWithPipeline.setPipelineVersion(TEST_NAME);
        runConfigurationVO = createRunConfigurationVO(
                TEST_NAME, TEST_DESCRIPTION, sourceFolder.getId(), Collections.singletonList(entryWithPipeline));

        ToolGroup toolGroup = createToolGroup();
        Tool tool = createTool(toolGroup);
        RunConfigurationEntry entryWithTool = createConfigEntry(TEST_NAME, true, pipelineConfiguration);
        pipelineConfiguration.setCmdTemplate(TEST_NAME);
        pipelineConfiguration.setDockerImage(tool.getImage());
        entryWithTool.setConfiguration(pipelineConfiguration);
        updatedRunConfigurationVO = createRunConfigurationVO(
                TEST_NAME, TEST_DESCRIPTION, sourceFolder.getId(), Collections.singletonList(entryWithTool));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER_1, roles = TEST_CONFIGURATION_MANAGER_ROLE)
    @Test
    public void testSaveConfigurationByUserHasPermission() {
        runConfigurationApiService.save(runConfigurationVO);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER_2)
    @Test(expected = AccessDeniedException.class)
    public void testSaveConfigurationByUserHasNoPermission() {
        runConfigurationApiService.save(runConfigurationVO);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER_1, roles = TEST_CONFIGURATION_MANAGER_ROLE)
    @Test
    public void testUpdateConfigurationByUserHasPermission() {
        runConfigurationApiService.save(runConfigurationVO);

        List<RunConfiguration> runConfigurations = runConfigurationDao.loadAll();
        updatedRunConfigurationVO.setId(runConfigurations.get(0).getId());

        runConfigurationApiService.update(updatedRunConfigurationVO);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER_1, roles = TEST_CONFIGURATION_MANAGER_ROLE)
    @Test
    public void testLoadConfigurationByUserHasPermission() {
        runConfigurationApiService.save(runConfigurationVO);
        Long id = runConfigurationDao.loadAll().get(0).getId();

        runConfigurationApiService.load(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER_2, roles = TEST_CONFIGURATION_MANAGER_ROLE)
    @Test(expected = AccessDeniedException.class)
    public void testLoadConfigurationByUserNoPermission() {
        runConfigurationApiService.save(runConfigurationVO);
        Long id = runConfigurationDao.loadAll().get(0).getId();

        runConfigurationApiService.load(id);
    }

    private Tool createTool(ToolGroup toolGroup) {
        Tool tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setCpu("10");
        tool.setRam("10");
        tool.setOwner(TEST_USER_1);
        tool.setToolGroupId(toolGroup.getId());
        tool.setRegistryId(dockerRegistry.getId());
        toolDao.createTool(tool);

        Tool loadedTool = toolDao.loadToolsByGroup(tool.getToolGroupId()).get(0);
        return loadedTool;
    }

    private ToolGroup createToolGroup() {
        DockerRegistry registry = new DockerRegistry();
        registry.setPath("repository");
        registry.setOwner(TEST_USER_1);
        registryDao.createDockerRegistry(registry);

        dockerRegistry = registryDao.loadDockerRegistry(registry.getPath());

        toolGroup = new ToolGroup();
        toolGroup.setName(TOOL_GROUP_NAME);
        toolGroup.setRegistryId(dockerRegistry.getId());
        toolGroup.setOwner(TEST_USER_1);
        toolGroupDao.createToolGroup(toolGroup);

        return toolGroupDao.loadToolGroup(TOOL_GROUP_NAME, registry.getId()).get();
    }
}

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

import static com.epam.pipeline.manager.ObjectCreatorUtils.constructPipeline;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class FolderApiServiceTest extends AbstractManagerTest {
    private static final String TEST_USER = "USER1";
    private static final String TEST_USER2 = "USER2";
    private static final String FOLDER_MANAGER_ROLE = "FOLDER_MANAGER";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String FOLDER_NAME = "Folder name";
    private static final String TEST_PIPELINE1 = "Pipeline1";
    private static final String TEST_PIPELINE_REPO = "///";
    private static final String TEST_PIPELINE_REPO_SSH = "git@test";

    @Autowired
    private FolderApiService folderApiService;

    @Autowired
    private AclTestDao aclTestDao;

    @Autowired
    private FolderDao folderDao;

    @Mock
    private FolderManager folderManager;

    @Autowired
    private PipelineDao pipelineDao;

    private AclTestDao.AclSid testUserSid;
    private AclTestDao.AclSid testUser2Sid;
    private AclTestDao.AclEntry parentAclEntry;
    private AclTestDao.AclObjectIdentity parentAclObjectIdentity;
    private AclTestDao.AclEntry childAclEntry;
    private AclTestDao.AclObjectIdentity childAclObjectIdentity;
    private AclTestDao.AclEntry pipelineAclEntry;
    private AclTestDao.AclObjectIdentity pipelineAclObjectIdentity;
    private Folder parent;
    private Folder childFolder;
    private Pipeline pipeline1;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(folderApiService, "folderManager", folderManager);

        testUserSid = new AclTestDao.AclSid(true, TEST_USER);
        aclTestDao.createAclSid(testUserSid);
        testUser2Sid = new AclTestDao.AclSid(true, TEST_USER2);
        aclTestDao.createAclSid(testUser2Sid);

        AclTestDao.AclClass folderAclClass = new AclTestDao.AclClass(Folder.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(folderAclClass);

        AclTestDao.AclClass pipelineAclClass = new AclTestDao.AclClass(Pipeline.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(pipelineAclClass);

        //Initial creating of parent folder
        parent = new Folder();
        parent.setName("testParent");
        parent.setOwner(TEST_USER);
        folderDao.createFolder(parent);

        childFolder = new Folder();
        childFolder.setName(FOLDER_NAME);
        childFolder.setParentId(parent.getId());
        childFolder.setOwner(TEST_USER2);

        pipeline1 = constructPipeline(TEST_PIPELINE1, TEST_PIPELINE_REPO, TEST_PIPELINE_REPO_SSH, childFolder.getId());
        pipeline1.setOwner(TEST_USER2);
        pipelineDao.createPipeline(pipeline1);

        childFolder.getPipelines().add(pipeline1);
        childFolder.setParent(parent);
        folderDao.createFolder(childFolder);

        //ACL's for Parent folder
        parentAclObjectIdentity = new AclTestDao.AclObjectIdentity(testUserSid,
                parent.getId(), folderAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(parentAclObjectIdentity);

        parentAclEntry = new AclTestDao.AclEntry(parentAclObjectIdentity, 1, testUserSid,
                AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(parentAclEntry);

        pipelineAclObjectIdentity = new AclTestDao.AclObjectIdentity(testUser2Sid,
                pipeline1.getId(), pipelineAclClass.getId(), parentAclObjectIdentity, false);
        aclTestDao.createObjectIdentity(pipelineAclObjectIdentity);

        pipelineAclEntry = new AclTestDao.AclEntry(pipelineAclObjectIdentity, 1, testUser2Sid,
                AclPermission.READ.getMask(), false);
        aclTestDao.createAclEntry(pipelineAclEntry);

        childAclObjectIdentity = new AclTestDao.AclObjectIdentity(testUser2Sid,
                childFolder.getId(), folderAclClass.getId(), parentAclObjectIdentity, true);
        aclTestDao.createObjectIdentity(childAclObjectIdentity);

        childAclEntry = new AclTestDao.AclEntry(childAclObjectIdentity, 1, testUser2Sid,
                AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(childAclEntry);

        // deny write for child folder fro TEST_USER
        AclTestDao.AclEntry denyChildAclEntry = new AclTestDao.AclEntry(childAclObjectIdentity, 2, testUserSid,
                AclPermission.NO_WRITE.getMask(), true);
        aclTestDao.createAclEntry(denyChildAclEntry);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER, roles = FOLDER_MANAGER_ROLE)
    public void testCreateUpdateCloneDeleteFolderByAllowedUser() {
        Folder folder = new Folder();
        folder.setName(FOLDER_NAME);
        folder.setParentId(parent.getId());
        folder.setId(parent.getId());

        when(folderManager.create(folder)).thenReturn(folder);
        folderApiService.create(folder);

        when(folderManager.load(anyLong())).thenReturn(folder);
        folderApiService.load(folder.getId());

        String newName = "New name";
        folder.setName(newName);
        when(folderManager.update(folder)).thenReturn(folder);
        folderApiService.update(folder);

        assertEquals(newName, folderApiService.load(folder.getId()).getName());

        when(folderManager.lockFolder(anyLong())).thenReturn(folder);
        folderApiService.lockFolder(folder.getId());
        when(folderManager.unlockFolder(anyLong())).thenReturn(folder);
        folderApiService.unlockFolder(folder.getId());

        when(folderManager.cloneFolder(anyLong(), anyLong(), anyString())).thenReturn(folder);
        folderApiService.cloneFolder(folder.getId(), parent.getId(), "Name");

        when(folderManager.delete(anyLong())).thenReturn(folder);
        folderApiService.delete(folder.getId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER, roles = FOLDER_MANAGER_ROLE)
    public void testCreateTemplateByAllowedUser() {
        createTemplateAndGetProject();
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER2)
    public void testCreateTemplateByNotAllowedUser() {
        createTemplateAndGetProject();
    }

    private void createTemplateAndGetProject() {
        Folder folder = new Folder();
        folder.setName(FOLDER_NAME);
        folder.setParentId(parent.getId());

        when(folderManager.create(folder)).thenReturn(folder);
        folderApiService.create(folder);

        when(folderManager.createFromTemplate(anyObject(), anyString())).thenReturn(folder);
        folderApiService.createFromTemplate(folder, "template");

        FolderWithMetadata folderWithMetadata = new FolderWithMetadata();
        folderWithMetadata.setId(parent.getId());
        when(folderManager.getProject(anyLong(), anyObject())).thenReturn(folderWithMetadata);
        folderApiService.getProject(folderWithMetadata.getId(), AclClass.FOLDER);
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2)
    public void testDeleteFolderByNotAdminOrFolderManagerAndWrite() {
        when(folderManager.delete(anyLong())).thenReturn(parent);

        folderApiService.delete(parent.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2)
    public void testLockByNotAdminOrOwner() {
        when(folderManager.lockFolder(anyLong())).thenReturn(parent);

        folderApiService.lockFolder(parent.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2)
    public void testUnlockByNotAdminOrOwner() {
        when(folderManager.unlockFolder(anyLong())).thenReturn(parent);

        folderApiService.unlockFolder(parent.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2)
    public void testLoadByIDOrPathNotAdminOrReadPermission() {
        when(folderManager.loadByNameOrId(anyString())).thenReturn(parent);

        folderApiService.loadByIdOrPath(parent.getId().toString());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = TEST_USER2)
    public void testCreateFolderByNotAdminOrFolderManagerAndWrite() {
        Folder folder = new Folder();
        folder.setName(FOLDER_NAME);
        folder.setParentId(parent.getId());
        when(folderManager.create(folder)).thenReturn(folder);

        folderApiService.create(folder);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER2, roles = ADMIN_ROLE)
    public void testDeleteFolderByAdmin() {
        Folder folder = new Folder();
        folder.setName(FOLDER_NAME);
        folder.setParentId(parent.getId());

        when(folderManager.create(folder)).thenReturn(folder);
        Folder createdFolder = folderApiService.create(folder);

        when(folderManager.delete(anyLong())).thenReturn(folder);
        folderApiService.delete(createdFolder.getId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER2, roles = ADMIN_ROLE)
    public void testDeleteForceFolderByFolderManagerWithPermissionToChildren() {
        when(folderManager.deleteForce(anyLong())).thenReturn(childFolder);

        folderApiService.deleteForce(childFolder.getId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER, roles = ADMIN_ROLE)
    public void testDeleteForceFolderByAdmin() {
        when(folderManager.deleteForce(anyLong())).thenReturn(parent);

        folderApiService.deleteForce(parent.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER, roles = FOLDER_MANAGER_ROLE)
    public void testDeleteForceFolderByUserWithoutPermissionToChildren() {
        folderApiService.deleteForce(parent.getId());
    }
}

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
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineWithPermissions;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.epam.pipeline.manager.ObjectCreatorUtils.constructPipeline;
import static org.junit.Assert.assertEquals;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class PipelineWithPermissionsTest extends AbstractManagerTest {
    private static final String TEST_FOLDER1 = "testFolder1";
    private static final String TEST_FOLDER2 = "testFolder2";
    private static final String TEST_OWNER1 = "testUser1";
    private static final String TEST_OWNER2 = "testUser2";
    private static final String TEST_PIPELINE1 = "Pipeline1";
    private static final String TEST_PIPELINE2 = "Pipeline2";
    private static final String TEST_PIPELINE_REPO = "///";

    @Autowired
    private AclTestDao aclTestDao;

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private GrantPermissionManager permissionManager;

    private Folder folder1;
    private Folder folder2;
    private Pipeline pipeline1;
    private Pipeline pipeline2;
    private AclTestDao.AclSid testUserSid1;
    private AclTestDao.AclSid testUserSid2;

    @Before
    public void setUp() {
        AclTestDao.AclClass folderAclClass = new AclTestDao.AclClass(Folder.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(folderAclClass);

        AclTestDao.AclClass pipelineAclClass = new AclTestDao.AclClass(Pipeline.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(pipelineAclClass);

        testUserSid1 = new AclTestDao.AclSid(true, TEST_OWNER1);
        aclTestDao.createAclSid(testUserSid1);

        testUserSid2 = new AclTestDao.AclSid(true, TEST_OWNER2);
        aclTestDao.createAclSid(testUserSid2);

        folder1 = getFolder(TEST_FOLDER1, TEST_OWNER1);
        folderDao.createFolder(folder1);

        folder2 = getFolder(TEST_FOLDER2, TEST_OWNER2);
        folder2.setParentId(folder1.getId());
        folderDao.createFolder(folder2);

        pipeline1 = constructPipeline(TEST_PIPELINE1, TEST_PIPELINE_REPO, folder2.getId());
        pipeline1.setOwner(TEST_OWNER1);
        pipelineDao.createPipeline(pipeline1);

        pipeline2 = constructPipeline(TEST_PIPELINE2, TEST_PIPELINE_REPO, folder1.getId());
        pipeline2.setOwner(TEST_OWNER1);
        pipelineDao.createPipeline(pipeline2);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_OWNER1)
    public void testMergePermissions() {
        // key - pipelineId, value - <sid, mask>
        Map<Long, Map<String, Integer>> expectedMap = new HashMap<>();
        HashMap<String, Integer> permissionsForPipeline1 = new HashMap<>();
        HashMap<String, Integer> permissionsForPipeline2 = new HashMap<>();

        grantPermissionToFolder(AclPermission.READ.getMask(), testUserSid1.getSid(), folder1.getId());
        grantPermissionToFolder(AclPermission.READ.getMask(), testUserSid1.getSid(), folder2.getId());
        grantPermissionsToPipeline(AclPermission.WRITE.getMask(), testUserSid1.getSid(), pipeline1.getId());
        grantPermissionsToPipeline(AclPermission.READ.getMask(), testUserSid1.getSid(), pipeline2.getId());

        permissionsForPipeline1.put(TEST_OWNER1.toUpperCase(), 5);
        permissionsForPipeline2.put(TEST_OWNER1.toUpperCase(), 1);
        expectedMap.put(pipeline1.getId(), permissionsForPipeline1);
        expectedMap.put(pipeline2.getId(), permissionsForPipeline2);
        assertPipelineWithPermissions(expectedMap);

        grantPermissionsToPipeline(AclPermission.WRITE.getMask(), testUserSid2.getSid(), pipeline1.getId());
        permissionsForPipeline1.put(TEST_OWNER2.toUpperCase(), 4);
        assertPipelineWithPermissions(expectedMap);

        grantPermissionToFolder(AclPermission.READ.getMask(), testUserSid2.getSid(), folder1.getId());
        permissionsForPipeline1.put(TEST_OWNER2.toUpperCase(), 5);
        permissionsForPipeline2.put(TEST_OWNER2.toUpperCase(), 1);
        assertPipelineWithPermissions(expectedMap);

        grantPermissionToFolder(AclPermission.NO_READ.getMask(), testUserSid2.getSid(), folder1.getId());
        grantPermissionToFolder(AclPermission.READ.getMask(), testUserSid2.getSid(), folder2.getId());
        permissionsForPipeline2.put(TEST_OWNER2.toUpperCase(), 2);
        assertPipelineWithPermissions(expectedMap);

        grantPermissionToFolder(AclPermission.WRITE.getMask(), testUserSid2.getSid(), folder1.getId());
        grantPermissionsToPipeline(AclPermission.NO_WRITE.getMask(), testUserSid2.getSid(), pipeline1.getId());
        permissionsForPipeline1.put(TEST_OWNER2.toUpperCase(), 9);
        permissionsForPipeline2.put(TEST_OWNER2.toUpperCase(), 4);
        assertPipelineWithPermissions(expectedMap);
    }

    private void assertPipelineWithPermissions(Map<Long, Map<String, Integer>> expectedMap) {
        Set<PipelineWithPermissions> loaded = permissionManager.loadAllPipelinesWithPermissions(null, null)
                .getPipelines();
        loaded.forEach(pipelineWithPermissions -> {
            Map<String, Integer> expectedPermissions = expectedMap.get(pipelineWithPermissions.getId());
            Set<AclPermissionEntry> actualPermissions = pipelineWithPermissions.getPermissions();
            assertEquals(expectedPermissions.size(), actualPermissions.size());
            actualPermissions.forEach(permission -> {
                String sid = permission.getSid().getName().toUpperCase();
                assertEquals(expectedPermissions.get(sid), permission.getMask());
            });
        });
    }

    private void grantPermissionsToPipeline(Integer mask, String user, Long pipelineId) {
        grantPermissions(mask, user, AclClass.PIPELINE, pipelineId);
    }

    private void grantPermissionToFolder(Integer mask, String user, Long folderId) {
        grantPermissions(mask, user, AclClass.FOLDER, folderId);
    }

    private void grantPermissions(Integer mask, String user, AclClass aclClass, Long entityId) {
        PermissionGrantVO grantVO = new PermissionGrantVO();
        grantVO.setAclClass(aclClass);
        grantVO.setId(entityId);
        grantVO.setMask(mask);
        grantVO.setPrincipal(true);
        grantVO.setUserName(user);
        permissionManager.setPermissions(grantVO);
    }

    private Folder getFolder(String name, String owner) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setOwner(owner);
        return folder;
    }
}

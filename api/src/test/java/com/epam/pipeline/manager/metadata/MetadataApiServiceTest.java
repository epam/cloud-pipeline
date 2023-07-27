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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
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

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class MetadataApiServiceTest extends AbstractManagerTest {
    private static final String TEST_USER1 = "USER1";
    private static final long TEST_USER1_ID = 1L;
    private static final String TEST_USER2 = "USER2";
    private static final String ADMIN_ROLE = "ADMIN";

    @Autowired
    private MetadataApiService metadataApiService;
    @Autowired
    private AclTestDao aclTestDao;
    @Autowired
    private GrantPermissionManager grantPermissionManager;

    @Mock
    private MetadataManager metadataManager;
    @Mock
    private UserManager userManager;

    private final EntityVO pipelineUserEntity = new EntityVO(TEST_USER1_ID, AclClass.PIPELINE_USER);
    private final EntityVO roleEntity = new EntityVO(TEST_USER1_ID, AclClass.ROLE);
    private MetadataVO pipelineUserMetadataVO;
    private MetadataVO roleMetadataVO;
    private MetadataEntry pipelineUserMetadata;
    private MetadataEntry roleMetadata;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(metadataApiService, "metadataManager", metadataManager);
        ReflectionTestUtils.setField(grantPermissionManager, "userManager", userManager);

        final AclTestDao.AclSid testUserSid1 = new AclTestDao.AclSid(true, TEST_USER1);
        aclTestDao.createAclSid(testUserSid1);
        final AclTestDao.AclSid testUserSid2 = new AclTestDao.AclSid(true, TEST_USER2);
        aclTestDao.createAclSid(testUserSid2);
        final PipelineUser pipelineUser1 = new PipelineUser();
        pipelineUser1.setId(TEST_USER1_ID);
        pipelineUser1.setUserName(TEST_USER1);

        pipelineUserMetadata = new MetadataEntry();
        pipelineUserMetadata.setEntity(pipelineUserEntity);
        roleMetadata = new MetadataEntry();
        roleMetadata.setEntity(roleEntity);
        pipelineUserMetadataVO = new MetadataVO();
        pipelineUserMetadataVO.setEntity(pipelineUserEntity);
        roleMetadataVO = new MetadataVO();
        roleMetadataVO.setEntity(roleEntity);

        doReturn(pipelineUser1).when(userManager).load(TEST_USER1_ID);
        doReturn(pipelineUser1).when(userManager).loadByNameOrId(TEST_USER1);
        doReturn(Collections.singletonList(pipelineUserMetadata)).when(metadataManager)
                .listMetadataItems(Collections.singletonList(pipelineUserEntity));
        doReturn(Collections.singletonList(roleMetadata)).when(metadataManager)
                .listMetadataItems(Collections.singletonList(roleEntity));
        doReturn(pipelineUserMetadata).when(metadataManager)
                .findMetadataEntryByNameOrId(TEST_USER1, AclClass.PIPELINE_USER);
        doReturn(roleMetadata).when(metadataManager).findMetadataEntryByNameOrId(TEST_USER1, AclClass.ROLE);
        doReturn(pipelineUserMetadata).when(metadataManager).updateMetadataItemKey(pipelineUserMetadataVO);
        doReturn(roleMetadata).when(metadataManager).updateMetadataItemKey(roleMetadataVO);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1, roles = ADMIN_ROLE)
    public void adminShouldLoadMetadataForPipelineUser() {
        final List<MetadataEntry> metadataEntries = metadataApiService
                .listMetadataItems(Collections.singletonList(pipelineUserEntity));
        assertEquals(Collections.singletonList(pipelineUserMetadata), metadataEntries);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1)
    public void ownerShouldLoadMetadataForPipelineUser() {
        final List<MetadataEntry> metadataEntries = metadataApiService
                .listMetadataItems(Collections.singletonList(pipelineUserEntity));
        assertEquals(Collections.singletonList(pipelineUserMetadata), metadataEntries);
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER2)
    public void nonAdminAndNotOwnerShouldNotLoadMetadataForPipelineUser() {
        metadataApiService.listMetadataItems(Collections.singletonList(pipelineUserEntity));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1, roles = ADMIN_ROLE)
    public void adminShouldLoadMetadataForRole() {
        final List<MetadataEntry> metadataEntries = metadataApiService
                .listMetadataItems(Collections.singletonList(roleEntity));
        assertEquals(Collections.singletonList(roleMetadata), metadataEntries);
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1)
    public void nonAdminShouldNotLoadMetadataForRole() {
        metadataApiService.listMetadataItems(Collections.singletonList(roleEntity));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1, roles = ADMIN_ROLE)
    public void adminShouldFindMetadataForPipelineUser() {
        final MetadataEntry metadataEntityIdByName = metadataApiService
                .findMetadataEntityIdByName(TEST_USER1, AclClass.PIPELINE_USER);
        assertEquals(pipelineUserEntity, metadataEntityIdByName.getEntity());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1)
    public void ownerShouldFindMetadataForPipelineUser() {
        final MetadataEntry metadataEntityIdByName = metadataApiService
                .findMetadataEntityIdByName(TEST_USER1, AclClass.PIPELINE_USER);
        assertEquals(pipelineUserEntity, metadataEntityIdByName.getEntity());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER2)
    public void nonAdminAndNotOwnerShouldNotFindMetadataForRole() {
        metadataApiService.findMetadataEntityIdByName(TEST_USER1, AclClass.PIPELINE_USER);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1, roles = ADMIN_ROLE)
    public void adminShouldFindMetadataForRole() {
        final MetadataEntry metadataEntityIdByName = metadataApiService
                .findMetadataEntityIdByName(TEST_USER1, AclClass.ROLE);
        assertEquals(roleEntity, metadataEntityIdByName.getEntity());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1)
    public void nonAdminShouldNotFindMetadataForRole() {
        metadataApiService.findMetadataEntityIdByName(TEST_USER1, AclClass.ROLE);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1, roles = ADMIN_ROLE)
    public void adminShouldUpdateMetadataForPipelineUser() {
        final MetadataEntry metadataEntry = metadataApiService.updateMetadataItemKey(pipelineUserMetadataVO);
        assertEquals(pipelineUserEntity, metadataEntry.getEntity());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1)
    public void ownerShouldUpdateMetadataForPipelineUser() {
        final MetadataEntry metadataEntry = metadataApiService.updateMetadataItemKey(pipelineUserMetadataVO);
        assertEquals(pipelineUserEntity, metadataEntry.getEntity());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER2)
    public void nonAdminAndNotOwnerShouldNotUpdateMetadataForPipelineUser() {
        metadataApiService.updateMetadataItemKey(pipelineUserMetadataVO);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1, roles = ADMIN_ROLE)
    public void adminShouldUpdateMetadataForRole() {
        final MetadataEntry metadataEntry = metadataApiService.updateMetadataItemKey(roleMetadataVO);
        assertEquals(roleEntity, metadataEntry.getEntity());
    }

    @Test(expected = AccessDeniedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_USER1)
    public void nonAdminShouldNotUpdateMetadataForRole() {
        metadataApiService.updateMetadataItemKey(roleMetadataVO);
    }
}

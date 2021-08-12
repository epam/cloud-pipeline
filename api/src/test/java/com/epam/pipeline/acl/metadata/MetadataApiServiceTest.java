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

package com.epam.pipeline.acl.metadata;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_SET;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class MetadataApiServiceTest extends AbstractAclTest {

    private static final AclClass ENTITY_ACL_CLASS = AclClass.DATA_STORAGE;
    private static final AclClass PIPELINE_USER_ACL_CLASS = AclClass.PIPELINE_USER;
    private static final AclClass ROLE_ACL_CLASS = AclClass.ROLE;
    private static final String SENSITIVE_KEY = "sensitive";
    private static final String NON_SENSITIVE_KEY = "type";

    private final MetadataVO metadataVO = MetadataCreatorUtils.getMetadataVO(ENTITY_ACL_CLASS);
    private final MetadataVO pipelineUserVO = MetadataCreatorUtils.getMetadataVO(PIPELINE_USER_ACL_CLASS);
    private final MetadataVO roleVO = MetadataCreatorUtils.getMetadataVO(ROLE_ACL_CLASS);
    private final AbstractSecuredEntity entity =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);
    private final AbstractSecuredEntity entityWithOwner =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID, SIMPLE_USER);
    private final AbstractSecuredEntity anotherEntity =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID_2, ANOTHER_SIMPLE_USER);
    private final EntityVO entityVO = SecurityCreatorUtils.getEntityVO(ID, ENTITY_ACL_CLASS);
    private final EntityVO anotherEntityVO = SecurityCreatorUtils.getEntityVO(ID_2, ENTITY_ACL_CLASS);
    private final MetadataEntry metadataEntry = MetadataCreatorUtils.getMetadataEntry(entityVO);
    private final MultipartFile file = new MockMultipartFile(TEST_STRING, TEST_STRING.getBytes());
    private final MetadataEntryWithIssuesCount entry = MetadataCreatorUtils.getMetadataEntryWithIssuesCount(entityVO);
    private final MetadataEntryWithIssuesCount anotherEntry =
            MetadataCreatorUtils.getMetadataEntryWithIssuesCount(anotherEntityVO);
    private final EntityVO pipelineUserEntityVO = SecurityCreatorUtils.getEntityVO(ID, PIPELINE_USER_ACL_CLASS);
    private final EntityVO roleEntityVO = SecurityCreatorUtils.getEntityVO(ID, ROLE_ACL_CLASS);
    private final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser(SIMPLE_USER);
    private final MetadataEntry pipelineUserEntry = MetadataCreatorUtils.getMetadataEntry(pipelineUserEntityVO);
    private final MetadataEntry roleEntry = MetadataCreatorUtils.getMetadataEntry(roleEntityVO);
    private final MetadataEntryWithIssuesCount pipelineUserEntryWithIssuesCount =
            MetadataCreatorUtils.getMetadataEntryWithIssuesCount(pipelineUserEntityVO);
    private final MetadataEntryWithIssuesCount roleEntryWithIssuesCount =
            MetadataCreatorUtils.getMetadataEntryWithIssuesCount(roleEntityVO);
    private final List<MetadataEntry> metadataEntries = new ArrayList<>(Collections.singletonList(metadataEntry));
    private final List<EntityVO> entityVOList = Collections.singletonList(entityVO);
    private final List<EntityVO> roleEntityVOList = Collections.singletonList(roleEntityVO);
    private final List<EntityVO> pipelineUserEntityVOList = Collections.singletonList(pipelineUserEntityVO);
    private final List<MetadataEntry> pipelineUserEntries = new ArrayList<>(
            Collections.singletonList(pipelineUserEntry));
    private final List<MetadataEntry> roleEntries = new ArrayList<>(Collections.singletonList(roleEntry));

    @Autowired
    private MetadataApiService metadataApiService;

    @Autowired
    private MetadataManager mockMetadataManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private UserManager mockUserManager;

    @Autowired
    private PreferenceManager mockPreferenceManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateMetadataItemKeyForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKey(metadataVO);

        assertThat(metadataApiService.updateMetadataItemKey(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataItemKeyForOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKey(metadataVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItemKey(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdatePipelineUserMetadataItemKeyForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).updateMetadataItemKey(pipelineUserVO);

        assertThat(metadataApiService.updateMetadataItemKey(pipelineUserVO)).isEqualTo(pipelineUserEntry);
    }


    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateMetadataItemKeyForPipelineUser() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).updateMetadataItemKey(pipelineUserVO);
        mockUser();

        assertThat(metadataApiService.updateMetadataItemKey(pipelineUserVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldUpdateRoleMetadataItemKeyForAdmin() {
        doReturn(roleEntry).when(mockMetadataManager).updateMetadataItemKey(roleVO);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItemKey(roleVO)).isEqualTo(roleEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataItemKeyForNonOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKey(metadataVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> metadataApiService.updateMetadataItemKey(metadataVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateMetadataItemKeysForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKeys(metadataVO);

        assertThat(metadataApiService.updateMetadataItemKeys(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataItemKeysForOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKeys(metadataVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItemKeys(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdatePipelineUserMetadataItemKeysForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).updateMetadataItemKeys(pipelineUserVO);

        assertThat(metadataApiService.updateMetadataItemKeys(pipelineUserVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateMetadataItemKeysForPipelineUser() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).updateMetadataItemKeys(pipelineUserVO);
        mockUser();

        assertThat(metadataApiService.updateMetadataItemKeys(pipelineUserVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldUpdateRoleMetadataItemKeysForAdmin() {
        doReturn(roleEntry).when(mockMetadataManager).updateMetadataItemKeys(roleVO);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItemKeys(roleVO)).isEqualTo(roleEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataItemKeysForNonOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKeys(metadataVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> metadataApiService.updateMetadataItemKeys(metadataVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateMetadataItemForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItem(metadataVO);

        assertThat(metadataApiService.updateMetadataItem(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataItemForOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItem(metadataVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItem(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdatePipelineUserMetadataItemForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).updateMetadataItem(pipelineUserVO);

        assertThat(metadataApiService.updateMetadataItem(pipelineUserVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = ANOTHER_SIMPLE_USER)
    public void shouldDenyUpdateUserMetadataItemForAnotherUser() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).updateMetadataItem(pipelineUserVO);
        mockUser();
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> metadataApiService.updateMetadataItem(pipelineUserVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateUserMetadataItemForSensitiveKeys() {
        final MetadataVO sensitiveMetadata = MetadataCreatorUtils.getMetadataVO(PIPELINE_USER_ACL_CLASS);
        sensitiveMetadata.setData(Collections.singletonMap(SENSITIVE_KEY, null));
        doReturn(Collections.singletonList(SENSITIVE_KEY))
                .when(mockPreferenceManager)
                .getPreference(eq(SystemPreferences.MISC_METADATA_SENSITIVE_KEYS));
        doReturn(pipelineUserEntry).when(mockMetadataManager).updateMetadataItem(sensitiveMetadata);
        mockUser();

        assertThrows(AccessDeniedException.class, () -> metadataApiService.updateMetadataItem(sensitiveMetadata));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateMetadataItemForPipelineUser() {
        final MetadataVO nonSensitiveMetadata = MetadataCreatorUtils.getMetadataVO(PIPELINE_USER_ACL_CLASS);
        nonSensitiveMetadata.setData(Collections.singletonMap(NON_SENSITIVE_KEY, null));
        doReturn(Collections.singletonList(SENSITIVE_KEY))
                .when(mockPreferenceManager)
                .getPreference(eq(SystemPreferences.MISC_METADATA_SENSITIVE_KEYS));

        doReturn(pipelineUserEntry).when(mockMetadataManager).updateMetadataItem(nonSensitiveMetadata);
        mockUser();

        assertThat(metadataApiService.updateMetadataItem(nonSensitiveMetadata)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldUpdateRoleMetadataItemForAdmin() {
        doReturn(roleEntry).when(mockMetadataManager).updateMetadataItem(roleVO);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItem(roleVO)).isEqualTo(roleEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataItemForNonOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItem(metadataVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> metadataApiService.updateMetadataItem(metadataVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldListMetadataItemsForAdmin() {
        doReturn(metadataEntries).when(mockMetadataManager).listMetadataItems(entityVOList);

        assertThat(metadataApiService.listMetadataItems(entityVOList)).isEqualTo(metadataEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldListMetadataItemsWhenPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        doReturn(metadataEntries).when(mockMetadataManager).listMetadataItems(entityVOList);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.listMetadataItems(entityVOList)).isEqualTo(metadataEntries);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldListPipelineUserMetadataItemsForAdmin() {
        doReturn(pipelineUserEntries).when(mockMetadataManager).listMetadataItems(pipelineUserEntityVOList);

        assertThat(metadataApiService.listMetadataItems(pipelineUserEntityVOList)).isEqualTo(pipelineUserEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldListMetadataItemsForPipelineUser() {
        doReturn(pipelineUserEntries).when(mockMetadataManager).listMetadataItems(pipelineUserEntityVOList);
        mockUser();

        assertThat(metadataApiService.listMetadataItems(pipelineUserEntityVOList)).isEqualTo(pipelineUserEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldListRoleMetadataItemsForAdmin() {
        doReturn(roleEntries).when(mockMetadataManager).listMetadataItems(roleEntityVOList);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.listMetadataItems(roleEntityVOList)).isEqualTo(roleEntries);
    }

    @Test
    @WithMockUser
    public void shouldDenyListMetadataItemsWhenPermissionIsNotGranted() {
        initAclEntity(entity);
        doReturn(metadataEntries).when(mockMetadataManager).listMetadataItems(entityVOList);
        mockLoadEntity(entity, ID);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> metadataApiService.listMetadataItems(entityVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteMetadataItemKeyForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKey(entityVO, TEST_STRING);

        assertThat(metadataApiService.deleteMetadataItemKey(entityVO, TEST_STRING)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataItemKeyForOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKey(entityVO, TEST_STRING);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItemKey(entityVO, TEST_STRING)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeletePipelineUserMetadataItemKeyForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItemKey(pipelineUserEntityVO, TEST_STRING);

        assertThat(metadataApiService.deleteMetadataItemKey(pipelineUserEntityVO, TEST_STRING))
                .isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteMetadataItemKeyForPipelineUser() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItemKey(pipelineUserEntityVO, TEST_STRING);
        mockUser();

        assertThat(metadataApiService.deleteMetadataItemKey(pipelineUserEntityVO, TEST_STRING))
                .isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldDeleteRoleMetadataItemKeyForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItemKey(pipelineUserEntityVO, TEST_STRING);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItemKey(pipelineUserEntityVO, TEST_STRING))
                .isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataItemKeyForNonOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKey(entityVO, TEST_STRING);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () ->
                metadataApiService.deleteMetadataItemKey(entityVO, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteMetadataItemForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItem(entityVO);

        assertThat(metadataApiService.deleteMetadataItem(entityVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataItemForOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItem(entityVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItem(entityVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeletePipelineUserMetadataItemForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItem(pipelineUserEntityVO);

        assertThat(metadataApiService.deleteMetadataItem(pipelineUserEntityVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteMetadataItemForPipelineUser() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItem(pipelineUserEntityVO);
        mockUser();

        assertThrows(AccessDeniedException.class, () -> metadataApiService.deleteMetadataItem(pipelineUserEntityVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldDeleteRoleMetadataItemForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItem(pipelineUserEntityVO);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItem(pipelineUserEntityVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataItemForNonOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItem(entityVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> metadataApiService.deleteMetadataItem(entityVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteMetadataItemKeysForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKeys(metadataVO);

        assertThat(metadataApiService.deleteMetadataItemKeys(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataItemKeysForOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKeys(metadataVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItemKeys(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeletePipelineUserMetadataItemKeysForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItemKeys(pipelineUserVO);

        assertThat(metadataApiService.deleteMetadataItemKeys(pipelineUserVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteMetadataItemKeysForPipelineUser() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItemKeys(pipelineUserVO);
        mockUser();

        assertThat(metadataApiService.deleteMetadataItemKeys(pipelineUserVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldDeleteRoleMetadataItemKeysForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).deleteMetadataItemKeys(pipelineUserVO);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItemKeys(pipelineUserVO)).isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataItemKeysForNonOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKeys(metadataVO);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> metadataApiService.deleteMetadataItemKeys(metadataVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFindMetadataEntityIdByNameForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).findMetadataEntryByNameOrId(TEST_STRING, ENTITY_ACL_CLASS);

        assertThat(metadataApiService.findMetadataEntityIdByName(TEST_STRING, ENTITY_ACL_CLASS))
                .isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFindMetadataEntityIdByNameWhenPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        doReturn(metadataEntry).when(mockMetadataManager).findMetadataEntryByNameOrId(TEST_STRING, ENTITY_ACL_CLASS);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.findMetadataEntityIdByName(TEST_STRING, ENTITY_ACL_CLASS))
                .isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFindPipelineUserMetadataEntityIdByNameForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager)
                .findMetadataEntryByNameOrId(TEST_STRING, PIPELINE_USER_ACL_CLASS);

        assertThat(metadataApiService.findMetadataEntityIdByName(TEST_STRING, PIPELINE_USER_ACL_CLASS))
                .isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFindMetadataEntityIdByNameForPipelineUser() {
        doReturn(pipelineUserEntry).when(mockMetadataManager)
                .findMetadataEntryByNameOrId(TEST_STRING, PIPELINE_USER_ACL_CLASS);
        mockUser();

        assertThat(metadataApiService.findMetadataEntityIdByName(TEST_STRING, PIPELINE_USER_ACL_CLASS))
                .isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldFindRoleMetadataEntityIdByNameForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager)
                .findMetadataEntryByNameOrId(TEST_STRING, PIPELINE_USER_ACL_CLASS);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.findMetadataEntityIdByName(TEST_STRING, PIPELINE_USER_ACL_CLASS))
                .isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyFindMetadataEntityIdByNameWhenPermissionIsNotGranted() {
        initAclEntity(entity);
        doReturn(metadataEntry).when(mockMetadataManager).findMetadataEntryByNameOrId(TEST_STRING, ENTITY_ACL_CLASS);
        mockSecurityContext();
        mockLoadEntity(entity, ID);

        assertThrows(AccessDeniedException.class, () ->
                metadataApiService.findMetadataEntityIdByName(TEST_STRING, ENTITY_ACL_CLASS));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUploadMetadataFromFileForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).uploadMetadataFromFile(entityVO, file, true);

        assertThat(metadataApiService.uploadMetadataFromFile(entityVO, file, true)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldUploadMetadataFromFileForOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).uploadMetadataFromFile(entityVO, file, true);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.uploadMetadataFromFile(entityVO, file, true)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUploadPipelineUserMetadataFromFileForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager).uploadMetadataFromFile(pipelineUserEntityVO, file, true);

        assertThat(metadataApiService.uploadMetadataFromFile(pipelineUserEntityVO, file, true))
                .isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUploadMetadataFromFileForPipelineUser() {
        doReturn(pipelineUserEntry).when(mockMetadataManager)
                .uploadMetadataFromFile(pipelineUserEntityVO, file, true);
        mockUser();

        assertThrows(AccessDeniedException.class, () ->
                metadataApiService.uploadMetadataFromFile(pipelineUserEntityVO, file, true));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldUploadRoleMetadataFromFileForAdmin() {
        doReturn(pipelineUserEntry).when(mockMetadataManager)
                .uploadMetadataFromFile(pipelineUserEntityVO, file, true);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.uploadMetadataFromFile(pipelineUserEntityVO, file, true))
                .isEqualTo(pipelineUserEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUploadMetadataFromFileForNonOwner() {
        doReturn(metadataEntry).when(mockMetadataManager).uploadMetadataFromFile(entityVO, file, true);
        mockLoadEntity(entityWithOwner, ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () ->
                metadataApiService.uploadMetadataFromFile(entityVO, file, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadEntitiesMetadataFromFolderForAdmin() {
        doReturn(mutableListOf(entry)).when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID)).hasSize(1).contains(entry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadEntitiesMetadataFromFolderWhenPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        doReturn(mutableListOf(entry)).when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID)).hasSize(1).contains(entry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadEntitiesMetadataFromFolderWhichPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        initAclEntity(anotherEntity);
        doReturn(mutableListOf(entry, anotherEntry)).when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);
        mockLoadEntity(entity, ID);
        mockLoadEntity(anotherEntity, ID_2);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID)).hasSize(1).contains(entry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadPipelineUserMetadataFromFolderForAdmin() {
        doReturn(mutableListOf(pipelineUserEntryWithIssuesCount))
                .when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID))
                .hasSize(1).contains(pipelineUserEntryWithIssuesCount);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadEntitiesMetadataFromFolderForPipelineUser() {
        doReturn(mutableListOf(pipelineUserEntryWithIssuesCount))
                .when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);
        mockUser();

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID)).hasSize(1)
                .contains(pipelineUserEntryWithIssuesCount);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldLoadEntitiesMetadataFromFolderForRole() {
        doReturn(mutableListOf(roleEntryWithIssuesCount))
                .when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID)).hasSize(1)
                .contains(roleEntryWithIssuesCount);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadEntitiesMetadataFromFolderWhenPermissionIsNotGranted() {
        initAclEntity(anotherEntity);
        doReturn(mutableListOf(anotherEntry)).when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);
        mockLoadEntity(anotherEntity, ID_2);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID)).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldSearchMetadataByClassAndKeyValueForAdmin() {
        doReturn(mutableListOf(entityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING);

        assertThat(metadataApiService.searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING))
                .hasSize(1).contains(entityVO);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldSearchMetadataByClassAndKeyValueWhenPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        doReturn(mutableListOf(entityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING))
                .hasSize(1).contains(entityVO);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldSearchMetadataByClassAndKeyValueWhichPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        initAclEntity(anotherEntity);
        doReturn(mutableListOf(entityVO, anotherEntityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING);
        mockLoadEntity(entity, ID);
        mockLoadEntity(anotherEntity, ID_2);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING))
                .hasSize(1).contains(entityVO);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldSearchPipelineUserMetadataByClassAndKeyValueForAdmin() {
        doReturn(mutableListOf(pipelineUserEntityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(PIPELINE_USER_ACL_CLASS, TEST_STRING, TEST_STRING);

        assertThat(metadataApiService
                .searchMetadataByClassAndKeyValue(PIPELINE_USER_ACL_CLASS, TEST_STRING, TEST_STRING))
                .hasSize(1).contains(pipelineUserEntityVO);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldSearchMetadataByClassAndKeyValueForPipelineUser() {
        doReturn(mutableListOf(pipelineUserEntityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(PIPELINE_USER_ACL_CLASS, TEST_STRING, TEST_STRING);
        mockUser();

        assertThat(metadataApiService
                .searchMetadataByClassAndKeyValue(PIPELINE_USER_ACL_CLASS, TEST_STRING, TEST_STRING)).hasSize(1)
                .contains(pipelineUserEntityVO);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldSearchMetadataByClassAndKeyValueForRole() {
        doReturn(mutableListOf(roleEntityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(ROLE_ACL_CLASS, TEST_STRING, TEST_STRING);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.searchMetadataByClassAndKeyValue(ROLE_ACL_CLASS, TEST_STRING, TEST_STRING))
                .hasSize(1).contains(roleEntityVO);
    }

    @Test
    @WithMockUser
    public void shouldDenySearchMetadataByClassAndKeyValueWhenPermissionIsNotGranted() {
        initAclEntity(anotherEntity);
        doReturn(mutableListOf(anotherEntityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING);
        mockLoadEntity(anotherEntity, ID_2);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING))
                .isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetMetadataKeysForAdmin() {
        doReturn(TEST_STRING_SET).when(mockMetadataManager).getMetadataKeys(ENTITY_ACL_CLASS);

        assertThat(metadataApiService.getMetadataKeys(ENTITY_ACL_CLASS)).isEqualTo(TEST_STRING_SET);
    }

    @Test
    @WithMockUser
    public void shouldGetMetadataKeysForUser() {
        doReturn(TEST_STRING_SET).when(mockMetadataManager).getMetadataKeys(ENTITY_ACL_CLASS);

        assertThat(metadataApiService.getMetadataKeys(ENTITY_ACL_CLASS)).isEqualTo(TEST_STRING_SET);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyGetMetadataKeysWithoutUserRole() {
        doReturn(TEST_STRING_SET).when(mockMetadataManager).getMetadataKeys(ENTITY_ACL_CLASS);

        assertThrows(AccessDeniedException.class, () -> metadataApiService.getMetadataKeys(ENTITY_ACL_CLASS));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenySyncWithMetadataForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> metadataApiService.syncWithCategoricalAttributes());
    }

    private void mockLoadEntity(final AbstractSecuredEntity entity, final Long id) {
        doReturn(entity).when(mockEntityManager).load(ENTITY_ACL_CLASS, id);
    }

    private void mockUser() {
        mockAuthUser(SIMPLE_USER);
        doReturn(pipelineUser).when(mockUserManager).loadUserById(ID);
    }
}

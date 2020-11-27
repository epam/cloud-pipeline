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

package com.epam.pipeline.acl.metadata;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_SET;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class MetadataApiServiceTest extends AbstractAclTest {

    private static final AclClass ENTITY_ACL_CLASS = AclClass.DATA_STORAGE;

    private final MetadataVO metadataVO = MetadataCreatorUtils.getMetadataVO(ENTITY_ACL_CLASS);
    private final AbstractSecuredEntity entity = DatastorageCreatorUtils.getS3bucketDataStorage(ID, SIMPLE_USER);
    private final AbstractSecuredEntity anotherEntity =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID_2, ANOTHER_SIMPLE_USER);
    private final EntityVO entityVO = SecurityCreatorUtils.getEntityVO(ID, ENTITY_ACL_CLASS);
    private final EntityVO anotherEntityVO = SecurityCreatorUtils.getEntityVO(ID_2, ENTITY_ACL_CLASS);
    private final MetadataEntry metadataEntry = MetadataCreatorUtils.getMetadataEntry(entityVO);
    private final MultipartFile file = new MockMultipartFile(TEST_STRING, TEST_STRING.getBytes());
    private final MetadataEntryWithIssuesCount entry = MetadataCreatorUtils.getMetadataEntryWithIssuesCount(entityVO);
    private final MetadataEntryWithIssuesCount anotherEntry =
            MetadataCreatorUtils.getMetadataEntryWithIssuesCount(anotherEntityVO);

    private final List<MetadataEntry> metadataEntries = Collections.singletonList(metadataEntry);
    private final List<EntityVO> entityVOList = Collections.singletonList(entityVO);

    @Autowired
    private MetadataApiService metadataApiService;

    @Autowired
    private MetadataManager mockMetadataManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateMetadataItemKeyForAdmin() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKey(metadataVO);

        assertThat(metadataApiService.updateMetadataItemKey(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataItemKeyWhenPermissionIsGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKey(metadataVO);
        doReturn(entity).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItemKey(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataItemKeyWhenPermissionIsNotGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKey(metadataVO);
        mockLoadEntity(entity, ID);

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
    public void shouldUpdateMetadataItemKeysWhenPermissionIsGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKeys(metadataVO);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItemKeys(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataItemKeysWhenPermissionIsNotGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItemKeys(metadataVO);
        mockLoadEntity(entity, ID);

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
    public void shouldUpdateMetadataItemWhenPermissionIsGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItem(metadataVO);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.updateMetadataItem(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataItemWhenPermissionIsNotGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).updateMetadataItem(metadataVO);
        mockLoadEntity(entity, ID);

        assertThrows(AccessDeniedException.class, () -> metadataApiService.updateMetadataItem(metadataVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldListMetadataItemsForAdmin() {
        doReturn(metadataEntries).when(mockMetadataManager).listMetadataItems(entityVOList);

        assertThat(metadataApiService.listMetadataItems(entityVOList)).isEqualTo(metadataEntries);
    }

    @Test
    @WithMockUser
    public void shouldListMetadataItemsWhenPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        doReturn(metadataEntries).when(mockMetadataManager).listMetadataItems(entityVOList);
        mockLoadEntity(entity, ID);
        mockAuthentication(SIMPLE_USER);

        assertThat(metadataApiService.listMetadataItems(entityVOList)).isEqualTo(metadataEntries);
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
    public void shouldDeleteMetadataItemKeyWhenPermissionIsGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKey(entityVO, TEST_STRING);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItemKey(entityVO, TEST_STRING)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataItemKeyWhenPermissionIsNotGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKey(entityVO, TEST_STRING);
        mockLoadEntity(entity, ID);

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
    public void shouldDeleteMetadataItemWhenPermissionIsGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItem(entityVO);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItem(entityVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataItemWhenPermissionIsNotGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItem(entityVO);
        mockLoadEntity(entity, ID);

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
    public void shouldDeleteMetadataItemKeysWhenPermissionIsGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKeys(metadataVO);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.deleteMetadataItemKeys(metadataVO)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataItemKeysWhenPermissionIsNotGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).deleteMetadataItemKeys(metadataVO);
        mockLoadEntity(entity, ID);

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
    @WithMockUser
    public void shouldFindMetadataEntityIdByNameWhenPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        doReturn(metadataEntry).when(mockMetadataManager).findMetadataEntryByNameOrId(TEST_STRING, ENTITY_ACL_CLASS);
        mockLoadEntity(entity, ID);
        mockAuthentication(SIMPLE_USER);

        assertThat(metadataApiService.findMetadataEntityIdByName(TEST_STRING, ENTITY_ACL_CLASS))
                .isEqualTo(metadataEntry);
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
    public void shouldUploadMetadataFromFileWhenPermissionIsGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).uploadMetadataFromFile(entityVO, file, true);
        mockLoadEntity(entity, ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataApiService.uploadMetadataFromFile(entityVO, file, true)).isEqualTo(metadataEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUploadMetadataFromFileWhenPermissionIsNotGranted() {
        doReturn(metadataEntry).when(mockMetadataManager).uploadMetadataFromFile(entityVO, file, true);
        mockLoadEntity(entity, ID);

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
    @WithMockUser
    public void shouldLoadEntitiesMetadataFromFolderWhenPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        doReturn(mutableListOf(entry)).when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);
        mockLoadEntity(entity, ID);
        mockAuthentication(SIMPLE_USER);

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID)).hasSize(1).contains(entry);
    }

    @Test
    @WithMockUser
    public void shouldLoadEntitiesMetadataFromFolderWhichPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        initAclEntity(anotherEntity);
        doReturn(mutableListOf(entry, anotherEntry)).when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);
        mockLoadEntity(entity, ID);
        mockLoadEntity(anotherEntity, ID_2);
        mockAuthentication(SIMPLE_USER);

        assertThat(metadataApiService.loadEntitiesMetadataFromFolder(ID)).hasSize(1).contains(entry);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadEntitiesMetadataFromFolderWhenPermissionIsNotGranted() {
        initAclEntity(anotherEntity);
        doReturn(mutableListOf(anotherEntry)).when(mockMetadataManager).loadEntitiesMetadataFromFolder(ID);
        mockLoadEntity(anotherEntity, ID_2);
        mockAuthentication(SIMPLE_USER);

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
    @WithMockUser
    public void shouldSearchMetadataByClassAndKeyValueWhenPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        doReturn(mutableListOf(entityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING);
        mockLoadEntity(entity, ID);
        mockAuthentication(SIMPLE_USER);

        assertThat(metadataApiService.searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING))
                .hasSize(1).contains(entityVO);
    }

    @Test
    @WithMockUser
    public void shouldSearchMetadataByClassAndKeyValueWhichPermissionIsGranted() {
        initAclEntity(entity, AclPermission.READ);
        initAclEntity(anotherEntity);
        doReturn(mutableListOf(entityVO, anotherEntityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING);
        mockLoadEntity(entity, ID);
        mockLoadEntity(anotherEntity, ID_2);
        mockAuthentication(SIMPLE_USER);

        assertThat(metadataApiService.searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING))
                .hasSize(1).contains(entityVO);
    }

    @Test
    @WithMockUser
    public void shouldDenySearchMetadataByClassAndKeyValueWhenPermissionIsNotGranted() {
        initAclEntity(anotherEntity);
        doReturn(mutableListOf(anotherEntityVO)).when(mockMetadataManager)
                .searchMetadataByClassAndKeyValue(ENTITY_ACL_CLASS, TEST_STRING, TEST_STRING);
        mockLoadEntity(anotherEntity, ID_2);
        mockAuthentication(SIMPLE_USER);

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

    private void mockLoadEntity(final AbstractSecuredEntity entity, final Long id) {
        doReturn(entity).when(mockEntityManager).load(ENTITY_ACL_CLASS, id);
    }
}

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

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.metadata.MetadataDownloadManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.MetadataUploadManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.folder.FolderCreatorUtils;
import com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG_SET;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_MAP;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class MetadataEntityApiServiceTest extends AbstractAclTest {

    private final MetadataClass metadataClass = MetadataCreatorUtils.getMetadataClass();
    private final MetadataEntityVO metadataEntityVO = MetadataCreatorUtils.getMetadataEntityVO(ID);
    private final Folder folder = FolderCreatorUtils.getFolder(ID, SIMPLE_USER);
    private final MetadataEntity metadataEntity = MetadataCreatorUtils.getMetadataEntity(ID, folder);
    private final PagedResult<List<MetadataEntity>> pagedResult = MetadataCreatorUtils.getPagedResult();
    private final MetadataFilter metadataFilter = MetadataCreatorUtils.getMetadataFilter(ID);
    private final MetadataField metadataField = MetadataCreatorUtils.getMetadataField();
    private final MultipartFile file = new MockMultipartFile(TEST_STRING, TEST_STRING.getBytes());
    private final InputStream inputStream = new ByteArrayInputStream(TEST_STRING.getBytes());

    private final List<MetadataEntity> metadataEntities = Collections.singletonList(metadataEntity);
    private final List<MetadataField> metadataFields = Collections.singletonList(metadataField);
    private final List<MetadataClassDescription> descriptions =
            Collections.singletonList(MetadataCreatorUtils.getMetadataClassDescription());

    @Autowired
    private MetadataEntityApiService entityApiService;

    @Autowired
    private MetadataEntityManager mockMetadataEntityManager;

    @Autowired
    private MetadataUploadManager mockMetadataUploadManager;

    @Autowired
    private MetadataDownloadManager mockMetadataDownloadManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateMetadataClassForAdmin() {
        doReturn(metadataClass).when(mockMetadataEntityManager).createMetadataClass(TEST_STRING);

        assertThat(entityApiService.createMetadataClass(TEST_STRING)).isEqualTo(metadataClass);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateMetadataClassForNotAdmin() {
        doReturn(metadataClass).when(mockMetadataEntityManager).createMetadataClass(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> entityApiService.createMetadataClass(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteMetadataClassForAdmin() {
        doReturn(metadataClass).when(mockMetadataEntityManager).deleteMetadataClass(ID);

        assertThat(entityApiService.deleteMetadataClass(ID)).isEqualTo(metadataClass);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataClassForNotAdmin() {
        doReturn(metadataClass).when(mockMetadataEntityManager).deleteMetadataClass(ID);

        assertThrows(AccessDeniedException.class, () -> entityApiService.deleteMetadataClass(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateMetadataClassForAdmin() {
        doReturn(metadataClass).when(mockMetadataEntityManager).updateExternalClassName(ID, FireCloudClass.PARTICIPANT);

        assertThat(entityApiService.updateExternalClassName(ID, FireCloudClass.PARTICIPANT)).isEqualTo(metadataClass);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataClassForNotAdmin() {
        doReturn(metadataClass).when(mockMetadataEntityManager).updateExternalClassName(ID, FireCloudClass.PARTICIPANT);

        assertThrows(AccessDeniedException.class, () ->
                entityApiService.updateExternalClassName(ID, FireCloudClass.PARTICIPANT));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateMetadataEntityForAdmin() {
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataEntity(metadataEntityVO);

        assertThat(entityApiService.updateMetadataEntity(metadataEntityVO)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateMetadataEntityWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataEntity(metadataEntityVO);

        assertThat(entityApiService.updateMetadataEntity(metadataEntityVO)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataEntityWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataEntity(metadataEntityVO);

        assertThrows(AccessDeniedException.class, () -> entityApiService.updateMetadataEntity(metadataEntityVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateMetadataEntityForAdmin() {
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataEntity(metadataEntityVO);

        assertThat(entityApiService.createMetadataEntity(metadataEntityVO)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ENTITIES_MANAGER_ROLE)
    public void shouldCreateMetadataEntityWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataEntity(metadataEntityVO);

        assertThat(entityApiService.createMetadataEntity(metadataEntityVO)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateMetadataEntityWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataEntity(metadataEntityVO);

        assertThrows(AccessDeniedException.class, () -> entityApiService.updateMetadataEntity(metadataEntityVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadMetadataEntityForAdmin() {
        doReturn(metadataEntity).when(mockMetadataEntityManager).load(ID);

        assertThat(entityApiService.loadMetadataEntity(ID)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser
    public void shouldLoadMetadataEntityWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        mockLoadEntities();
        mockAuthentication(SIMPLE_USER);

        assertThat(entityApiService.loadMetadataEntity(ID)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadMetadataEntityWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        mockLoadEntities();
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> entityApiService.loadMetadataEntity(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteMetadataEntityForAdmin() {
        doReturn(metadataEntity).when(mockMetadataEntityManager).deleteMetadataEntity(ID);

        assertThat(entityApiService.deleteMetadataEntity(ID)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ENTITIES_MANAGER_ROLE)
    public void shouldDeleteMetadataEntityWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(metadataEntity).when(mockMetadataEntityManager).deleteMetadataEntity(ID);
        mockLoadEntities();
        mockAuthentication(SIMPLE_USER);

        assertThat(entityApiService.deleteMetadataEntity(ID)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataEntityWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataEntity).when(mockMetadataEntityManager).deleteMetadataEntity(ID);
        mockLoadEntities();
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> entityApiService.deleteMetadataEntity(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadMetadataEntityByClassForAdmin() {
        doReturn(metadataEntities).when(mockMetadataEntityManager)
                .loadMetadataEntityByClassNameAndFolderId(ID, TEST_STRING);

        assertThat(entityApiService.loadMetadataEntityByClass(ID, TEST_STRING)).isEqualTo(metadataEntities);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadMetadataEntityByClassWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        doReturn(metadataEntities).when(mockMetadataEntityManager)
                .loadMetadataEntityByClassNameAndFolderId(ID, TEST_STRING);

        assertThat(entityApiService.loadMetadataEntityByClass(ID, TEST_STRING)).isEqualTo(metadataEntities);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadMetadataEntityByClassWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataEntities).when(mockMetadataEntityManager)
                .loadMetadataEntityByClassNameAndFolderId(ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> entityApiService.loadMetadataEntityByClass(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateMetadataItemKeyForAdmin() {
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataItemKey(metadataEntityVO);

        assertThat(entityApiService.updateMetadataItemKey(metadataEntityVO)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateMetadataItemKeyWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataItemKey(metadataEntityVO);

        assertThat(entityApiService.updateMetadataItemKey(metadataEntityVO)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateMetadataItemKeyWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataEntity).when(mockMetadataEntityManager).updateMetadataItemKey(metadataEntityVO);

        assertThrows(AccessDeniedException.class, () -> entityApiService.updateMetadataItemKey(metadataEntityVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteMetadataItemKeyForAdmin() {
        doReturn(metadataEntity).when(mockMetadataEntityManager).deleteMetadataItemKey(ID, TEST_STRING);

        assertThat(entityApiService.deleteMetadataItemKey(ID, TEST_STRING)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteMetadataItemKeyWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(metadataEntity).when(mockMetadataEntityManager).deleteMetadataItemKey(ID, TEST_STRING);
        mockLoadEntities();
        mockAuthentication(SIMPLE_USER);

        assertThat(entityApiService.deleteMetadataItemKey(ID, TEST_STRING)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataItemKeyWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataEntity).when(mockMetadataEntityManager).deleteMetadataItemKey(ID, TEST_STRING);
        mockLoadEntities();
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> entityApiService.deleteMetadataItemKey(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteMetadataEntitiesForAdmin() {
        doReturn(TEST_LONG_SET).when(mockMetadataEntityManager).deleteMetadataEntities(TEST_LONG_SET);

        assertThat(entityApiService.deleteMetadataEntities(TEST_LONG_SET)).isEqualTo(TEST_LONG_SET);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ENTITIES_MANAGER_ROLE)
    public void shouldDeleteMetadataEntitiesWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(TEST_LONG_SET).when(mockMetadataEntityManager).deleteMetadataEntities(TEST_LONG_SET);
        mockLoadEntities();
        mockAuthentication(SIMPLE_USER);

        assertThat(entityApiService.deleteMetadataEntities(TEST_LONG_SET)).isEqualTo(TEST_LONG_SET);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataEntitiesWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(TEST_LONG_SET).when(mockMetadataEntityManager).deleteMetadataEntities(TEST_LONG_SET);
        mockLoadEntities();
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> entityApiService.deleteMetadataEntities(TEST_LONG_SET));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFilterMetadataForAdmin() {
        doReturn(pagedResult).when(mockMetadataEntityManager).filterMetadata(metadataFilter);

        assertThat(entityApiService.filterMetadata(metadataFilter)).isEqualTo(pagedResult);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFilterMetadataWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(pagedResult).when(mockMetadataEntityManager).filterMetadata(metadataFilter);

        assertThat(entityApiService.filterMetadata(metadataFilter)).isEqualTo(pagedResult);
    }

    @Test
    @WithMockUser
    public void shouldDenyFilterMetadataWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(pagedResult).when(mockMetadataEntityManager).filterMetadata(metadataFilter);

        assertThrows(AccessDeniedException.class, () -> entityApiService.filterMetadata(metadataFilter));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetMetadataKeysForAdmin() {
        doReturn(metadataFields).when(mockMetadataEntityManager).getMetadataKeys(ID, TEST_STRING);

        assertThat(entityApiService.getMetadataKeys(ID, TEST_STRING)).isEqualTo(metadataFields);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetMetadataKeysWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        doReturn(metadataFields).when(mockMetadataEntityManager).getMetadataKeys(ID, TEST_STRING);

        assertThat(entityApiService.getMetadataKeys(ID, TEST_STRING)).isEqualTo(metadataFields);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetMetadataKeysWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataFields).when(mockMetadataEntityManager).getMetadataKeys(ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> entityApiService.getMetadataKeys(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetMetadataFieldsForAdmin() {
        doReturn(descriptions).when(mockMetadataEntityManager).getMetadataFields(ID);

        assertThat(entityApiService.getMetadataFields(ID)).isEqualTo(descriptions);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetMetadataFieldsWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        doReturn(descriptions).when(mockMetadataEntityManager).getMetadataFields(ID);

        assertThat(entityApiService.getMetadataFields(ID)).isEqualTo(descriptions);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetMetadataFieldsWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(descriptions).when(mockMetadataEntityManager).getMetadataFields(ID);

        assertThrows(AccessDeniedException.class, () -> entityApiService.getMetadataFields(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUploadMetadataFromFileForAdmin() {
        doReturn(metadataEntities).when(mockMetadataUploadManager).uploadFromFile(ID, file);

        assertThat(entityApiService.uploadMetadataFromFile(ID, file)).isEqualTo(metadataEntities);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ENTITIES_MANAGER_ROLE)
    public void shouldUploadMetadataFromFileWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        doReturn(metadataEntities).when(mockMetadataUploadManager).uploadFromFile(ID, file);

        assertThat(entityApiService.uploadMetadataFromFile(ID, file)).isEqualTo(metadataEntities);
    }

    @Test
    @WithMockUser
    public void shouldDenyUploadMetadataFromFileWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataEntities).when(mockMetadataUploadManager).uploadFromFile(ID, file);

        assertThrows(AccessDeniedException.class, () -> entityApiService.uploadMetadataFromFile(ID, file));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadByExternalIdForAdmin() {
        doReturn(metadataEntity).when(mockMetadataEntityManager).loadByExternalId(TEST_STRING, TEST_STRING, ID);

        assertThat(entityApiService.loadByExternalId(TEST_STRING, TEST_STRING, ID)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadByExternalIdWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        doReturn(metadataEntity).when(mockMetadataEntityManager).loadByExternalId(TEST_STRING, TEST_STRING, ID);

        assertThat(entityApiService.loadByExternalId(TEST_STRING, TEST_STRING, ID)).isEqualTo(metadataEntity);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadByExternalIdWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(metadataEntity).when(mockMetadataEntityManager).loadByExternalId(TEST_STRING, TEST_STRING, ID);

        assertThrows(AccessDeniedException.class, () ->
                entityApiService.loadByExternalId(TEST_STRING, TEST_STRING, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteMetadataFromProjectForAdmin() {
        doNothing().when(mockMetadataEntityManager).deleteMetadataEntitiesInProject(ID, TEST_STRING);

        entityApiService.deleteMetadataFromProject(ID, TEST_STRING);

        verify(mockMetadataEntityManager).deleteMetadataEntitiesInProject(ID, TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteMetadataFromProjectWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        doNothing().when(mockMetadataEntityManager).deleteMetadataEntitiesInProject(ID, TEST_STRING);

        entityApiService.deleteMetadataFromProject(ID, TEST_STRING);

        verify(mockMetadataEntityManager).deleteMetadataEntitiesInProject(ID, TEST_STRING);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteMetadataFromProjectWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doNothing().when(mockMetadataEntityManager).deleteMetadataEntitiesInProject(ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> entityApiService.deleteMetadataFromProject(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadEntitiesDataForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockMetadataEntityManager).loadEntitiesData(TEST_LONG_SET);

        assertThat(entityApiService.loadEntitiesData(TEST_LONG_SET)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadEntitiesDataWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        doReturn(TEST_STRING_MAP).when(mockMetadataEntityManager).loadEntitiesData(TEST_LONG_SET);
        mockLoadEntities();
        mockAuthentication(SIMPLE_USER);

        assertThat(entityApiService.loadEntitiesData(TEST_LONG_SET)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadEntitiesDataWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(TEST_STRING_MAP).when(mockMetadataEntityManager).loadEntitiesData(TEST_LONG_SET);
        mockLoadEntities();
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> entityApiService.loadEntitiesData(TEST_LONG_SET));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetMetadataEntityFileForAdmin() {
        doReturn(inputStream).when(mockMetadataDownloadManager).getInputStream(ID, TEST_STRING, TEST_STRING);

        assertThat(entityApiService.getMetadataEntityFile(ID, TEST_STRING, TEST_STRING)).isEqualTo(inputStream);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ENTITIES_MANAGER_ROLE)
    public void shouldGetMetadataEntityFileWhenPermissionIsGranted() {
        initAclEntity(folder, AclPermission.READ);
        doReturn(inputStream).when(mockMetadataDownloadManager).getInputStream(ID, TEST_STRING, TEST_STRING);

        assertThat(entityApiService.getMetadataEntityFile(ID, TEST_STRING, TEST_STRING)).isEqualTo(inputStream);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetMetadataEntityFileWhenPermissionIsNotGranted() {
        initAclEntity(folder);
        doReturn(inputStream).when(mockMetadataDownloadManager).getInputStream(ID, TEST_STRING, TEST_STRING);

        assertThrows(AccessDeniedException.class, () ->
                entityApiService.getMetadataEntityFile(ID, TEST_STRING, TEST_STRING));
    }

    private void mockLoadEntities() {
        doReturn(metadataEntity).when(mockMetadataEntityManager).load(ID);
        doReturn(folder).when(mockEntityManager).load(AclClass.FOLDER, ID);
    }
}

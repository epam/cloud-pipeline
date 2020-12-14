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

package com.epam.pipeline.acl.folder;

import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ALL_PERMISSIONS;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.READ_PERMISSION;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getRunConfiguration;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolder;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolderWithMetadata;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipeline;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class FolderApiServiceTest extends AbstractAclTest {

    private static final long ID_4 = 4L;
    private static final long ID_5 = 5L;
    private static final long ID_6 = 6L;
    private static final long ID_7 = 7L;
    private static final long ID_8 = 8L;
    private static final long ID_9 = 9L;
    private static final long ID_10 = 10L;
    private static final long ID_11 = 11L;
    private static final long ID_12 = 12L;
    private static final long ID_100 = 100L;
    private static final long ID_110 = 110L;
    private static final String FOLDER_MANAGER = "FOLDER_MANAGER";
    private final Folder folder = getFolder(ID, ID_100, ANOTHER_SIMPLE_USER);
    final Folder folder10 = getFolder(ID_100, ID_110, ANOTHER_SIMPLE_USER);
    private final FolderWithMetadata folderWithMetadata = getFolderWithMetadata(ID, ID_110, ANOTHER_SIMPLE_USER);
    private final Folder emptyChildFolderWithoutPermission = getFolder(ID_3, ID, ANOTHER_SIMPLE_USER);
    private final Pipeline pipelineRead1 = getPipeline(ID, ANOTHER_SIMPLE_USER);
    private final Pipeline pipelineRead2 = getPipeline(ID_2, ANOTHER_SIMPLE_USER);
    private final Pipeline pipelineWithoutPermission1 = getPipeline(ID_3, ANOTHER_SIMPLE_USER);
    private final Pipeline pipelineWithoutPermission2 = getPipeline(ID_4, ANOTHER_SIMPLE_USER);
    private final S3bucketDataStorage storageRead1 = getS3bucketDataStorage(ID_5, ANOTHER_SIMPLE_USER);
    private final S3bucketDataStorage storageRead2 = getS3bucketDataStorage(ID_6, ANOTHER_SIMPLE_USER);
    private final S3bucketDataStorage storageWithoutPermission1 = getS3bucketDataStorage(ID_7, ANOTHER_SIMPLE_USER);
    private final S3bucketDataStorage storageWithoutPermission2 = getS3bucketDataStorage(ID_8, ANOTHER_SIMPLE_USER);
    private final RunConfiguration runConfigRead1 = getRunConfiguration(ID_9, ANOTHER_SIMPLE_USER);
    private final RunConfiguration runConfigRead2 = getRunConfiguration(ID_10, ANOTHER_SIMPLE_USER);
    private final RunConfiguration runConfigWithoutPermission1 = getRunConfiguration(ID_11, ANOTHER_SIMPLE_USER);
    private final RunConfiguration runConfigWithoutPermission2 = getRunConfiguration(ID_12, ANOTHER_SIMPLE_USER);
    private final List<AbstractSecuredEntity> entitiesWithPermission = Arrays.asList(pipelineRead2,
                                                                                     storageRead2,
                                                                                     runConfigRead2);
    private final List<AbstractSecuredEntity> allEntities = Arrays.asList(pipelineRead1, pipelineWithoutPermission1,
                                                                          storageRead1, storageWithoutPermission1,
                                                                          runConfigRead1, runConfigWithoutPermission1);
    private final List<AbstractSecuredEntity> allEntities2 = Arrays.asList(pipelineRead2, pipelineWithoutPermission2,
                                                                           storageRead2, storageWithoutPermission2,
                                                                           runConfigRead2, runConfigWithoutPermission2);

    @Autowired
    private FolderManager mockFolderManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private FolderApiService folderApiService;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateForAdmin() {
        doReturn(folder).when(mockFolderManager).create(folder);

        assertThat(folderApiService.create(folder)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldCreateForManagerWhenPermissionIsGranted() {
        doReturn(folder).when(mockFolderManager).create(folder);
        initAclEntity(folder10, AclPermission.WRITE);
        initAclEntity(folder, AclPermission.WRITE);

        assertThat(folderApiService.create(folder)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateWithInvalidRole() {
        assertThrows(AccessDeniedException.class, () -> folderApiService.create(folder));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCreateWhenPermissionIsNotGranted() {
        initAclEntity(folder10);
        initAclEntity(folder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.create(folder));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCreateWhenParentIdIsNull() {
        final Folder folder = getFolder(ID, null, ANOTHER_SIMPLE_USER);
        initAclEntity(folder, AclPermission.WRITE);

        assertThrows(AccessDeniedException.class, () -> folderApiService.create(folder));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateFromTemplateForAdmin() {
        doReturn(folder).when(mockFolderManager).createFromTemplate(folder, TEST_STRING);

        assertThat(folderApiService.createFromTemplate(folder, TEST_STRING)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldCreateFromTemplateForManagerWhenPermissionIsGranted() {
        doReturn(folder).when(mockFolderManager).createFromTemplate(folder, TEST_STRING);
        initAclEntity(folder10, AclPermission.WRITE);
        initAclEntity(folder, AclPermission.WRITE);

        assertThat(folderApiService.createFromTemplate(folder, TEST_STRING)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateFromTemplateWithInvalidRole() {
        assertThrows(AccessDeniedException.class, () -> folderApiService.createFromTemplate(folder, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCreateFromTemplateWhenPermissionIsNotGranted() {
        initAclEntity(folder10);
        initAclEntity(folder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.createFromTemplate(folder, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCreateFromTemplateWhenParentIdIsNull() {
        final Folder folder = getFolder(ID, null, ANOTHER_SIMPLE_USER);
        initAclEntity(folder, AclPermission.WRITE);

        assertThrows(AccessDeniedException.class, () -> folderApiService.createFromTemplate(folder, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateForAdmin() {
        doReturn(folder).when(mockFolderManager).update(folder);

        assertThat(folderApiService.update(folder)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateWhenPermissionIsGranted() {
        doReturn(folder).when(mockFolderManager).update(folder);
        initAclEntity(folder, AclPermission.WRITE);

        assertThat(folderApiService.update(folder)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateWhenPermissionIsNotGranted() {
        initAclEntity(folder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.update(folder));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetProjectForAdmin() {
        doReturn(folderWithMetadata).when(mockFolderManager).getProject(ID, AclClass.FOLDER);

        assertThat(folderApiService.getProject(ID, AclClass.FOLDER)).isEqualTo(folderWithMetadata);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetProjectWhenPermissionIsGranted() {
        doReturn(folderWithMetadata).when(mockEntityManager).load(AclClass.FOLDER, ID);
        doReturn(folderWithMetadata).when(mockFolderManager).getProject(ID, AclClass.FOLDER);
        initAclEntity(folderWithMetadata, AclPermission.READ);
        mockSecurityContext();

        assertThat(folderApiService.getProject(ID, AclClass.FOLDER)).isEqualTo(folderWithMetadata);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetProjectWhenPermissionIsNotGranted() {
        doReturn(folderWithMetadata).when(mockEntityManager).load(AclClass.FOLDER, ID);
        doReturn(folderWithMetadata).when(mockFolderManager).getProject(ID, AclClass.FOLDER);
        initAclEntity(folderWithMetadata);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> folderApiService.getProject(ID, AclClass.FOLDER));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetProjectWithHierarchyWhenPermissionIsGranted() {
        final FolderWithMetadata childFolderWithPermission = initFolderWithReadPermission();
        final FolderWithMetadata childFolderWithoutPermission = initFolderWithoutReadPermission();
        final List<Folder> folders = Arrays.asList(childFolderWithPermission,
                childFolderWithoutPermission,
                emptyChildFolderWithoutPermission);
        final FolderWithMetadata parentFolder = initParentFolder(folders);
        doReturn(parentFolder).when(mockEntityManager).load(AclClass.FOLDER, ID);
        doReturn(parentFolder).when(mockFolderManager).getProject(ID, AclClass.FOLDER);
        initAclEntity(emptyChildFolderWithoutPermission);
        initAclEntity(parentFolder, AclPermission.READ);
        initEntities();
        mockSecurityContext();

        final FolderWithMetadata returnedFolder = folderApiService.getProject(ID, AclClass.FOLDER);
        final Map<Long, AbstractHierarchicalEntity> childrenById = returnedFolder.getChildren().stream()
                .collect(Collectors.toMap(AbstractSecuredEntity::getId, Function.identity()));
        final List<? extends AbstractSecuredEntity> withoutPermissionLeaves =
                childrenById.get(childFolderWithoutPermission.getId()).getLeaves();

        assertThat(returnedFolder).isEqualTo(parentFolder);
        assertTreeForFolderWithReadPermission(childrenById.get(childFolderWithPermission.getId()));
        assertThat(withoutPermissionLeaves).isEqualTo(allEntities2);
        assertAclMaskForLeaves(withoutPermissionLeaves);
        assertThat(childrenById.get(emptyChildFolderWithoutPermission.getId()))
                .isEqualTo(emptyChildFolderWithoutPermission);
    }

    @Test
    @WithMockUser
    public void shouldLoadTree() {
        doReturn(folder).when(mockFolderManager).loadTree();

        assertThat(folderApiService.loadTree()).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadTreeWithHierarchy() {
        final Folder childFolderWithPermission = initFolderWithReadPermission();
        final Folder childFolderWithoutPermission = initFolderWithoutReadPermission();
        final List<Folder> folders = Arrays.asList(childFolderWithPermission, childFolderWithoutPermission,
                emptyChildFolderWithoutPermission);
        final Folder parentFolder = initParentFolder(folders);
        doReturn(parentFolder).when(mockFolderManager).loadTree();
        initAclEntity(emptyChildFolderWithoutPermission);
        initEntities();
        mockSecurityContext();

        final Folder returnedFolder = folderApiService.loadTree();
        final Map<Long, AbstractHierarchicalEntity> childrenById = returnedFolder.getChildren().stream()
                .collect(Collectors.toMap(AbstractSecuredEntity::getId, Function.identity()));

        assertThat(returnedFolder).isEqualTo(parentFolder);
        assertTreeForFolderWithReadPermission(childrenById.get(childFolderWithPermission.getId()));
        assertTreeForFolderWithoutPermission(childrenById.get(childFolderWithoutPermission.getId()));
    }

    @Test
    @WithMockUser
    public void shouldLoadProjects() {
        doReturn(folder).when(mockFolderManager).loadAllProjects();

        assertThat(folderApiService.loadProjects()).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadProjectsWithHierarchy() {
        final Folder childFolderWithPermission = initFolderWithReadPermission();
        final Folder childFolderWithoutPermission = initFolderWithoutReadPermission();
        final List<Folder> folders = Arrays.asList(childFolderWithPermission, childFolderWithoutPermission,
                                                   emptyChildFolderWithoutPermission);
        final Folder parentFolder = initParentFolder(folders);
        doReturn(parentFolder).when(mockFolderManager).loadAllProjects();
        initAclEntity(emptyChildFolderWithoutPermission);
        initEntities();
        mockSecurityContext();

        doReturn(parentFolder).when(mockFolderManager).loadAllProjects();

        final Folder returnedFolder = folderApiService.loadProjects();
        final Map<Long, AbstractHierarchicalEntity> childrenById = returnedFolder.getChildren().stream()
                .collect(Collectors.toMap(AbstractSecuredEntity::getId, Function.identity()));

        assertThat(returnedFolder).isEqualTo(parentFolder);
        assertTreeForFolderWithReadPermission(childrenById.get(childFolderWithPermission.getId()));
        assertTreeForFolderWithoutPermission(childrenById.get(childFolderWithoutPermission.getId()));
    }

    @Test
    @WithMockUser
    public void shouldLoad() {
        doReturn(folder).when(mockFolderManager).load(ID);

        assertThat(folderApiService.load(ID)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadWithHierarchy() {
        final Folder childFolderWithPermission = initFolderWithReadPermission();
        final Folder childFolderWithoutPermission = initFolderWithoutReadPermission();
        final List<Folder> folders = Arrays.asList(childFolderWithPermission, childFolderWithoutPermission,
                                                   emptyChildFolderWithoutPermission);
        final Folder parentFolder = initParentFolder(folders);
        doReturn(parentFolder).when(mockFolderManager).load(ID);
        initAclEntity(emptyChildFolderWithoutPermission);
        initEntities();
        mockSecurityContext();

        final Folder returnedFolder = folderApiService.load(ID);
        final Map<Long, AbstractHierarchicalEntity> childrenById = returnedFolder.getChildren().stream()
                .collect(Collectors.toMap(AbstractSecuredEntity::getId, Function.identity()));

        assertThat(returnedFolder).isEqualTo(parentFolder);
        assertTreeForFolderWithReadPermission(childrenById.get(childFolderWithPermission.getId()));
        assertTreeForFolderWithoutPermission(childrenById.get(childFolderWithoutPermission.getId()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadByIdOrPathForAdmin() {
        doReturn(folder).when(mockFolderManager).loadByNameOrId(TEST_STRING);

        assertThat(folderApiService.loadByIdOrPath(TEST_STRING)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadByIdOrPathWhenPermissionIsGranted() {
        doReturn(folder).when(mockFolderManager).loadByNameOrId(TEST_STRING);
        initAclEntity(folder, AclPermission.READ);

        final Folder returnedFolder = folderApiService.loadByIdOrPath(TEST_STRING);

        assertThat(returnedFolder).isEqualTo(folder);
        assertThat(returnedFolder.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadByIdOrPathWhenPermissionIsNotGranted() {
        doReturn(folder).when(mockFolderManager).loadByNameOrId(TEST_STRING);
        initAclEntity(folder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.loadByIdOrPath(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteForAdmin() {
        doReturn(folder).when(mockFolderManager).delete(ID);

        assertThat(folderApiService.delete(ID)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDeleteForManagerWhenPermissionIsGranted() {
        doReturn(folder).when(mockFolderManager).delete(ID);
        initAclEntity(folder, AclPermission.WRITE);

        assertThat(folderApiService.delete(ID)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteWithInvalidRole() {
        assertThrows(AccessDeniedException.class, () -> folderApiService.delete(ID));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyDeleteWhenPermissionIsNotGranted() {
        initAclEntity(folder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.delete(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteForceForAdmin() {
        doReturn(folder).when(mockFolderManager).deleteForce(ID);

        assertThat(folderApiService.deleteForce(ID)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDeleteForceForManagerWhenPermissionForChildIsGranted() {
        final Folder folder = getFolder(ID, ID_100, ANOTHER_SIMPLE_USER);
        final Folder childFolder = getFolder(ID_2, ID, ANOTHER_SIMPLE_USER);
        folder.setChildFolders(Collections.singletonList(childFolder));
        doReturn(childFolder).when(mockFolderManager).load(ID);
        doReturn(folder).when(mockFolderManager).deleteForce(ID);
        initAclEntity(folder, AclPermission.WRITE);
        initAclEntity(childFolder, AclPermission.WRITE);

        assertThat(folderApiService.deleteForce(ID)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteForceWithInvalidRole() {
        assertThrows(AccessDeniedException.class, () -> folderApiService.deleteForce(ID));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyDeleteForceWhenPermissionForChildIsNotGranted() {
        final Folder folder = getFolder(ID, ID_100, ANOTHER_SIMPLE_USER);
        final Folder childFolder = getFolder(ID_2, ID, ANOTHER_SIMPLE_USER);
        folder.setChildFolders(Collections.singletonList(childFolder));
        doReturn(childFolder).when(mockFolderManager).load(ID);
        doReturn(folder).when(mockFolderManager).deleteForce(ID);
        initAclEntity(folder, AclPermission.WRITE);
        initAclEntity(childFolder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.deleteForce(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCloneFolderForAdmin() {
        doReturn(folder).when(mockFolderManager).cloneFolder(ID, ID_2, TEST_STRING);

        assertThat(folderApiService.cloneFolder(ID, ID_2, TEST_STRING)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldCloneFolderForManagerWhenPermissionIsGranted() {
        final Folder destinationFolder = getFolder(ID_2, ID, ANOTHER_SIMPLE_USER);
        doReturn(folder).when(mockFolderManager).cloneFolder(ID, ID_2, TEST_STRING);
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(destinationFolder, AclPermission.WRITE);

        assertThat(folderApiService.cloneFolder(ID, ID_2, TEST_STRING)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCloneFolderWithInvalidRole() {
        assertThrows(AccessDeniedException.class, () -> folderApiService.cloneFolder(ID, ID_2, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCloneFolderForManagerWhenDestinationFolderPermissionIsNotGranted() {
        final Folder destinationFolder = getFolder(ID_2, ID, ANOTHER_SIMPLE_USER);
        doReturn(folder).when(mockFolderManager).cloneFolder(ID, ID_2, TEST_STRING);
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(destinationFolder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.cloneFolder(ID, ID_2, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCloneFolderForManagerWhenTargetFolderPermissionIsNotGranted() {
        final Folder destinationFolder = getFolder(ID_2, ID, ANOTHER_SIMPLE_USER);
        doReturn(folder).when(mockFolderManager).cloneFolder(ID, ID_2, TEST_STRING);
        initAclEntity(folder);
        initAclEntity(destinationFolder, AclPermission.WRITE);

        assertThrows(AccessDeniedException.class, () -> folderApiService.cloneFolder(ID, ID_2, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLockFolderForAdmin() {
        doReturn(folder).when(mockFolderManager).lockFolder(ID);

        assertThat(folderApiService.lockFolder(ID)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = ANOTHER_SIMPLE_USER)
    public void shouldLockFolderForOwner() {
        doReturn(folder).when(mockEntityManager).load(AclClass.FOLDER, ID);
        doReturn(folder).when(mockFolderManager).lockFolder(ID);
        doReturn(ANOTHER_SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        final Folder returnedFolder = folderApiService.lockFolder(ID);

        assertThat(returnedFolder).isEqualTo(folder);
        assertThat(returnedFolder.getMask()).isEqualTo(ALL_PERMISSIONS);
    }

    @Test
    @WithMockUser
    public void shouldDenyLockFolderForNotOwner() {
        doReturn(folder).when(mockEntityManager).load(AclClass.FOLDER, ID);

        assertThrows(AccessDeniedException.class, () -> folderApiService.lockFolder(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUnlockFolderForAdmin() {
        doReturn(folder).when(mockFolderManager).unlockFolder(ID);

        assertThat(folderApiService.unlockFolder(ID)).isEqualTo(folder);
    }

    @Test
    @WithMockUser(username = ANOTHER_SIMPLE_USER)
    public void shouldUnlockFolderForOwner() {
        doReturn(folder).when(mockEntityManager).load(AclClass.FOLDER, ID);
        doReturn(folder).when(mockFolderManager).unlockFolder(ID);
        doReturn(ANOTHER_SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        final Folder returnedFolder = folderApiService.unlockFolder(ID);

        assertThat(returnedFolder).isEqualTo(folder);
        assertThat(returnedFolder.getMask()).isEqualTo(ALL_PERMISSIONS);
    }

    @Test
    @WithMockUser
    public void shouldDenyUnlockFolderForNotOwner() {
        doReturn(folder).when(mockEntityManager).load(AclClass.FOLDER, ID);

        assertThrows(AccessDeniedException.class, () -> folderApiService.unlockFolder(ID));
    }

    private void assertTreeForFolderWithReadPermission(final AbstractHierarchicalEntity folderWithPermission) {
        final List<? extends AbstractSecuredEntity> leaves = folderWithPermission.getLeaves();

        assertThat(folderWithPermission.getMask()).isEqualTo(READ_PERMISSION);
        assertThat(leaves).isEqualTo(allEntities);
        assertAclMaskForLeaves(leaves);
    }

    private void assertTreeForFolderWithoutPermission(final AbstractHierarchicalEntity folderWithoutPermission) {
        final List<? extends AbstractSecuredEntity> leaves = folderWithoutPermission.getLeaves();

        assertThat(leaves).isEqualTo(entitiesWithPermission);
        assertAclMaskForLeaves(leaves);
    }

    private void assertAclMaskForLeaves(final List<? extends AbstractSecuredEntity> leaves) {
        leaves.forEach(entity -> assertThat(entity.getMask()).isEqualTo(READ_PERMISSION));
    }

    private FolderWithMetadata initFolderWithReadPermission() {
        final FolderWithMetadata childFolderWithPermission = getFolderWithMetadata(ID, ID_3, ANOTHER_SIMPLE_USER);
        childFolderWithPermission.setConfigurations(Arrays.asList(runConfigRead1, runConfigWithoutPermission1));
        childFolderWithPermission.setStorages(Arrays.asList(storageRead1, storageWithoutPermission1));
        childFolderWithPermission.setPipelines(Arrays.asList(pipelineRead1, pipelineWithoutPermission1));
        initAclEntity(childFolderWithPermission, AclPermission.READ);
        return childFolderWithPermission;
    }

    private FolderWithMetadata initFolderWithoutReadPermission() {
        final FolderWithMetadata childFolderWithoutPermission = getFolderWithMetadata(ID_2, ID_3, ANOTHER_SIMPLE_USER);
        childFolderWithoutPermission.setConfigurations(Arrays.asList(runConfigRead2, runConfigWithoutPermission2));
        childFolderWithoutPermission.setStorages(Arrays.asList(storageRead2, storageWithoutPermission2));
        childFolderWithoutPermission.setPipelines(Arrays.asList(pipelineRead2, pipelineWithoutPermission2));
        initAclEntity(childFolderWithoutPermission);
        return childFolderWithoutPermission;
    }

    private FolderWithMetadata initParentFolder(final List<Folder> folders) {
        final FolderWithMetadata parentFolder = getFolderWithMetadata(ID_3, ID_110, ANOTHER_SIMPLE_USER);
        parentFolder.setChildFolders(folders);
        return parentFolder;
    }

    private void initEntities() {
        initAclEntity(pipelineRead1, AclPermission.READ);
        initAclEntity(pipelineRead2, AclPermission.READ);
        initAclEntity(pipelineWithoutPermission1);
        initAclEntity(pipelineWithoutPermission2);
        initAclEntity(storageRead1, AclPermission.READ);
        initAclEntity(storageRead2, AclPermission.READ);
        initAclEntity(storageWithoutPermission1);
        initAclEntity(storageWithoutPermission2);
        initAclEntity(runConfigRead1, AclPermission.READ);
        initAclEntity(runConfigRead2, AclPermission.READ);
        initAclEntity(runConfigWithoutPermission1);
        initAclEntity(runConfigWithoutPermission2);
    }
}

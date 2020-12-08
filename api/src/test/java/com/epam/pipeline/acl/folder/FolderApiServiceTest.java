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
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
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
import static com.epam.pipeline.test.creator.CommonCreatorConstants.WRITE_PERMISSION;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getRunConfiguration;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getAzureBlobStorage;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolder;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolderWithMetadata;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipeline;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class FolderApiServiceTest extends AbstractAclTest {

    private static final String FOLDER_MANAGER = "FOLDER_MANAGER";
    private final Folder folder = getFolder(ID, ANOTHER_SIMPLE_USER);
    private final FolderWithMetadata folderWithMetadata = getFolderWithMetadata(ID, ANOTHER_SIMPLE_USER);

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
        initAclEntity(folder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.create(folder));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCreateWhenParentIdIsNull() {
        final Folder folder = getFolder(ID, ANOTHER_SIMPLE_USER);
        folder.setParentId(null);
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
        initAclEntity(folder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.createFromTemplate(folder, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCreateFromTemplateWhenParentIdIsNull() {
        final Folder folder = getFolder(ID, ANOTHER_SIMPLE_USER);
        folder.setParentId(null);
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
        final FolderWithMetadata parentFolder = getFolderWithMetadata(ID_3, ANOTHER_SIMPLE_USER);
        final FolderWithMetadata childFolderWithPermission = getFolderWithMetadata(ID, ANOTHER_SIMPLE_USER);
        final FolderWithMetadata childFolderWithoutPermission = getFolderWithMetadata(ID_2, ANOTHER_SIMPLE_USER);
        final FolderWithMetadata emptyChildFolderWithoutPermission = getFolderWithMetadata(ID_3, ANOTHER_SIMPLE_USER);
        final Pipeline pipelineRead1 = getPipeline(ID, ANOTHER_SIMPLE_USER);
        final Pipeline pipelineRead2 = getPipeline(ID, ANOTHER_SIMPLE_USER);
        final Pipeline pipelineWithoutPermission1 = getPipeline(ID_2, ANOTHER_SIMPLE_USER);
        final Pipeline pipelineWithoutPermission2 = getPipeline(ID_2, ANOTHER_SIMPLE_USER);
        childFolderWithPermission.setParent(parentFolder);
        childFolderWithoutPermission.setParent(parentFolder);
        childFolderWithPermission.setPipelines(Arrays.asList(pipelineRead1, pipelineWithoutPermission1));
        childFolderWithoutPermission.setPipelines(Arrays.asList(pipelineRead2, pipelineWithoutPermission2));
        parentFolder.setChildFolders(Arrays.asList(childFolderWithPermission,
                                                   childFolderWithoutPermission,
                                                   emptyChildFolderWithoutPermission));

        initAclEntity(parentFolder);
        initAclEntity(childFolderWithPermission, AclPermission.READ);
        initAclEntity(childFolderWithoutPermission);
        initAclEntity(emptyChildFolderWithoutPermission);
        initAclEntity(pipelineRead1, AclPermission.READ);
        initAclEntity(pipelineRead2, AclPermission.READ);
        initAclEntity(pipelineWithoutPermission1);
        initAclEntity(pipelineWithoutPermission2);
        mockSecurityContext();

        doReturn(childFolderWithPermission).when(mockEntityManager).load(AclClass.FOLDER, ID);
        doReturn(childFolderWithPermission).when(mockFolderManager).getProject(ID, AclClass.FOLDER);

        final FolderWithMetadata returnedFolder = folderApiService.getProject(ID, AclClass.FOLDER);

        assertThat(returnedFolder).isEqualTo(childFolderWithPermission);
        assertThat(returnedFolder.getParent()).isEqualTo(parentFolder);
        assertThat(returnedFolder.getParent().getMask()).isEqualTo(ALL_PERMISSIONS);
        assertThat(returnedFolder.getParent().getChildren().get(1).getMask()).isEqualTo(READ_PERMISSION);
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
        final Folder parentFolder = getFolder(ID_3, ANOTHER_SIMPLE_USER);
        final Folder childFolderWithPermission = getFolder(ID, ANOTHER_SIMPLE_USER);
        final Folder childFolderWithoutPermission = getFolder(ID_2, ANOTHER_SIMPLE_USER);
        final Folder emptyChildFolderWithoutPermission = getFolder(ID_3, ANOTHER_SIMPLE_USER);
        final Pipeline pipelineRead1 = getPipeline(ID, ANOTHER_SIMPLE_USER);
        final Pipeline pipelineRead2 = getPipeline(ID, ANOTHER_SIMPLE_USER);
        final Pipeline pipelineWithoutPermission1 = getPipeline(ID_2, ANOTHER_SIMPLE_USER);
        final Pipeline pipelineWithoutPermission2 = getPipeline(ID_2, ANOTHER_SIMPLE_USER);
        childFolderWithPermission.setPipelines(Arrays.asList(pipelineRead1, pipelineWithoutPermission1));
        childFolderWithoutPermission.setPipelines(Arrays.asList(pipelineRead2, pipelineWithoutPermission2));
        parentFolder.setChildFolders(Arrays.asList(childFolderWithPermission,
                childFolderWithoutPermission,
                emptyChildFolderWithoutPermission));

        initAclEntity(parentFolder);
        initAclEntity(childFolderWithPermission, AclPermission.READ);
        initAclEntity(childFolderWithoutPermission);
        initAclEntity(emptyChildFolderWithoutPermission);
        initAclEntity(pipelineRead1, AclPermission.READ);
        initAclEntity(pipelineRead2, AclPermission.READ);
        initAclEntity(pipelineWithoutPermission1);
        initAclEntity(pipelineWithoutPermission2);
        mockSecurityContext();

        doReturn(parentFolder).when(mockFolderManager).loadTree();

        final Folder returnedFolder = folderApiService.loadTree();

        assertThat(returnedFolder).isEqualTo(parentFolder);
        assertThat(returnedFolder.getChildren()).isEqualTo(Arrays.asList(childFolderWithPermission, childFolderWithoutPermission));
        assertThat(returnedFolder.getChildren().get(0)).isEqualTo(childFolderWithPermission);
        assertThat(returnedFolder.getChildren().get(1)).isEqualTo(childFolderWithoutPermission);
        assertThat(returnedFolder.getChildren().get(0).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(0).getLeaves().get(0).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(0).getLeaves().get(1).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(1).getLeaves().get(0).getMask()).isEqualTo(READ_PERMISSION);
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
        final Folder parentFolder = getFolder(ID_3, ANOTHER_SIMPLE_USER);
        final Folder childFolderWithPermission = getFolder(ID, ANOTHER_SIMPLE_USER);
        final Folder childFolderWithoutPermission = getFolder(ID_2, ANOTHER_SIMPLE_USER);
        final Folder emptyChildFolderWithoutPermission = getFolder(ID_3, ANOTHER_SIMPLE_USER);
        final S3bucketDataStorage storageRead1 = getS3bucketDataStorage(ID_2, ANOTHER_SIMPLE_USER);
        final S3bucketDataStorage storageRead2 = getS3bucketDataStorage(ID_2, ANOTHER_SIMPLE_USER);
        final S3bucketDataStorage storageWithoutPermission1 = getS3bucketDataStorage(ID_3, ANOTHER_SIMPLE_USER);
        final S3bucketDataStorage storageWithoutPermission2 = getS3bucketDataStorage(ID_3, ANOTHER_SIMPLE_USER);
        childFolderWithPermission.setStorages(Arrays.asList(storageRead1, storageWithoutPermission1));
        childFolderWithoutPermission.setStorages(Arrays.asList(storageRead2, storageWithoutPermission2));
        parentFolder.setChildFolders(Arrays.asList(childFolderWithPermission,
                childFolderWithoutPermission,
                emptyChildFolderWithoutPermission));

        initAclEntity(parentFolder);
        initAclEntity(childFolderWithPermission, AclPermission.READ);
        initAclEntity(childFolderWithoutPermission);
        initAclEntity(emptyChildFolderWithoutPermission);
        initAclEntity(storageRead1, AclPermission.READ);
        initAclEntity(storageRead2, AclPermission.READ);
        initAclEntity(storageWithoutPermission1);
        initAclEntity(storageWithoutPermission2);
        mockSecurityContext();

        doReturn(parentFolder).when(mockFolderManager).loadAllProjects();

        final Folder returnedFolder = folderApiService.loadProjects();

        assertThat(returnedFolder).isEqualTo(parentFolder);
        assertThat(returnedFolder.getChildren()).isEqualTo(Arrays.asList(childFolderWithPermission, childFolderWithoutPermission));
        assertThat(returnedFolder.getChildren().get(0)).isEqualTo(childFolderWithPermission);
        assertThat(returnedFolder.getChildren().get(1)).isEqualTo(childFolderWithoutPermission);
        assertThat(returnedFolder.getChildren().get(0).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(0).getLeaves().get(0).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(0).getLeaves().get(1).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(1).getLeaves().get(0).getMask()).isEqualTo(READ_PERMISSION);
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
        final Folder parentFolder = getFolder(ID_3, ANOTHER_SIMPLE_USER);
        final Folder childFolderWithPermission = getFolder(ID, ANOTHER_SIMPLE_USER);
        final Folder childFolderWithoutPermission = getFolder(ID_2, ANOTHER_SIMPLE_USER);
        final Folder emptyChildFolderWithoutPermission = getFolder(ID_3, ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfigRead1 = getRunConfiguration(ID_2, ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfigRead2 = getRunConfiguration(ID_2, ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfigWithoutPermission1 = getRunConfiguration(ID_3, ANOTHER_SIMPLE_USER);
        final RunConfiguration runConfigWithoutPermission2 = getRunConfiguration(ID_3, ANOTHER_SIMPLE_USER);
        childFolderWithPermission.setConfigurations(Arrays.asList(runConfigRead1, runConfigWithoutPermission1));
        childFolderWithoutPermission.setConfigurations(Arrays.asList(runConfigRead2, runConfigWithoutPermission2));
        parentFolder.setChildFolders(Arrays.asList(childFolderWithPermission,
                childFolderWithoutPermission,
                emptyChildFolderWithoutPermission));

        initAclEntity(parentFolder);
        initAclEntity(childFolderWithPermission, AclPermission.READ);
        initAclEntity(childFolderWithoutPermission);
        initAclEntity(emptyChildFolderWithoutPermission);
        initAclEntity(runConfigRead1, AclPermission.READ);
        initAclEntity(runConfigRead2, AclPermission.READ);
        initAclEntity(runConfigWithoutPermission1);
        initAclEntity(runConfigWithoutPermission2);
        mockSecurityContext();

        doReturn(parentFolder).when(mockFolderManager).loadAllProjects();

        final Folder returnedFolder = folderApiService.loadProjects();

        assertThat(returnedFolder).isEqualTo(parentFolder);
        assertThat(returnedFolder.getChildren()).isEqualTo(Arrays.asList(childFolderWithPermission, childFolderWithoutPermission));
        assertThat(returnedFolder.getChildren().get(0)).isEqualTo(childFolderWithPermission);
        assertThat(returnedFolder.getChildren().get(1)).isEqualTo(childFolderWithoutPermission);
        assertThat(returnedFolder.getChildren().get(0).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(0).getLeaves().get(0).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(0).getLeaves().get(1).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedFolder.getChildren().get(1).getLeaves().get(0).getMask()).isEqualTo(READ_PERMISSION);
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
        final Folder folder = getFolder(ID, ANOTHER_SIMPLE_USER);
        final Folder childFolder = getFolder(ID, ANOTHER_SIMPLE_USER);
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
        final Folder folder = getFolder(ID, ANOTHER_SIMPLE_USER);
        final Folder childFolder = getFolder(ID, ANOTHER_SIMPLE_USER);
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
        final Folder destinationFolder = getFolder(ID_2, ANOTHER_SIMPLE_USER);
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
        final Folder destinationFolder = getFolder(ID_2, ANOTHER_SIMPLE_USER);
        doReturn(folder).when(mockFolderManager).cloneFolder(ID, ID_2, TEST_STRING);
        initAclEntity(folder, AclPermission.READ);
        initAclEntity(destinationFolder);

        assertThrows(AccessDeniedException.class, () -> folderApiService.cloneFolder(ID, ID_2, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = FOLDER_MANAGER, username = SIMPLE_USER)
    public void shouldDenyCloneFolderForManagerWhenTargetFolderPermissionIsNotGranted() {
        final Folder destinationFolder = getFolder(ID_2, ANOTHER_SIMPLE_USER);
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

//    private void initCommonToolAcls() {
//        initAclEntity(toolRead1, AclPermission.READ);
//        initAclEntity(toolRead2, AclPermission.READ);
//        initAclEntity(toolWithoutPermission1);
//        initAclEntity(toolWithoutPermission2);
//        initAclEntity(emptyToolGroupWithoutPermission);
//    }
//
//    private void assertPermissionInheritedForLeaves(final int permission,
//                                                    final AbstractHierarchicalEntity actualToolGroupWithPermissions) {
//        final Map<Long, AbstractSecuredEntity> leavesById = actualToolGroupWithPermissions.getLeaves().stream()
//                .collect(Collectors.toMap(AbstractSecuredEntity::getId, Function.identity()));
//        final List<? extends AbstractSecuredEntity> actualToolsWithReadPermissions =
//                actualToolGroupWithPermissions.getLeaves();
//        assertThat(actualToolGroupWithPermissions.getMask()).isEqualTo(permission);
//        assertThat(actualToolsWithReadPermissions).isEqualTo(toolList1);
//        final AbstractSecuredEntity toolWithReadPermission = leavesById.get(toolRead1.getId());
//        assertThat(toolWithReadPermission.getMask()).isEqualTo(permission);
//        final AbstractSecuredEntity toolWithoutPermission = leavesById.get(toolWithoutPermission1.getId());
//        assertThat(toolWithoutPermission.getMask()).isEqualTo(permission); // permission inherited from tool group
//    }
//
//    private void assertPermissionGrantedToGroup(final int permission,
//                                                final AbstractHierarchicalEntity actualToolGroupWithoutPermissions) {
//        final List<? extends AbstractSecuredEntity> resultToolGroupWithoutPermissionLeaves =
//                actualToolGroupWithoutPermissions.getLeaves();
//        assertThat(resultToolGroupWithoutPermissionLeaves).hasSize(1); // no permission tool has been filtered
//        final AbstractSecuredEntity actualTool = resultToolGroupWithoutPermissionLeaves.get(0);
//        assertThat(actualTool).isEqualTo(toolRead1);
//        assertThat(actualTool.getMask()).isEqualTo(permission);
//    }
//
//    private void assertDockerRegistryAclTreeWithRead(final List<AbstractHierarchicalEntity> actualGroups,
//                                                     final List<? extends AbstractHierarchicalEntity> expectedGroups) {
//        assertDockerRegistryAclTreeWithPermission(actualGroups, expectedGroups, READ_PERMISSION);
//    }
//
//    private void assertDockerRegistryAclTreeWithReadWrite(
//            final List<AbstractHierarchicalEntity> actualGroups,
//            final List<? extends AbstractHierarchicalEntity> expectedGroups) {
//        assertDockerRegistryAclTreeWithPermission(actualGroups, expectedGroups, READ_PERMISSION + WRITE_PERMISSION);
//    }
//
//    private void assertDockerRegistryAclTreeWithPermission(
//            final List<AbstractHierarchicalEntity> actualGroups,
//            final List<? extends AbstractHierarchicalEntity> expectedGroups,
//            final int permission) {
//        final Map<Long, AbstractHierarchicalEntity> childrenById = actualGroups.stream()
//                .collect(Collectors.toMap(AbstractSecuredEntity::getId, Function.identity()));
//        assertThat(actualGroups).hasSize(expectedGroups.size()).containsAll(expectedGroups);
//
//        assertPermissionGrantedToGroup(permission, childrenById.get(toolRead1.getId()));
//        assertPermissionInheritedForLeaves(permission, childrenById.get(toolWithoutPermission1.getId()));
//    }
//
//    private void assertDockerRegistryAclTreeForAdmin(final List<AbstractHierarchicalEntity> actualGroups,
//                                                     final List<? extends AbstractHierarchicalEntity> expectedGroups) {
//        final Map<Long, AbstractHierarchicalEntity> childrenById = actualGroups.stream()
//                .collect(Collectors.toMap(AbstractSecuredEntity::getId, Function.identity()));
//        assertThat(actualGroups).containsAll(expectedGroups);
//
//        final List<? extends AbstractSecuredEntity> toolGroupLeaves = childrenById.get(toolRead1.getId()).getLeaves();
//        final List<? extends AbstractSecuredEntity> toolGroupWithoutPermissionLeaves =
//                childrenById.get(toolWithoutPermission1.getId()).getLeaves();
//
//        assertThat(toolGroupWithoutPermissionLeaves).isEqualTo(toolList1);
//        assertThat(toolGroupLeaves).isEqualTo(toolList1);
//    }
//
//    private DockerRegistry getChildFolderWithGroups(final List<ToolGroup> toolGroups) {
//        final Folder childFolder = getFolder(ID, ANOTHER_SIMPLE_USER);
//        childFolder.set(toolGroups);
//        return dockerRegistryWithTools;
//    }
//
//    private ToolGroup initToolGroupWithReadPermissions() {
//        final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ID_2, ANOTHER_SIMPLE_USER);
//        toolGroup.setTools(toolList1);
//        initAclEntity(toolGroup, AclPermission.READ);
//        return toolGroup;
//    }
//
//    private ToolGroup initToolGroupWithoutPermissions() {
//        final ToolGroup toolGroupWithoutPermission = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
//        toolGroupWithoutPermission.setTools(toolList2);
//        initAclEntity(toolGroupWithoutPermission);
//        return toolGroupWithoutPermission;
//    }
}

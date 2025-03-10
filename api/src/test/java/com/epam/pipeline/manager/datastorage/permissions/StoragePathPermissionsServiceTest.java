/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.permissions;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.datastorage.permissions.StoragePathPermissionsDao;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissions;
import com.epam.pipeline.dto.datastorage.permissions.StorageFolderListPermissionsContainer;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.user.SidImpl;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.util.CustomAssertions;
import org.junit.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class StoragePathPermissionsServiceTest {
    private static final String USER = "User";
    private static final String ROLE1 = "role1";
    private static final String ROLE2 = "role2";
    private static final String ROOT_PATH = "/";
    private static final String PATH0 = "/A/";
    private static final String PATH1 = "/A/B/C/D/";
    private static final String PATH2 = "/A/B/C/";
    private static final String PATH3 = "/A/B/C/E/";
    private static final String FILENAME1 = "file1.txt";
    private static final String FILENAME2 = "file2.txt";
    private static final String FILE_PATH1 = PATH1 + FILENAME1;
    private static final int READ = 1;
    private static final int WRITE = 4;
    private static final Long STORAGE_ID = 1L;
    private static final List<String> SPLIT_TO_PATH0 = Arrays.asList(ROOT_PATH, PATH0);
    private static final List<String> SPLIT_TO_PATH1 = Arrays.asList(ROOT_PATH, PATH0, "/A/B/", PATH2, PATH1);
    private static final List<String> SPLIT_TO_PATH2 = Arrays.asList(ROOT_PATH, PATH0, "/A/B/", PATH2);
    private static final StoragePathPermissions WRITE_FOLDER_PATH1 = StoragePathPermissions.builder()
            .folderPath(PATH1).mask(WRITE).build();
    private static final StoragePathPermissions WRITE_FOLDER_PATH3 = StoragePathPermissions.builder()
            .folderPath(PATH3).mask(WRITE).build();
    private static final StoragePathPermissions WRITE_FILE_PATH1 = StoragePathPermissions.builder()
            .folderPath(PATH1).mask(WRITE).fileName(FILENAME1).build();
    private static final StoragePathPermissions READ_FOLDER_PATH1 = StoragePathPermissions.builder()
            .folderPath(PATH1).mask(READ).build();
    private static final StoragePathPermissions READ_FILE_PATH1 = StoragePathPermissions.builder()
            .folderPath(PATH1).mask(READ).fileName(FILENAME1).build();
    private static final StoragePathPermissions READ_FILE_PATH2 = StoragePathPermissions.builder()
            .folderPath(PATH2).mask(READ).fileName(FILENAME1).build();
    private static final StoragePathPermissions WRITE_FILE_PATH12 = StoragePathPermissions.builder()
            .folderPath(PATH1).mask(WRITE).fileName(FILENAME2).build();
    private static final StoragePathPermissions WRITE_ROOT = StoragePathPermissions.builder()
            .folderPath(ROOT_PATH).mask(WRITE).build();
    private static final List<SidImpl> SIDS = Arrays.asList(userSid(), roleSid(ROLE1), roleSid(ROLE2));

    private final StoragePathPermissionsDao pathPermissionsDao = mock(StoragePathPermissionsDao.class);
    private final DataStorageDao dataStorageDao = mock(DataStorageDao.class);
    private final UserManager userManager = mock(UserManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final StoragePathPermissionsService service = new StoragePathPermissionsService(
            pathPermissionsDao,
            dataStorageDao,
            userManager,
            messageHelper);

    @Test
    public void shouldNormalizeFolderPaths() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(WRITE_ROOT)).when(pathPermissionsDao)
                .findClosestFolderPermission(any(), any(), any());

        service.canWriteToFolder(STORAGE_ID, "A/B/C/D");
        verify(pathPermissionsDao).findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);

        service.canWriteToFolder(STORAGE_ID, "");
        verify(pathPermissionsDao).findClosestFolderPermission(STORAGE_ID, SIDS, Collections.singletonList(ROOT_PATH));
    }

    @Test
    public void shouldNormalizeFilePaths() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(WRITE_ROOT)).when(pathPermissionsDao)
                .findClosestFilePermission(any(), any(), any(), any(), any());

        service.canWriteToFile(STORAGE_ID, "A/B/C/D/file1.txt");
        verify(pathPermissionsDao).findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);

        service.canWriteToFile(STORAGE_ID, "/file1.txt");
        service.canWriteToFile(STORAGE_ID, FILENAME1);
        verify(pathPermissionsDao, times(2)).findClosestFilePermission(STORAGE_ID, SIDS,
                Collections.singletonList(ROOT_PATH), ROOT_PATH, FILENAME1);
    }

    @Test
    public void shouldNotThrowIfUserCanWriteToFileWhenPermissionsOnFileGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(WRITE_FILE_PATH1)).when(pathPermissionsDao)
                .findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);

        service.canWriteToFile(STORAGE_ID, FILE_PATH1);
        verify(pathPermissionsDao).findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);
    }

    @Test
    public void shouldNotThrowIfUserCanWriteToFileWhenPermissionsOnFolderGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(WRITE_FOLDER_PATH1)).when(pathPermissionsDao)
                .findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);

        service.canWriteToFile(STORAGE_ID, FILE_PATH1);
    }

    @Test
    public void shouldThrowIfUserCanNotWriteToFile() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(READ_FILE_PATH1)).when(pathPermissionsDao)
                .findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);

        assertThrows(AccessDeniedException.class, () -> service.canWriteToFile(STORAGE_ID, FILE_PATH1));
    }

    @Test
    public void shouldNotThrowIfUserCanReadFileWhenPermissionsOnFileGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(READ_FILE_PATH1)).when(pathPermissionsDao)
                .findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);

        service.canReadFile(STORAGE_ID, FILE_PATH1);
        verify(pathPermissionsDao).findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);
    }

    @Test
    public void shouldNotThrowIfUserCanReadFileWhenPermissionsOnFolderGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(READ_FOLDER_PATH1)).when(pathPermissionsDao)
                .findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);

        service.canReadFile(STORAGE_ID, FILE_PATH1);
        verify(pathPermissionsDao).findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);
    }

    @Test
    public void shouldThrowIfUserCanNotReadFile() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.empty()).when(pathPermissionsDao)
                .findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);

        assertThrows(AccessDeniedException.class, () -> service.canReadFile(STORAGE_ID, FILE_PATH1));
        verify(pathPermissionsDao).findClosestFilePermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1, PATH1, FILENAME1);
    }

    @Test
    public void shouldNotThrowIfUserCanWriteToFolder() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(WRITE_FOLDER_PATH1)).when(pathPermissionsDao)
                .findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);

        service.canWriteToFolder(STORAGE_ID, PATH1);
        verify(pathPermissionsDao).findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
    }

    @Test
    public void shouldThrowIfUserCanNotWriteToFolder() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.empty()).when(pathPermissionsDao)
                .findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);

        assertThrows(AccessDeniedException.class, () -> service.canWriteToFolder(STORAGE_ID, PATH1));
        verify(pathPermissionsDao).findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
    }

    @Test
    public void shouldNotThrowIfUserCanReadFolderContent() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.of(READ_FOLDER_PATH1)).when(pathPermissionsDao)
                .findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);

        service.canReadFolder(STORAGE_ID, PATH1);
        verify(pathPermissionsDao).findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
    }

    @Test
    public void shouldThrowIfUserCanNotReadFolderContent() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(Optional.empty()).when(pathPermissionsDao)
                .findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);

        assertThrows(AccessDeniedException.class, () -> service.canReadFolder(STORAGE_ID, PATH1));
        verify(pathPermissionsDao).findClosestFolderPermission(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
    }

    @Test
    public void shouldNotThrowIfUserCanGetFolderWhenPermissionsGrantedOnParentFolder() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(1).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);

        service.canGetFolder(STORAGE_ID, PATH1);
        verify(pathPermissionsDao).countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
        CustomAssertions.notInvoked(pathPermissionsDao).findByPrefix(any(), any(), any());
    }

    @Test
    public void shouldNotThrowIfUserCanGetFolderWhenPermissionsGrantedOnChildFile() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(0).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
        doReturn(Collections.singletonList(WRITE_FILE_PATH1))
                .when(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH1);

        service.canGetFolder(STORAGE_ID, PATH1);
        verify(pathPermissionsDao).countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
        verify(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH1);
    }

    @Test
    public void shouldThrowIfUserCanNotGetFolder() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(0).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
        doReturn(Collections.emptyList())
                .when(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH1);

        assertThrows(AccessDeniedException.class, () -> service.canGetFolder(STORAGE_ID, PATH1));
        verify(pathPermissionsDao).countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
        verify(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH1);
    }

    @Test
    public void shouldGetFolderListPermissionsForUserWhenPermissionsOnRequestedFolderGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(1).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);

        final StorageFolderListPermissionsContainer permissionsContainer = service
                .getFolderListPermissions(STORAGE_ID, PATH1);
        assertThat(permissionsContainer.isHasListPermissions()).isEqualTo(true);
        CustomAssertions.notInvoked(pathPermissionsDao).findByPrefix(any(), any(), any());
    }

    @Test
    public void shouldGetFolderListPermissionsForUserWhenPermissionsOnFilesGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(0).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
        doReturn(Arrays.asList(WRITE_FILE_PATH1, WRITE_FILE_PATH12))
                .when(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH1);

        final StorageFolderListPermissionsContainer permissionsContainer = service
                .getFolderListPermissions(STORAGE_ID, PATH1);
        assertThat(permissionsContainer.isHasListPermissions()).isEqualTo(false);
        assertThat(permissionsContainer.getFiles()).hasSize(2).contains(FILENAME1, FILENAME2);
        assertThat(permissionsContainer.getFolders()).isNull();
        verify(pathPermissionsDao).countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH1);
        verify(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH1);
    }

    @Test
    public void shouldGetFolderListPermissionsForUserWhenPermissionsOnFoldersGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(0).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH2);
        doReturn(Arrays.asList(WRITE_FOLDER_PATH1, WRITE_FOLDER_PATH3))
                .when(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH2);

        final StorageFolderListPermissionsContainer permissionsContainer = service
                .getFolderListPermissions(STORAGE_ID, PATH2);
        assertThat(permissionsContainer.isHasListPermissions()).isEqualTo(false);
        assertThat(permissionsContainer.getFolders()).hasSize(2).contains("D", "E");
        assertThat(permissionsContainer.getFiles()).isNull();
        verify(pathPermissionsDao).countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH2);
        verify(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH2);
    }

    @Test
    public void shouldGetFolderListPermissionsForUserWhenPermissionsOnFolderAndFileGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(0).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH2);
        doReturn(Arrays.asList(WRITE_FOLDER_PATH1, READ_FILE_PATH2))
                .when(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH2);

        final StorageFolderListPermissionsContainer permissionsContainer = service
                .getFolderListPermissions(STORAGE_ID, PATH2);
        assertThat(permissionsContainer.isHasListPermissions()).isEqualTo(false);
        assertThat(permissionsContainer.getFolders()).hasSize(1).contains("D");
        assertThat(permissionsContainer.getFiles()).hasSize(1).contains(FILENAME1);
        verify(pathPermissionsDao).countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH2);
        verify(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH2);
    }

    @Test
    public void shouldGetFolderListPermissionsForUserWhenNestedPermissionsGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(0).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH0);
        doReturn(Arrays.asList(WRITE_FOLDER_PATH1, READ_FILE_PATH2))
                .when(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH0);

        final StorageFolderListPermissionsContainer permissionsContainer = service
                .getFolderListPermissions(STORAGE_ID, PATH0);
        assertThat(permissionsContainer.isHasListPermissions()).isEqualTo(false);
        assertThat(permissionsContainer.getFolders()).hasSize(1).contains("B");
        assertThat(permissionsContainer.getFiles()).isNull();
        verify(pathPermissionsDao).countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH0);
        verify(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH0);
    }

    @Test
    public void shouldThrowWhenGetFolderListPermissionsForUserIfNoPermissionsGranted() {
        doReturn(pipelineUser()).when(userManager).getCurrentUser();
        doReturn(0).when(pathPermissionsDao)
                .countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH0);
        doReturn(Collections.emptyList()).when(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH0);

        assertThrows(AccessDeniedException.class, () -> service.getFolderListPermissions(STORAGE_ID, PATH0));
        verify(pathPermissionsDao).countParentFoldersByStorageAndSids(STORAGE_ID, SIDS, SPLIT_TO_PATH0);
        verify(pathPermissionsDao).findByPrefix(STORAGE_ID, SIDS, PATH0);
    }

    private static PipelineUser pipelineUser() {
        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setUserName(USER);
        pipelineUser.setRoles(Arrays.asList(role(ROLE1), role(ROLE2)));
        return pipelineUser;
    }

    private static Role role(final String name) {
        return new Role(name);
    }

    private static SidImpl userSid() {
        final SidImpl user = new SidImpl();
        user.setName(USER.toUpperCase(Locale.ROOT));
        user.setPrincipal(true);
        return user;
    }

    private static SidImpl roleSid(final String name) {
        final SidImpl group = new SidImpl();
        group.setName(name.toUpperCase(Locale.ROOT));
        group.setPrincipal(false);
        return group;
    }
}

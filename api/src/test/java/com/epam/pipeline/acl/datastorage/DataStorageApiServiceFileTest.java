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

package com.epam.pipeline.acl.datastorage;

import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import org.junit.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_ARRAY;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_MAP;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_SET;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DataStorageApiServiceFileTest extends AbstractDataStorageAclTest {

    private final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
    private final AbstractDataStorageItem dataStorageFile = DatastorageCreatorUtils.getDataStorageFile();
    private final DataStorageDownloadFileUrl downloadFileUrl =
            DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
    private final DataStorageItemContent dataStorageItemContent =
            DatastorageCreatorUtils.getDefaultDataStorageItemContent();
    private final InputStream inputStream = new ByteArrayInputStream(TEST_STRING.getBytes());
    private final DataStorageStreamingContent dataStorageStreamingContent =
            DatastorageCreatorUtils.getDefaultDataStorageStreamingContent(inputStream);

    private final List<PathDescription> pathDescriptionList = DatastorageCreatorUtils.getPathDescriptionList();
    private final List<String> testList = Collections.singletonList(TEST_STRING);
    private final List<UpdateDataStorageItemVO> dataStorageItemVOList =
            DatastorageCreatorUtils.getUpdateDataStorageItemVOList();
    private final List<DataStorageFile> dataStorageFileList = DatastorageCreatorUtils.getDataStorageFileList();
    private final List<DataStorageDownloadFileUrl> downloadFileUrlList =
            DatastorageCreatorUtils.getDataStorageDownloadFileUrlList();

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetDataStorageItemsForAdmin() {
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        mockUserContext(context);

        assertThat(dataStorageApiService.getDataStorageItems(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING)).isEqualTo(dataStorageListing);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetDataStorageItemsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.getDataStorageItems(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING)).isEqualTo(dataStorageListing);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataStorageItemsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataStorageItemsWhenCheckStorageSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.READ);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetDataStorageItemsOwnerForAdmin() {
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        mockUserContext(context);

        assertThat(dataStorageApiService.getDataStorageItemsOwner(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING)).isEqualTo(dataStorageListing);
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageItemsOwnerWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService.getDataStorageItemsOwner(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING)).isEqualTo(dataStorageListing);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataStorageItemsOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataStorageItemsOwner(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataStorageItemsOwnerWhenCheckStorageSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataStorageItemsOwner(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDataStorageItemsForAdmin() {
        doReturn(dataStorageFileList).when(mockDataStorageManager).updateDataStorageItems(ID, dataStorageItemVOList);
        mockUserContext(context);

        assertThat(dataStorageApiService.updateDataStorageItems(ID, dataStorageItemVOList))
                .isEqualTo(dataStorageFileList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateDataStorageItemsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFileList).when(mockDataStorageManager).updateDataStorageItems(ID, dataStorageItemVOList);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.updateDataStorageItems(ID, dataStorageItemVOList))
                .isEqualTo(dataStorageFileList);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDataStorageItemsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFileList).when(mockDataStorageManager).updateDataStorageItems(ID, dataStorageItemVOList);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.updateDataStorageItems(ID, dataStorageItemVOList));
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDataStorageItemsWhenCheckStorageSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(dataStorageFileList).when(mockDataStorageManager).updateDataStorageItems(ID, dataStorageItemVOList);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.updateDataStorageItems(ID, dataStorageItemVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateFileForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY);
        mockUserContext(context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateFileWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY));
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateFileThroughInputStreamForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream);
        mockUserContext(context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateFileThroughInputStreamWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileThroughInputStreamWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream));
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileThroughInputStreamWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateFileThroughPathForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager).createDataStorageFile(ID, TEST_STRING, TEST_ARRAY);
        mockUserContext(context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_ARRAY))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateFileThroughPathWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager).createDataStorageFile(ID, TEST_STRING, TEST_ARRAY);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_ARRAY))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileThroughPathWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager).createDataStorageFile(ID, TEST_STRING, TEST_ARRAY);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_ARRAY));
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileThroughPathWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager).createDataStorageFile(ID, TEST_STRING, TEST_ARRAY);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_ARRAY));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteItemsForAdmin() {
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        mockUserContext(context);

        assertThat(dataStorageApiService.deleteDataStorageItems(ID, dataStorageItemVOList, true))
                .isEqualTo(TEST_INT);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteItemsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.deleteDataStorageItems(ID, dataStorageItemVOList, true))
                .isEqualTo(TEST_INT);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteItemsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.deleteDataStorageItems(ID, dataStorageItemVOList, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteItemsWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.deleteDataStorageItems(ID, dataStorageItemVOList, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteItemsOwnerForAdmin() {
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        mockUserContext(context);

        assertThat(dataStorageApiService.deleteDataStorageItemsOwner(ID, dataStorageItemVOList, true))
                .isEqualTo(TEST_INT);
    }

    @Test
    @WithMockUser
    public void shouldDeleteItemsOwnerWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService.deleteDataStorageItemsOwner(ID, dataStorageItemVOList, true))
                .isEqualTo(TEST_INT);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteItemsOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.deleteDataStorageItemsOwner(ID, dataStorageItemVOList, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteItemsOwnerWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.deleteDataStorageItemsOwner(ID, dataStorageItemVOList, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateItemUrlForAdmin() {
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        mockUserContext(context);

        assertThat(dataStorageApiService.generateDataStorageItemUrl(
                ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateItemUrlWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrl(
                ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUrlWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUrlWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.READ);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateItemUrlOwnerForAdmin() {
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        mockUserContext(context);

        assertThat(dataStorageApiService.generateDataStorageItemUrlOwner(
                ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser
    public void shouldGenerateItemUrlOwnerWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrlOwner(
                ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUrlOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrlOwner(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUrlOwnerWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrlOwner(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateFileUrlsForAdmin() {
        doReturn(downloadFileUrlList).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, testList, testList, ID);
        mockUserContext(context);

        assertThat(dataStorageApiService.generateDataStorageItemUrl(ID, testList, testList, ID))
                .isEqualTo(downloadFileUrlList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateFileUrlsWhenPermissionIsGranted() {
        final List<String> testPermissionList = Collections.singletonList("READ");
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(downloadFileUrlList).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, testList, testPermissionList, ID);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrl(ID, testList, testPermissionList, ID))
                .isEqualTo(downloadFileUrlList);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateFileUrlsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrlList).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, testList, testList, ID);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrl(ID, testList, testList, ID));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateFileUrlsWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.READ);
        doReturn(downloadFileUrlList).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, testList, testList, ID);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrl(ID, testList, testList, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateItemUploadUrlOwnerForAdmin() {
        doReturn(downloadFileUrl).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, TEST_STRING);
        mockUserContext(context);

        assertThat(dataStorageApiService.generateDataStorageItemUploadUrl(
                ID, TEST_STRING)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateItemUploadUrlOwnerWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(downloadFileUrl).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.generateDataStorageItemUploadUrl(
                ID, TEST_STRING)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUploadUrlOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrl).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUploadUrl(ID, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUploadUrlOwnerWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(downloadFileUrl).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUploadUrl(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateFileUploadUrlsForAdmin() {
        doReturn(downloadFileUrlList).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, testList);
        mockUserContext(context);

        assertThat(dataStorageApiService.generateDataStorageItemUploadUrl(ID, testList))
                .isEqualTo(downloadFileUrlList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateFileUploadUrlsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(downloadFileUrlList).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, testList);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.generateDataStorageItemUploadUrl(ID, testList))
                .isEqualTo(downloadFileUrlList);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateFileUploadUrlsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrlList).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, testList);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUploadUrl(ID, testList));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateFileUploadUrlsWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(downloadFileUrlList).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, testList);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUploadUrl(ID, testList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRestoreFileVersionForAdmin() {
        final DataStorageApiService mockApiService = mock(DataStorageApiService.class);
        doNothing().when(mockDataStorageManager).restoreVersion(ID, TEST_STRING, TEST_STRING);
        mockUserContext(context);

        mockApiService.restoreFileVersion(ID, TEST_STRING, TEST_STRING);

        verify(mockApiService).restoreFileVersion(ID, TEST_STRING, TEST_STRING);
    }

    @Test
    @WithMockUser
    public void shouldRestoreFileVersionWhenPermissionIsGranted() {
        final DataStorageApiService mockApiService = mock(DataStorageApiService.class);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doNothing().when(mockDataStorageManager).restoreVersion(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        mockApiService.restoreFileVersion(ID, TEST_STRING, TEST_STRING);

        verify(mockApiService).restoreFileVersion(ID, TEST_STRING, TEST_STRING);
    }

    @Test
    @WithMockUser
    public void shouldDenyRestoreFileVersionWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doNothing().when(mockDataStorageManager).restoreVersion(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .restoreFileVersion(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyRestoreFileVersionWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doNothing().when(mockDataStorageManager).restoreVersion(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .restoreFileVersion(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateObjectTagsForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true);
        mockUserContext(context);

        assertThat(dataStorageApiService.updateDataStorageObjectTags(
                ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldUpdateObjectTagsWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService.updateDataStorageObjectTags(
                ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateObjectTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateObjectTagsWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadObjectTagsOwnerForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        mockUserContext(context);

        assertThat(dataStorageApiService
                .loadDataStorageObjectTagsOwner(ID, TEST_STRING, TEST_STRING)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldLoadObjectTagsOwnerWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService
                .loadDataStorageObjectTagsOwner(ID, TEST_STRING, TEST_STRING)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadObjectTagsOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .loadDataStorageObjectTagsOwner(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadObjectTagsOwnerWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .loadDataStorageObjectTagsOwner(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadObjectTagsForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        mockUserContext(context);

        assertThat(dataStorageApiService.loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadObjectTagsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadObjectTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadObjectTagsWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.READ);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteObjectTagsForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING, TEST_STRING_SET);
        mockUserContext(context);

        assertThat(dataStorageApiService.deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING, TEST_STRING_SET))
                .isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDeleteObjectTagsWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING, TEST_STRING_SET);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService.deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING, TEST_STRING_SET))
                .isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteObjectTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING, TEST_STRING_SET);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING, TEST_STRING_SET));
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteObjectTagsWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING, TEST_STRING_SET);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING, TEST_STRING_SET));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetItemWithTagsForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        mockUserContext(context);

        assertThat(dataStorageApiService.getDataStorageItemWithTags(ID, TEST_STRING, true))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetItemWithTagsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.getDataStorageItemWithTags(ID, TEST_STRING, true))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemWithTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemWithTags(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemWithTagsWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.READ);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemWithTags(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetItemOwnerWithTagsForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        mockUserContext(context);

        assertThat(dataStorageApiService.getDataStorageItemOwnerWithTags(ID, TEST_STRING, true))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldGetItemOwnerWithTagsWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService.getDataStorageItemOwnerWithTags(ID, TEST_STRING, true))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemOwnerWithTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemOwnerWithTags(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemOwnerWithTagsWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemOwnerWithTags(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetItemContentOwnerForAdmin() {
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        mockUserContext(context);

        assertThat(dataStorageApiService.getDataStorageItemContentOwner(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageItemContent);
    }

    @Test
    @WithMockUser
    public void shouldGetItemContentOwnerWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService.getDataStorageItemContentOwner(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageItemContent);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemContentOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemContentOwner(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemContentOwnerWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemContentOwner(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetItemContentForAdmin() {
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        mockUserContext(context);

        assertThat(dataStorageApiService.getDataStorageItemContent(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageItemContent);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetItemContentWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.getDataStorageItemContent(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageItemContent);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemContentWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemContent(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemContentWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.READ);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemContent(ID, TEST_STRING, TEST_STRING));
    }


    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetDataSizesForAdmin() {
        doReturn(pathDescriptionList).when(mockDataStorageManager).getDataSizes(TEST_STRING_LIST);

        assertThat(dataStorageApiService.getDataSizes(TEST_STRING_LIST)).isEqualTo(pathDescriptionList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetDataSizesWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(pathDescriptionList).when(mockDataStorageManager).getDataSizes(TEST_STRING_LIST);
        doReturn(s3bucket).when(mockEntityManager).load(eq(AclClass.DATA_STORAGE), anyLong());
        mockAuthUser(SIMPLE_USER);

        assertThat(dataStorageApiService.getDataSizes(TEST_STRING_LIST)).isEqualTo(pathDescriptionList);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataSizesWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(pathDescriptionList).when(mockDataStorageManager).getDataSizes(TEST_STRING_LIST);
        doReturn(s3bucket).when(mockEntityManager).load(eq(AclClass.DATA_STORAGE), anyLong());
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataSizes(TEST_STRING_LIST));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetStreamingContentForAdmin() {
        doReturn(dataStorageStreamingContent).
                when(mockDataStorageManager).getStreamingContent(ID, TEST_STRING, TEST_STRING);
        mockUserContext(context);

        assertThat(dataStorageApiService.getStreamingContent(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageStreamingContent);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetStreamingContentWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageStreamingContent).
                when(mockDataStorageManager).getStreamingContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.getStreamingContent(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageStreamingContent);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetStreamingContentWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageStreamingContent).
                when(mockDataStorageManager).getStreamingContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getStreamingContent(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetStreamingContentWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.READ);
        doReturn(dataStorageStreamingContent).
                when(mockDataStorageManager).getStreamingContent(ID, TEST_STRING, TEST_STRING);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getStreamingContent(ID, TEST_STRING, TEST_STRING));
    }
}

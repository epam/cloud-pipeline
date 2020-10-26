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
import com.epam.pipeline.controller.vo.security.EntityWithPermissionVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StorageMountPath;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.DataStorageRuleManager;
import com.epam.pipeline.manager.datastorage.RunMountService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_ARRAY;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_MAP;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_SET;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DataStorageApiServiceTest extends AbstractAclTest {

    private final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER);
    private final AbstractDataStorage anotherS3bucket =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID_2, TEST_STRING);
    private final DataStorageWithShareMount storageShareMount = DatastorageCreatorUtils.getDataStorageWithShareMount();
    private final Authentication authentication = new TestingAuthenticationToken(new Object(), new Object());
    private final UserContext context = SecurityCreatorUtils.getUserContext();
    private final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
    private final AbstractDataStorageItem dataStorageFile = DatastorageCreatorUtils.getDataStorageFile();
    private final InputStream inputStream = new ByteArrayInputStream(TEST_STRING.getBytes());
    private final DataStorageDownloadFileUrl downloadFileUrl = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
    private final Pipeline pipeline = PipelineCreatorUtils.getPipeline(OWNER_USER);
    private final DataStorageRule dataStorageRule = DatastorageCreatorUtils.getDataStorageRule();
    private final DataStorageItemContent dataStorageItemContent =
            DatastorageCreatorUtils.getDefaultDataStorageItemContent();
    private final DataStorageStreamingContent dataStorageStreamingContent =
            DatastorageCreatorUtils.getDefaultDataStorageStreamingContent(inputStream);
    private final List<PathDescription> pathDescriptionList = DatastorageCreatorUtils.getPathDescriptionList();
    private final StorageUsage storageUsage = DatastorageCreatorUtils.getStorageUsage();
    private final StorageMountPath storageMountPath = DatastorageCreatorUtils.getStorageMountPath();

    private final List<String> testList = Collections.singletonList(TEST_STRING);
    private final List<UpdateDataStorageItemVO> dataStorageItemVOList =
            DatastorageCreatorUtils.getUpdateDataStorageItemVOList();
    private final List<DataStorageFile> dataStorageFileList = DatastorageCreatorUtils.getDataStorageFileList();
    private final List<DataStorageDownloadFileUrl> downloadFileUrlList =
            DatastorageCreatorUtils.getDataStorageDownloadFileUrlList();
    private final List<DataStorageRule> dataStorageRuleList = DatastorageCreatorUtils.getDataStorageRuleList();

    @Autowired
    private DataStorageApiService dataStorageApiService;

    @Autowired
    private GrantPermissionManager grantPermissionManager;

    @Autowired
    private DataStorageManager mockDataStorageManager;

    @Autowired
    private DataStorageRuleManager mockDataStorageRuleManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private RunMountService mockRunMountService;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnDataStoragesForAdmin() {
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getDataStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnDataStoragesWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getDataStorages();

        assertThat(dataStorageApiService.getDataStorages()).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnDataStoragesWhichPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        initAclEntity(anotherS3bucket, AclPermission.NO_READ);
        doReturn(mutableListOf(s3bucket, anotherS3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getDataStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser
    public void shouldReturnEmptyDataStorageListWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.NO_READ);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getDataStorages()).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnWritableDataStoragesForAdmin() {
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getWritableStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnWritableDataStoragesWhenPermissionIsGranted() {
        initAclEntity(s3bucket, Arrays.asList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getWritableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnWritableDataStoragesWhichPermissionIsGranted() {
        initAclEntity(s3bucket, Arrays.asList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        initAclEntity(anotherS3bucket, Arrays.asList(new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(mutableListOf(s3bucket, anotherS3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getWritableStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser
    public void shouldReturnEmptyWritableDataStorageListWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket, Arrays.asList(new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.NO_WRITE.getMask())));
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getWritableStorages()).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnAvailableStoragesWithShareMountForAdmin() {
        doReturn(mutableListOf(storageShareMount)).when(mockDataStorageManager).getDataStoragesWithShareMountObject(ID);

        assertThat(dataStorageApiService.getAvailableStoragesWithShareMount(ID)).hasSize(1).contains(storageShareMount);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWithShareMountWhenReadPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(mutableListOf(storageShareMount)).when(mockDataStorageManager).getDataStoragesWithShareMountObject(ID);

        assertThat(dataStorageApiService.getAvailableStoragesWithShareMount(ID)).hasSize(1).contains(storageShareMount);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWithShareMountWhenWritePermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(mutableListOf(storageShareMount)).when(mockDataStorageManager).getDataStoragesWithShareMountObject(ID);

        assertThat(dataStorageApiService.getAvailableStoragesWithShareMount(ID)).hasSize(1).contains(storageShareMount);
    }

    @Test
    @WithMockUser
    public void shouldReturnEmptyAvailableStoragesWithShareMountWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(mutableListOf(storageShareMount)).when(mockDataStorageManager).getDataStoragesWithShareMountObject(ID);

        assertThat(dataStorageApiService.getAvailableStoragesWithShareMount(ID)).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnAvailableStoragesForAdmin() {
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getAvailableStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWhenReadPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWhenWritePermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWhichPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        initAclEntity(anotherS3bucket);
        doReturn(mutableListOf(s3bucket, anotherS3bucket)).when(mockDataStorageManager).getDataStorages();

        List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    public void shouldReturnEmptyAvailableStoragesWhenWritePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getAvailableStorages()).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadDataStorageForAdmin() {
        final UserContext context = SecurityCreatorUtils.getUserContext();
        doReturn(s3bucket).when(mockDataStorageManager).load(ID);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.load(ID)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadDataStorageWhenPermissionIsGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext();
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(s3bucket).when(mockDataStorageManager).load(ID);
        initMocks(OWNER_USER, context);

        AbstractDataStorage returnedDataStorage = dataStorageApiService.load(ID);

        assertThat(returnedDataStorage).isEqualTo(s3bucket);
        assertThat(returnedDataStorage.getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadDataStorageWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.NO_READ);
        doReturn(s3bucket).when(mockDataStorageManager).load(ID);
        mockS3bucket();
        mockAuthUser(SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.load(ID));
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadDataStorageWhenCheckStorageSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(s3bucket).when(mockDataStorageManager).load(ID);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.load(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadByNameOrIdForAdmin() {
        doReturn(s3bucket).when(mockDataStorageManager).loadByNameOrId(TEST_STRING);

        assertThat(dataStorageApiService.loadByNameOrId(TEST_STRING)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadByNameOrIdWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(s3bucket).when(mockDataStorageManager).loadByNameOrId(TEST_STRING);

        AbstractDataStorage returnedDataStorage = dataStorageApiService.loadByNameOrId(TEST_STRING);

        assertThat(returnedDataStorage).isEqualTo(s3bucket);
        assertThat(returnedDataStorage.getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingByNameOrIdWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.NO_READ);
        doReturn(s3bucket).when(mockDataStorageManager).loadByNameOrId(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.loadByNameOrId(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadByPathOrIdForAdmin() {
        doReturn(s3bucket).when(mockDataStorageManager).loadByPathOrId(TEST_STRING);

        assertThat(dataStorageApiService.loadByPathOrId(TEST_STRING)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadByPathOrIdWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(s3bucket).when(mockDataStorageManager).loadByPathOrId(TEST_STRING);

        AbstractDataStorage returnedDataStorage = dataStorageApiService.loadByPathOrId(TEST_STRING);

        assertThat(returnedDataStorage).isEqualTo(s3bucket);
        assertThat(returnedDataStorage.getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingByPathOrIdWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.NO_READ);
        doReturn(s3bucket).when(mockDataStorageManager).loadByPathOrId(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.loadByPathOrId(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetDataStorageItemsForAdmin() {
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initMocks(OWNER_USER, context);

        DataStorageListing returnedDataStorageListing =
                dataStorageApiService.getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);

        assertThat(returnedDataStorageListing).isEqualTo(dataStorageListing);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetDataStorageItemsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItems(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING)).isEqualTo(dataStorageListing);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataStorageItemsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.NO_READ);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataStorageItemsWhenCheckStorageSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetDataStorageItemsOwnerForAdmin() {
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemsOwner(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING)).isEqualTo(dataStorageListing);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetDataStorageItemsOwnerWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemsOwner(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING)).isEqualTo(dataStorageListing);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataStorageItemsOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataStorageItemsOwner(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetDataStorageItemsOwnerWhenCheckStorageSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(dataStorageListing).when(mockDataStorageManager)
                .getDataStorageItems(ID, TEST_STRING, true, TEST_INT, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataStorageItemsOwner(
                ID, TEST_STRING, true, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDataStorageItemsForAdmin() {
        doReturn(dataStorageFileList).when(mockDataStorageManager).updateDataStorageItems(ID, dataStorageItemVOList);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.updateDataStorageItems(ID, dataStorageItemVOList))
                .isEqualTo(dataStorageFileList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateDataStorageItemsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFileList).when(mockDataStorageManager).updateDataStorageItems(ID, dataStorageItemVOList);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.updateDataStorageItems(ID, dataStorageItemVOList))
                .isEqualTo(dataStorageFileList);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDataStorageItemsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFileList).when(mockDataStorageManager).updateDataStorageItems(ID, dataStorageItemVOList);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.updateDataStorageItems(ID, dataStorageItemVOList));
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDataStorageItemsWhenCheckStorageSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFileList).when(mockDataStorageManager).updateDataStorageItems(ID, dataStorageItemVOList);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.updateDataStorageItems(ID, dataStorageItemVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateFileForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateFileWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY));
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, TEST_ARRAY));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateFileThroughInputStreamForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateFileThroughInputStreamWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileThroughInputStreamWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream));
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileThroughInputStreamWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager)
                .createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_STRING, inputStream));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateFileThroughPathForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager).createDataStorageFile(ID, TEST_STRING, TEST_ARRAY);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_ARRAY))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateFileThroughPathWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager).createDataStorageFile(ID, TEST_STRING, TEST_ARRAY);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_ARRAY))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileThroughPathWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager).createDataStorageFile(ID, TEST_STRING, TEST_ARRAY);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_ARRAY));
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateFileThroughPathWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(dataStorageFile).when(mockDataStorageManager).createDataStorageFile(ID, TEST_STRING, TEST_ARRAY);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.createDataStorageFile(ID, TEST_STRING, TEST_ARRAY));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteItemsForAdmin() {
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.deleteDataStorageItems(ID, dataStorageItemVOList, true))
                .isEqualTo(TEST_INT);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteItemsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.deleteDataStorageItems(ID, dataStorageItemVOList, true))
                .isEqualTo(TEST_INT);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteItemsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.deleteDataStorageItems(ID, dataStorageItemVOList, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteItemsWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.deleteDataStorageItems(ID, dataStorageItemVOList, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteItemsOwnerForAdmin() {
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.deleteDataStorageItemsOwner(ID, dataStorageItemVOList, true))
                .isEqualTo(TEST_INT);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteItemsOwnerWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.deleteDataStorageItemsOwner(ID, dataStorageItemVOList, true))
                .isEqualTo(TEST_INT);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteItemsOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.deleteDataStorageItemsOwner(ID, dataStorageItemVOList, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteItemsOwnerWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(TEST_INT).when(mockDataStorageManager).deleteDataStorageItems(ID, dataStorageItemVOList, true);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.deleteDataStorageItemsOwner(ID, dataStorageItemVOList, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateItemUrlForAdmin() {
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrl(
                ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateItemUrlWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrl(
                ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUrlWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUrlWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateItemUrlOwnerForAdmin() {
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrlOwner(
                ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateItemUrlOwnerWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrlOwner(
                ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUrlOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrlOwner(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUrlOwnerWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(downloadFileUrl).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrlOwner(ID, TEST_STRING, TEST_STRING, ContentDisposition.INLINE));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateFileUrlsForAdmin() {
        doReturn(downloadFileUrlList).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, testList, testList, ID);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrl(ID, testList, testList, ID))
                .isEqualTo(downloadFileUrlList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateFileUrlsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(downloadFileUrlList).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, testList, testList, ID);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUrl(ID, testList, testList, ID))
                .isEqualTo(downloadFileUrlList);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateFileUrlsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrlList).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, testList, testList, ID);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrl(ID, testList, testList, ID));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateFileUrlsWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(downloadFileUrlList).when(mockDataStorageManager)
                .generateDataStorageItemUrl(ID, testList, testList, ID);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUrl(ID, testList, testList, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateItemUploadUrlOwnerForAdmin() {
        doReturn(downloadFileUrl).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUploadUrl(
                ID, TEST_STRING)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateItemUploadUrlOwnerWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(downloadFileUrl).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUploadUrl(
                ID, TEST_STRING)).isEqualTo(downloadFileUrl);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUploadUrlOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrl).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUploadUrl(ID, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateItemUploadUrlOwnerWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(downloadFileUrl).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUploadUrl(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateFileUploadUrlsForAdmin() {
        doReturn(downloadFileUrlList).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, testList);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUploadUrl(ID, testList))
                .isEqualTo(downloadFileUrlList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateFileUploadUrlsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(downloadFileUrlList).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, testList);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.generateDataStorageItemUploadUrl(ID, testList))
                .isEqualTo(downloadFileUrlList);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateFileUploadUrlsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(downloadFileUrlList).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, testList);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUploadUrl(ID, testList));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateFileUploadUrlsWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(downloadFileUrlList).when(mockDataStorageManager).generateDataStorageItemUploadUrl(ID, testList);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .generateDataStorageItemUploadUrl(ID, testList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRestoreFileVersionForAdmin() {
        final DataStorageApiService mockApiService = mock(DataStorageApiService.class);
        doNothing().when(mockDataStorageManager).restoreVersion(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);
        mockApiService.restoreFileVersion(ID, TEST_STRING, TEST_STRING);

        verify(mockApiService, times(1)).restoreFileVersion(ID, TEST_STRING, TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRestoreFileVersionWhenPermissionIsGranted() {
        final DataStorageApiService mockApiService = mock(DataStorageApiService.class);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doNothing().when(mockDataStorageManager).restoreVersion(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);
        mockApiService.restoreFileVersion(ID, TEST_STRING, TEST_STRING);

        verify(mockApiService, times(1)).restoreFileVersion(ID, TEST_STRING, TEST_STRING);
    }

    @Test
    @WithMockUser
    public void shouldDenyRestoreFileVersionWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doNothing().when(mockDataStorageManager).restoreVersion(ID, TEST_STRING, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .restoreFileVersion(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyRestoreFileVersionWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doNothing().when(mockDataStorageManager).restoreVersion(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .restoreFileVersion(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRulesForAdmin() {
        doReturn(dataStorageRuleList).when(mockDataStorageRuleManager).loadRules(ID, TEST_STRING);

        assertThat(dataStorageApiService.loadRules(ID, TEST_STRING)).isEqualTo(dataStorageRuleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadRulesWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(dataStorageRuleList).when(mockDataStorageRuleManager).loadRules(ID, TEST_STRING);

        assertThat(dataStorageApiService.loadRules(ID, TEST_STRING)).isEqualTo(dataStorageRuleList);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadRulesWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(dataStorageRuleList).when(mockDataStorageRuleManager).loadRules(ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.loadRules(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteRuleForAdmin() {
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).deleteRule(ID, TEST_STRING);

        assertThat(dataStorageApiService.deleteRule(ID, TEST_STRING)).isEqualTo(dataStorageRule);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteRuleWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).deleteRule(ID, TEST_STRING);

        assertThat(dataStorageApiService.deleteRule(ID, TEST_STRING)).isEqualTo(dataStorageRule);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteRuleWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).deleteRule(ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.deleteRule(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateObjectTagsForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.updateDataStorageObjectTags(
                ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateObjectTagsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.updateDataStorageObjectTags(
                ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateObjectTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateObjectTagsWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .updateDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_MAP, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadObjectTagsOwnerForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService
                .loadDataStorageObjectTagsOwner(ID, TEST_STRING, TEST_STRING)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadObjectTagsOwnerWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService
                .loadDataStorageObjectTagsOwner(ID, TEST_STRING, TEST_STRING)).isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadObjectTagsOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .loadDataStorageObjectTagsOwner(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadObjectTagsOwnerWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .loadDataStorageObjectTagsOwner(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadObjectTagsForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadObjectTagsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadObjectTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadObjectTagsWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager).loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .loadDataStorageObjectTags(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteObjectTagsForAdmin() {
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_SET, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_SET, TEST_STRING))
                .isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteObjectTagsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_SET, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_SET, TEST_STRING))
                .isEqualTo(TEST_STRING_MAP);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteObjectTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_SET, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_SET, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteObjectTagsWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(TEST_STRING_MAP).when(mockDataStorageManager)
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_SET, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .deleteDataStorageObjectTags(ID, TEST_STRING, TEST_STRING_SET, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetItemWithTagsForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemWithTags(ID, TEST_STRING, true))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetItemWithTagsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemWithTags(ID, TEST_STRING, true))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemWithTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemWithTags(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemWithTagsWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemWithTags(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetItemOwnerWithTagsForAdmin() {
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemOwnerWithTags(ID, TEST_STRING, true))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetItemOwnerWithTagsWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemOwnerWithTags(ID, TEST_STRING, true))
                .isEqualTo(dataStorageFile);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemOwnerWithTagsWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemOwnerWithTags(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemOwnerWithTagsWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(dataStorageFile).when(mockDataStorageManager).getDataStorageItemWithTags(ID, TEST_STRING, true);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemOwnerWithTags(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetItemContentOwnerForAdmin() {
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemContentOwner(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageItemContent);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetItemContentOwnerWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemContentOwner(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageItemContent);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemContentOwnerWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemContentOwner(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemContentOwnerWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemContentOwner(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetItemContentForAdmin() {
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemContent(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageItemContent);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetItemContentWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageItemContent(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageItemContent);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemContentWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemContent(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetItemContentWhenSharedPermissionIsNotGranted() {
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageItemContent).
                when(mockDataStorageManager).getDataStorageItemContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getDataStorageItemContent(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetStreamingContentForAdmin() {
        doReturn(dataStorageStreamingContent).
                when(mockDataStorageManager).getStreamingContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getStreamingContent(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageStreamingContent);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetStreamingContentWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageStreamingContent).
                when(mockDataStorageManager).getStreamingContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getStreamingContent(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(dataStorageStreamingContent);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetStreamingContentWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(dataStorageStreamingContent).
                when(mockDataStorageManager).getStreamingContent(ID, TEST_STRING, TEST_STRING);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getStreamingContent(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetStreamingContentWhenSharedPermissionIsNotGranted() {
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(dataStorageStreamingContent).
                when(mockDataStorageManager).getStreamingContent(ID, TEST_STRING, TEST_STRING);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService
                .getStreamingContent(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetSharedLinkForAdmin() {
        doReturn(TEST_STRING).when(mockDataStorageManager).generateSharedUrlForStorage(ID);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageSharedLink(ID)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetSharedLinkWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(TEST_STRING).when(mockDataStorageManager).generateSharedUrlForStorage(ID);
        initMocks(OWNER_USER, context);

        assertThat(dataStorageApiService.getDataStorageSharedLink(ID)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetSharedLinkWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING).when(mockDataStorageManager).generateSharedUrlForStorage(ID);
        initMocks(SIMPLE_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataStorageSharedLink(ID));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetSharedLinkWhenSharedPermissionIsNotGranted() {
        final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER, false);
        final UserContext context = SecurityCreatorUtils.getUserContext(true);
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(TEST_STRING).when(mockDataStorageManager).generateSharedUrlForStorage(ID);
        initMocks(OWNER_USER, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataStorageSharedLink(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetStoragePermissionForAdmin() {
        EntityWithPermissionVO entityWithPermissionVO = grantPermissionManager.loadAllEntitiesPermissions(
                AclClass.DATA_STORAGE, TEST_INT, TEST_INT, true, TEST_INT);

        assertThat(dataStorageApiService.getStoragePermission(TEST_INT, TEST_INT, TEST_INT))
                .isEqualTo(entityWithPermissionVO);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetStoragePermissionForAdmin() {
        grantPermissionManager.loadAllEntitiesPermissions(
                AclClass.DATA_STORAGE, TEST_INT, TEST_INT, true, TEST_INT);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.getStoragePermission(TEST_INT, TEST_INT, TEST_INT));
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
        mockAuthUser(OWNER_USER);

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
    public void shouldGetStorageUsageForAdmin() {
        doReturn(storageUsage).when(mockDataStorageManager).getStorageUsage(TEST_STRING, TEST_STRING);

        assertThat(dataStorageApiService.getStorageUsage(TEST_STRING, TEST_STRING)).isEqualTo(storageUsage);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetStorageUsageWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(storageUsage).when(mockDataStorageManager).getStorageUsage(TEST_STRING, TEST_STRING);
        doReturn(s3bucket).when(mockEntityManager).loadByNameOrId(eq(AclClass.DATA_STORAGE), anyString());
        mockAuthUser(OWNER_USER);

        assertThat(dataStorageApiService.getStorageUsage(TEST_STRING, TEST_STRING)).isEqualTo(storageUsage);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetStorageUsageWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(storageUsage).when(mockDataStorageManager).getStorageUsage(TEST_STRING, TEST_STRING);
        doReturn(s3bucket).when(mockEntityManager).loadByNameOrId(eq(AclClass.DATA_STORAGE), anyString());
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.getStorageUsage(TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetSharedFSSPathForAdmin() {
        doReturn(storageMountPath).when(mockRunMountService).getSharedFSSPathForRun(ID, true);

        assertThat(dataStorageApiService.getSharedFSSPathForRun(ID, true)).isEqualTo(storageMountPath);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetSharedFSSPathWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        doReturn(storageUsage).when(mockDataStorageManager).getStorageUsage(TEST_STRING, TEST_STRING);
        doReturn(s3bucket).when(mockEntityManager).loadByNameOrId(eq(AclClass.DATA_STORAGE), anyString());
        mockAuthUser(OWNER_USER);

        assertThat(dataStorageApiService.getStorageUsage(TEST_STRING, TEST_STRING)).isEqualTo(storageUsage);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetSharedFSSPathWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(storageUsage).when(mockDataStorageManager).getStorageUsage(TEST_STRING, TEST_STRING);
        doReturn(s3bucket).when(mockEntityManager).loadByNameOrId(eq(AclClass.DATA_STORAGE), anyString());
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.getStorageUsage(TEST_STRING, TEST_STRING));
    }

    private void initMocks(String user, UserContext context) {
        mockAuthUser(user);
        mockS3bucket();
        doReturn(context).when(mockAuthManager).getUserContext();
    }

    private void mockAuthUser(String user) {
        doReturn(user).when(mockAuthManager).getAuthorizedUser();
        doReturn(authentication).when(mockAuthManager).getAuthentication();
    }

    private void mockS3bucket() {
        doReturn(s3bucket).when(mockEntityManager).load(AclClass.DATA_STORAGE, ID);
    }
}

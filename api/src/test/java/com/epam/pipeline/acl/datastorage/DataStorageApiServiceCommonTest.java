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

package com.epam.pipeline.acl.datastorage;

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.security.EntityWithPermissionVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequest;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestAction;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestType;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.StorageMountPath;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.RunMountService;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.AopTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class DataStorageApiServiceCommonTest extends AbstractDataStorageAclTest {

    private static final int READ_PERMISSION = 1;
    private static final int WRITE_PERMISSION = 2;
    private static final int READ_AND_WRITE_PERMISSION = 3;
    private static final int ALL_PERMISSIONS = 15;
    private static final DataStorageConvertRequest CONVERT_REQUEST = new DataStorageConvertRequest(
            DataStorageConvertRequestType.VERSIONED_STORAGE, DataStorageConvertRequestAction.LEAVE);
    public static final long SECS_IN_HOUR = 3600L;

    private final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
    private final DataStorageWithShareMount storageShareMount =
            DatastorageCreatorUtils.getDefaultDataStorageWithShareMount();
    private final TemporaryCredentials temporaryCredentials = DatastorageCreatorUtils.getTemporaryCredentials();
    private final StorageUsage storageUsage = DatastorageCreatorUtils.getStorageUsage();
    private final StorageMountPath storageMountPath = DatastorageCreatorUtils.getDefaultStorageMountPath();
    private final PipelineRun pipelinerun = PipelineCreatorUtils.getPipelineRun(ID, SIMPLE_USER);
    private final List<DataStorageAction> dataStorageActionList = DatastorageCreatorUtils.getDataStorageActionList();

    @Autowired
    private GrantPermissionManager grantPermissionManager;

    @Autowired
    private PipelineRunCRUDService mockRunCRUDService;

    @Autowired
    private RunMountService mockRunMountService;

    @Autowired
    private TemporaryCredentialsManager mockTemporaryCredentialsManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnDataStoragesForAdmin() {
        mockSecurityContext();
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();
        assertThat(dataStorageApiService.getDataStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnDataStoragesWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getDataStorages();

        assertThat(dataStorageApiService.getDataStorages()).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldAbleToRequestDavMountWhenPermissionIsGranted() {
        initAclEntity(s3bucket, new AclPermission(AclPermission.WRITE.getMask() | AclPermission.READ.getMask()));
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        dataStorageApiService.requestDataStorageDavMount(s3bucket.getId(), SECS_IN_HOUR);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotAbleToRequestDavMountWhenOnlyReadIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        dataStorageApiService.requestDataStorageDavMount(s3bucket.getId(), SECS_IN_HOUR);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldAbleToCallOffRequestDavMountWhenPermissionIsGranted() {
        initAclEntity(s3bucket, new AclPermission(AclPermission.WRITE.getMask() | AclPermission.READ.getMask()));
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        dataStorageApiService.callOffDataStorageDavMount(s3bucket.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotBeAbleToCallOffRequestDavMountWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.NO_READ);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        dataStorageApiService.callOffDataStorageDavMount(s3bucket.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotBeAbleToCallOffRequestDavMountWhenOnlyReadIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        dataStorageApiService.callOffDataStorageDavMount(s3bucket.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotAbleToRequestDavMountWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.NO_READ);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        dataStorageApiService.requestDataStorageDavMount(s3bucket.getId(), SECS_IN_HOUR);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnDataStoragesWhichPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        initAclEntity(anotherS3bucket);
        mockStorage(anotherS3bucket);
        doReturn(mutableListOf(s3bucket, anotherS3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getDataStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnEmptyDataStorageListWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getDataStorages()).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnWritableDataStoragesForAdmin() {
        mockSecurityContext();
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();
        assertThat(dataStorageApiService.getWritableStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnWritableDataStoragesWhenPermissionIsGranted() {
        initAclEntity(s3bucket, Arrays.asList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask()),
                                              new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getWritableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(READ_AND_WRITE_PERMISSION);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnFullPermissionOnDataStoragesForAdminWhenMountStatusDisabled() {
        mockSecurityContext();
        final NFSDataStorage nfsDataStorage =
            DatastorageCreatorUtils.getNfsDataStorage(NFSStorageMountStatus.MOUNT_DISABLED, OWNER_USER);
        doReturn(mutableListOf(nfsDataStorage)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getDataStorages();
        assertThat(returnedDataStorages).hasSize(1).contains(nfsDataStorage);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(ALL_PERMISSIONS);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldNotModifyMaskForDataStorageWithShareWhenAdminAndMountStatusDisabled() {
        final NFSDataStorage nfsDataStorage =
            DatastorageCreatorUtils.getNfsDataStorage(NFSStorageMountStatus.MOUNT_DISABLED, OWNER_USER);
        final DataStorageWithShareMount storageWithShareMount =
            new DataStorageWithShareMount(nfsDataStorage, new FileShareMount());
        doReturn(mutableListOf(storageWithShareMount)).when(mockDataStorageManager)
            .getDataStoragesWithShareMountObject(eq(ID));

        final List<DataStorageWithShareMount> returnedDataStorages =
            dataStorageApiService.getAvailableStoragesWithShareMount(ID);
        assertThat(returnedDataStorages).hasSize(1);
        assertThat(returnedDataStorages.get(0).getStorage().getMask()).isEqualTo(ALL_PERMISSIONS);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnWritableDataStoragesWhichPermissionIsGranted() {
        initAclEntity(s3bucket, Arrays.asList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        initAclEntity(anotherS3bucket, Arrays.asList(new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        mockStorage(anotherS3bucket);
        doReturn(mutableListOf(s3bucket, anotherS3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getWritableStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnEmptyWritableDataStorageListWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket, Arrays.asList(new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.NO_WRITE.getMask())));
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
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
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(storageShareMount)).when(mockDataStorageManager).getDataStoragesWithShareMountObject(ID);

        assertThat(dataStorageApiService.getAvailableStoragesWithShareMount(ID)).hasSize(1).contains(storageShareMount);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWithShareMountWhenWritePermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(storageShareMount)).when(mockDataStorageManager).getDataStoragesWithShareMountObject(ID);

        assertThat(dataStorageApiService.getAvailableStoragesWithShareMount(ID)).hasSize(1).contains(storageShareMount);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnAvailableStoragesForAdmin() {
        mockSecurityContext();
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getAvailableStorages()).hasSize(1).contains(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWhenReadPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWhenWritePermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(WRITE_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWhichPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        initAclEntity(anotherS3bucket);
        mockStorage(anotherS3bucket);
        doReturn(mutableListOf(s3bucket, anotherS3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnEmptyAvailableStoragesWhenWritePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getAvailableStorages()).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadDataStorageForAdmin() {
        doReturn(s3bucket).when(mockDataStorageManager).load(ID);
        mockUserContext(context);

        assertThat(dataStorageApiService.load(ID)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadDataStorageWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(s3bucket).when(mockDataStorageManager).load(ID);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        final AbstractDataStorage returnedDataStorage = dataStorageApiService.load(ID);

        assertThat(returnedDataStorage).isEqualTo(s3bucket);
        assertThat(returnedDataStorage.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadDataStorageWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(s3bucket).when(mockDataStorageManager).load(ID);
        mockStorage(s3bucket);
        mockAuthUser(SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.load(ID));
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadDataStorageWhenCheckStorageSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.READ);
        doReturn(notSharedS3bucket).when(mockDataStorageManager).load(ID);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, externalContext);

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
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        final AbstractDataStorage returnedDataStorage = dataStorageApiService.loadByNameOrId(TEST_STRING);

        assertThat(returnedDataStorage).isEqualTo(s3bucket);
        assertThat(returnedDataStorage.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingByNameOrIdWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
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
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(s3bucket).when(mockDataStorageManager).loadByPathOrId(TEST_STRING);

        final AbstractDataStorage returnedDataStorage = dataStorageApiService.loadByPathOrId(TEST_STRING);

        assertThat(returnedDataStorage).isEqualTo(s3bucket);
        assertThat(returnedDataStorage.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingByPathOrIdWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(s3bucket).when(mockDataStorageManager).loadByPathOrId(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.loadByPathOrId(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDataStorageForAdmin() {
        initAclEntity(s3bucket);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).update(dataStorageVO);

        assertThat(dataStorageApiService.update(dataStorageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateDataStorageWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        mockStorage(s3bucket);
        mockAuthUser(SIMPLE_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).update(dataStorageVO);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.update(dataStorageVO));
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDataStorageWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        mockStorage(s3bucket);
        mockAuthUser(SIMPLE_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).update(dataStorageVO);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.update(dataStorageVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdatePolicyForAdmin() {
        doReturn(s3bucket).when(mockDataStorageManager).updatePolicy(dataStorageVO);

        assertThat(dataStorageApiService.updatePolicy(dataStorageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser
    public void shouldUpdatePolicyWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        mockStorage(notSharedS3bucket);
        mockAuthUser(SIMPLE_USER);
        doReturn(notSharedS3bucket).when(mockDataStorageManager).updatePolicy(dataStorageVO);

        assertThat(dataStorageApiService.updatePolicy(dataStorageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdatePolicyWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        mockStorage(s3bucket);
        mockAuthUser(SIMPLE_USER);
        doReturn(s3bucket).when(mockDataStorageManager).updatePolicy(dataStorageVO);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.updatePolicy(dataStorageVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteDataStorageForAdmin() {
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).delete(ID, true);

        assertThat(dataStorageApiService.delete(ID, true)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_MANAGER_ROLE)
    public void shouldDenyDeleteDataStorageWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        mockStorage(s3bucket);
        mockAuthUser(SIMPLE_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).delete(ID, true);
        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.delete(ID, true));
    }

    @Test
    @WithMockUser(roles = STORAGE_MANAGER_ROLE)
    public void shouldDenyDeleteDataStorageWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        mockStorage(s3bucket);
        mockAuthUser(SIMPLE_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).delete(ID, true);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.delete(ID, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteDataStorageWhenRoleIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        mockStorage(s3bucket);
        mockAuthUser(SIMPLE_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).delete(ID, true);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.delete(ID, true));
    }


    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetSharedLinkForAdmin() {
        doReturn(TEST_STRING).when(mockDataStorageManager).generateSharedUrlForStorage(ID);
        mockUserContext(context);

        assertThat(dataStorageApiService.getDataStorageSharedLink(ID)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetSharedLinkWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(TEST_STRING).when(mockDataStorageManager).generateSharedUrlForStorage(ID);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThat(dataStorageApiService.getDataStorageSharedLink(ID)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetSharedLinkWhenStoragePermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(TEST_STRING).when(mockDataStorageManager).generateSharedUrlForStorage(ID);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataStorageSharedLink(ID));
    }

    @Test
    @WithMockUser
    public void shouldDenyGetSharedLinkWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.WRITE);
        doReturn(TEST_STRING).when(mockDataStorageManager).generateSharedUrlForStorage(ID);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.getDataStorageSharedLink(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldConvertDataStorageForAdmin() {
        doReturn(pipeline).when(mockDataStorageConvertManager).convert(ID, CONVERT_REQUEST);
        mockUserContext(context);

        assertThat(dataStorageApiService.convert(ID, CONVERT_REQUEST)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser
    public void shouldConvertDataStorageWhenPermissionIsGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(pipeline).when(mockDataStorageConvertManager).convert(ID, CONVERT_REQUEST);
        initUserAndEntityMocks(SIMPLE_USER, notSharedS3bucket, context);

        assertThat(dataStorageApiService.convert(ID, CONVERT_REQUEST)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser
    public void shouldDenyConvertDataStorageWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(pipeline).when(mockDataStorageConvertManager).convert(ID, CONVERT_REQUEST);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.convert(ID, CONVERT_REQUEST));
    }

    @Test
    @WithMockUser
    public void shouldDenyConvertDataStorageWhenSharedPermissionIsNotGranted() {
        initAclEntity(notSharedS3bucket, AclPermission.OWNER);
        doReturn(pipeline).when(mockDataStorageConvertManager).convert(ID, CONVERT_REQUEST);
        initUserAndEntityMocks(ANOTHER_SIMPLE_USER, notSharedS3bucket, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.convert(ID, CONVERT_REQUEST));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetStoragePermissionForAdmin() {
        final EntityWithPermissionVO entityWithPermissionVO = grantPermissionManager.loadAllEntitiesPermissions(
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
        mockAuthUser(SIMPLE_USER);

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
    @WithMockUser
    public void shouldGetSharedFSSPathWhenPermissionIsGranted() {
        initAclEntity(pipelinerun, AclPermission.OWNER);
        doReturn(storageMountPath).when(mockRunMountService).getSharedFSSPathForRun(ID, true);
        doReturn(pipelinerun).when(mockRunCRUDService).loadRunById(ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(dataStorageApiService.getSharedFSSPathForRun(ID, true)).isEqualTo(storageMountPath);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetSharedFSSPathWhenPermissionIsNotGranted() {
        initAclEntity(pipelinerun);
        doReturn(storageMountPath).when(mockRunMountService).getSharedFSSPathForRun(ID, true);
        doReturn(pipelinerun).when(mockRunCRUDService).loadRunById(ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.getSharedFSSPathForRun(ID, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGenerateCredentialsForAdmin() {
        mockUserContext(context);
        doReturn(temporaryCredentials).when(mockTemporaryCredentialsManager).generate(dataStorageActionList);

        assertThat(dataStorageApiService.generateCredentials(dataStorageActionList)).isEqualTo(temporaryCredentials);
    }

    @Test
    @WithMockUser
    public void shouldGenerateCredentialsWhenPermissionIsGranted() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(temporaryCredentials).when(mockTemporaryCredentialsManager).generate(dataStorageActionList);

        assertThat(dataStorageApiService.generateCredentials(dataStorageActionList)).isEqualTo(temporaryCredentials);
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateCredentialsWhenReadPermissionIsNotGranted() {
        final DataStorageAction dataStorageAction = DatastorageCreatorUtils.getDataStorageAction();
        dataStorageAction.setRead(true);
        final List<DataStorageAction> dataStorageActionList = Collections.singletonList(dataStorageAction);
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(temporaryCredentials).when(mockTemporaryCredentialsManager).generate(dataStorageActionList);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.generateCredentials(dataStorageActionList));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateCredentialsWhenWritePermissionIsNotGranted() {
        final DataStorageAction dataStorageAction = DatastorageCreatorUtils.getDataStorageAction();
        dataStorageAction.setWrite(true);
        final List<DataStorageAction> dataStorageActionList = Collections.singletonList(dataStorageAction);
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(temporaryCredentials).when(mockTemporaryCredentialsManager).generate(dataStorageActionList);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.generateCredentials(dataStorageActionList));
    }

    @Test
    @WithMockUser
    public void shouldDenyGenerateCredentialsWhenStorageNotShared() {
        context.setExternal(true);
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(temporaryCredentials).when(mockTemporaryCredentialsManager).generate(dataStorageActionList);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.generateCredentials(dataStorageActionList));
    }
}

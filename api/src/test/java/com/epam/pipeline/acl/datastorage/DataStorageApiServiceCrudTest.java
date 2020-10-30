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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.junit.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.AopTestUtils;

import java.util.Arrays;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class DataStorageApiServiceCrudTest extends AbstractDataStorageAclTest {

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

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getDataStorages();

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

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getWritableStorages();

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

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWhenWritePermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAvailableStoragesWhichPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        initAclEntity(anotherS3bucket);
        doReturn(mutableListOf(s3bucket, anotherS3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

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

        final AbstractDataStorage returnedDataStorage = dataStorageApiService.load(ID);

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

        final AbstractDataStorage returnedDataStorage = dataStorageApiService.loadByNameOrId(TEST_STRING);

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

        final AbstractDataStorage returnedDataStorage = dataStorageApiService.loadByPathOrId(TEST_STRING);

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
    public void shouldUpdateDataStorageForAdmin() {
        initAclEntity(s3bucket);
        mockS3bucket();
        mockAuthUser(OWNER_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).update(dataStorageVO);

        assertThat(dataStorageApiService.update(dataStorageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateDataStorageWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        mockS3bucket();
        mockAuthUser(OWNER_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).update(dataStorageVO);

        final AbstractDataStorage returnedStorage = dataStorageApiService.update(dataStorageVO);

        assertThat(returnedStorage).isEqualTo(s3bucket);
        assertThat(returnedStorage.getMask()).isEqualTo(2);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDataStorageWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        mockS3bucket();
        mockAuthUser(SIMPLE_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).update(dataStorageVO);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.update(dataStorageVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdatePolicyForAdmin() {
        initAclEntity(s3bucket);
        mockS3bucket();
        mockAuthUser(OWNER_USER);
        doReturn(s3bucket).when(mockDataStorageManager).updatePolicy(dataStorageVO);

        assertThat(dataStorageApiService.updatePolicy(dataStorageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdatePolicyWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.OWNER);
        mockS3bucket();
        mockAuthUser(OWNER_USER);
        doReturn(s3bucket).when(mockDataStorageManager).updatePolicy(dataStorageVO);

        assertThat(dataStorageApiService.updatePolicy(dataStorageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdatePolicyWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        mockS3bucket();
        mockAuthUser(SIMPLE_USER);
        doReturn(s3bucket).when(mockDataStorageManager).updatePolicy(dataStorageVO);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.updatePolicy(dataStorageVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteDataStorageForAdmin() {
        initAclEntity(s3bucket);
        mockS3bucket();
        mockAuthUser(OWNER_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).delete(ID, true);

        assertThat(dataStorageApiService.delete(ID, true)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(roles = STORAGE_MANAGER_ROLE)
    public void shouldDeleteDataStorageWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        mockS3bucket();
        mockAuthUser(OWNER_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).delete(ID, true);

        assertThat(dataStorageApiService.delete(ID, true)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(roles = STORAGE_MANAGER_ROLE)
    public void shouldDenyDeleteDataStorageWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        mockS3bucket();
        mockAuthUser(SIMPLE_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).delete(ID, true);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.delete(ID, true));
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteDataStorageWhenRoleIsNotGranted() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        mockS3bucket();
        mockAuthUser(SIMPLE_USER);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).delete(ID, true);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.delete(ID, true));
    }
}

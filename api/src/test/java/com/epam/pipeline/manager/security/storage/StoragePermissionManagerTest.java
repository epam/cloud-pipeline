/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.manager.security.storage;

import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

public class StoragePermissionManagerTest extends AbstractAclTest {

    private static final List<String> READ_OR_WRITE = Arrays.asList("READ", "WRITE");
    private static final int READ_PERMISSION = 1;
    private static final int READ_AND_WRITE_PERMISSION = 3;

    private final AbstractDataStorage grantedStorage =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);
    private final AbstractDataStorage notGrantedStorage =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID_2, ANOTHER_SIMPLE_USER);

    @Autowired
    private StoragePermissionManager storagePermissionManager;

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void filterStorageShouldKeepEveryStorageForStorageReader() {
        initStorages();
        final List<AbstractDataStorage> storages = mutableListOf(grantedStorage, notGrantedStorage);

        storagePermissionManager.filterStorage(storages, READ_OR_WRITE, false);

        assertThat(storages).containsExactly(grantedStorage, notGrantedStorage);
        assertThat(grantedStorage.getMask()).isEqualTo(READ_AND_WRITE_PERMISSION);
        assertThat(notGrantedStorage.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void filterStorageShouldKeepEveryStorageReadOnlyForStorageReaderInReadOnlyMode() {
        initStorages();
        doReturn(Optional.of(new AppliedQuota())).when(mockQuotaService)
                .findActiveActionForUser(any(), any(), any());
        final List<AbstractDataStorage> storages = mutableListOf(grantedStorage, notGrantedStorage);

        storagePermissionManager.filterStorage(storages, READ_OR_WRITE, false);

        assertThat(storages).containsExactly(grantedStorage, notGrantedStorage);
        assertThat(grantedStorage.getMask()).isEqualTo(READ_PERMISSION);
        assertThat(notGrantedStorage.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void filterStorageShouldKeepReadOnlyNfsStorageReadOnlyForStorageReader() {
        final AbstractDataStorage nfsStorage =
                DatastorageCreatorUtils.getNfsDataStorage(NFSStorageMountStatus.READ_ONLY, ANOTHER_SIMPLE_USER);
        initAclEntity(nfsStorage, new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask()));
        mockAuthUser(SIMPLE_USER);
        final List<AbstractDataStorage> storages = mutableListOf(nfsStorage);

        storagePermissionManager.filterStorage(storages, READ_OR_WRITE, false);

        assertThat(storages).containsExactly(nfsStorage);
        assertThat(nfsStorage.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void filterStorageShouldKeepOnlyGrantedStoragesWithoutStorageReader() {
        initStorages();
        final List<AbstractDataStorage> storages = mutableListOf(grantedStorage, notGrantedStorage);

        storagePermissionManager.filterStorage(storages, READ_OR_WRITE, false);

        assertThat(storages).containsExactly(grantedStorage);
        assertThat(grantedStorage.getMask()).isEqualTo(READ_AND_WRITE_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void storagePermissionShouldGrantOnlyReadForStorageReader() {
        initStorages();

        assertThat(storagePermissionManager.storagePermissionById(ID_2, AclPermission.READ_NAME)).isTrue();
        assertThat(storagePermissionManager.storagePermissionById(ID_2, AclPermission.WRITE_PERMISSION)).isFalse();
        assertThat(storagePermissionManager.storagePermissionById(ID_2, "OWNER")).isFalse();
        assertThat(storagePermissionManager.isStorageReader()).isTrue();
        assertThat(storagePermissionManager.isStorageAdmin()).isFalse();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void storagePermissionShouldNotGrantReadWithoutStorageReader() {
        initStorages();

        assertThat(storagePermissionManager.storagePermissionById(ID_2, AclPermission.READ_NAME)).isFalse();
        assertThat(storagePermissionManager.isStorageReader()).isFalse();
    }

    private void initStorages() {
        initAclEntity(grantedStorage, Arrays.asList(
                new UserPermission(SIMPLE_USER, AclPermission.READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        initAclEntity(notGrantedStorage);
        mockAuthUser(SIMPLE_USER);
    }
}

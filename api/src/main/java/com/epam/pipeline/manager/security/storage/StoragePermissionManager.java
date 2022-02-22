/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.security.storage;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoragePermissionManager {

    private static final String READ = "READ";

    private final GrantPermissionManager grantPermissionManager;
    private final EntityManager entityManager;

    public boolean storagePermission(final AbstractDataStorage storage,
                                     final String permissionName) {
        return grantPermissionManager.storagePermission(storage, permissionName);
    }

    public boolean storageWithSharePermission(final DataStorageWithShareMount storageWithShare,
                                              final String permissionName) {
        final AbstractDataStorage storage = storageWithShare.getStorage();
        final boolean accessGranted = storagePermission(storage, permissionName);
        if (accessGranted) {
            Optional.of(storage)
                    .filter(NFSDataStorage.class::isInstance)
                    .map(NFSDataStorage.class::cast)
                    .map(NFSDataStorage::getMountStatus)
                    .filter(NFSStorageMountStatus.MOUNT_DISABLED::equals)
                    .ifPresent(status -> storage.setMask(AclPermission.READ.getMask()));
        }
        return accessGranted;
    }

    public boolean storagePermissionById(final Long storageId,
                                     final String permissionName) {
        AbstractSecuredEntity storage = entityManager.load(AclClass.DATA_STORAGE, storageId);
        return grantPermissionManager.storagePermission(storage, permissionName);
    }

    public boolean storagePermissions(final Long storageId,
                                      final List<String> permissionNames) {
        return Optional.ofNullable(permissionNames)
                .filter(CollectionUtils::isNotEmpty)
                .orElseGet(() -> Collections.singletonList(READ))
                .stream()
                .allMatch(permissionName -> storagePermissionById(storageId, permissionName));
    }

    public boolean storagePermissionByName(final String identifier, final String permissionName) {
        final AbstractSecuredEntity storage = entityManager.loadByNameOrId(AclClass.DATA_STORAGE, identifier);
        return grantPermissionManager.storagePermission(storage, permissionName);
    }
}
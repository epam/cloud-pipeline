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

import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.dto.quota.QuotaGroup;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.datastorage.DataStoragePathLoader;
import com.epam.pipeline.manager.quota.QuotaService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.security.acls.model.Sid;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoragePermissionManager {

    private static final String READ = "READ";

    private final GrantPermissionManager grantPermissionManager;
    private final EntityManager entityManager;
    private final CheckPermissionHelper permissionHelper;
    private final PermissionsService permissionsService;
    private final QuotaService quotaService;
    private final AuthManager authManager;
    private final DataStoragePathLoader storagePathLoader;

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
        final AbstractSecuredEntity storage = entityManager.load(AclClass.DATA_STORAGE, storageId);
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

    public boolean storagePermissionByName(final String identifier,
                                           final String permissionName) {
        final AbstractSecuredEntity storage = entityManager.loadByNameOrId(AclClass.DATA_STORAGE, identifier);
        return grantPermissionManager.storagePermission(storage, permissionName);
    }

    public boolean storagePermissionAndSharedByPath(final String path,
                                                    final String permissionName) {
        try {
            final AbstractSecuredEntity storage = storagePathLoader.loadDataStorageByPathOrId(path);
            return grantPermissionManager.checkStorageShared(storage.getId())
                    && grantPermissionManager.storagePermission(storage, permissionName);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void filterStorage(final List<AbstractDataStorage> storages,
                              final List<String> permissionNames) {
        filterStorage(storages, permissionNames, false);
    }

    public void filterStorage(final List<AbstractDataStorage> storages,
                              final List<String> permissionNames,
                              final boolean allPermissions) {
        if (permissionHelper.isAdmin()) {
            return;
        }
        final Optional<AppliedQuota> activeQuota = quotaService.findActiveActionForUser(authManager.getCurrentUser(),
                QuotaActionType.READ_MODE, QuotaGroup.STORAGE);
        final List<Sid> sids = permissionHelper.getSids();
        final List<AclPermission> permissions = permissionNames.stream()
                .map(AclPermission::getAclPermissionByName)
                .collect(Collectors.toList());
        final List<AbstractDataStorage> filtered = ListUtils.emptyIfNull(storages)
                .stream()
                .peek(storage -> storage.setMask(
                        grantPermissionManager.getPermissionsMask(storage, true, true, sids,
                                activeQuota)))
                .filter(storage -> checkPermissions(permissions, storage, allPermissions))
                .collect(Collectors.toList());
        if (storages.size() != filtered.size()) {
            storages.clear();
            storages.addAll(filtered);
        }
    }

    private boolean checkPermissions(final List<AclPermission> permissions,
                                     final AbstractDataStorage storage,
                                     final boolean allPermissions) {
        if (allPermissions) {
            return permissions.stream()
                    .allMatch(permission -> permissionsService.isMaskBitSet(storage.getMask(),
                            permission.getSimpleMask()));
        }
        return permissions.stream()
                .anyMatch(permission -> permissionsService.isMaskBitSet(storage.getMask(),
                        permission.getSimpleMask()));
    }
}
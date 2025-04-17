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

package com.epam.pipeline.acl.datastorage.permissions;

import com.epam.pipeline.dto.PermissionVO;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissionsVO;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissions;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.user.SidImpl;
import com.epam.pipeline.manager.datastorage.permissions.StoragePathPermissionsService;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StoragePathPermissionsApiService {

    public final StoragePathPermissionsService storagePathPermissionsService;

    @PreAuthorize(AclExpressions.STORAGE_ID_OWNER)
    public void updateStoragePathPermissions(final Long id, final String sidName, final boolean principal,
                                             final List<StoragePathPermissions> permissions) {
        storagePathPermissionsService.batchUpdate(id, sidName, principal, permissions);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_READ)
    public List<StoragePathPermissions> loadStoragePathPermissions(final Long id) {
        return storagePathPermissionsService.loadHierarchyForStorage(id);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_OWNER)
    public void deleteStoragePathPermissions(final Long id, final List<SidImpl> sids) {
        storagePathPermissionsService.deleteByStorageIdAndSids(id, sids);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_OWNER)
    public List<PermissionVO> loadStoragePathPermissionsSids(final Long id, final String path,
                                                             final DataStorageItemType type) {
        return Objects.isNull(path)
                ? storagePathPermissionsService.loadSids(id)
                : storagePathPermissionsService.loadSidsByPath(id, path, type);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_OWNER)
    public void updateStoragePathPermissionsForItems(final Long id,
                                                     final List<StoragePathPermissionsVO> permissions) {
        storagePathPermissionsService.updateByStorageIdAndPaths(id, permissions);
    }
}

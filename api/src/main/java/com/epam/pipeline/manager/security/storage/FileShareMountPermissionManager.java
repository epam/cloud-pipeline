/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileShareMountPermissionManager {

    private final DataStorageManager dataStorageManager;
    private final StoragePermissionManager storagePermissionManager;

    public boolean hasFSMountPermission(final Long id, final String permissionName) {
        final List<AbstractDataStorage> storages = dataStorageManager.loadDataStoragesByMountId(id);
        storagePermissionManager.filterStorage(storages, Collections.singletonList(permissionName), false);
        return CollectionUtils.isNotEmpty(storages);
    }
}

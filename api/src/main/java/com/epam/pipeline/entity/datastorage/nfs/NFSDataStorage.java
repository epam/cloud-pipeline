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

package com.epam.pipeline.entity.datastorage.nfs;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a Data Storage, backed by NFS file system
 */
@Getter
@Setter
@NoArgsConstructor
public class NFSDataStorage extends AbstractDataStorage {

    public NFSDataStorage(Long id, String name, String path) {
        super(id, name, normalizeNfsPath(path), DataStorageType.NFS);
    }

    public NFSDataStorage(Long id, String name, String path, StoragePolicy policy, String mountPoint) {
        super(id, name, normalizeNfsPath(path), DataStorageType.NFS, policy, mountPoint);
    }

    @Override
    public String getDelimiter() {
        return ProviderUtils.DELIMITER;
    }

    @Override
    public String getPathMask() {
        return String.format("nfs://%s", getPath());
    }

    @Override
    public boolean isPolicySupported() {
        return false;
    }

    @Override
    public String getRoot() {
        return NFSHelper.getNfsRootPath(getPath());
    }

    private static String normalizeNfsPath(String path) {
        return path.replaceAll(" ", "-");
    }
}

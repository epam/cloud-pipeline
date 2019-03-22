/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.datastorage;

import java.util.List;

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.entity.datastorage.aws.S3TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;

public abstract class AbstractDataStorageFactory {

    public static AbstractDataStorageFactory getDefaultDataStorageFactory() {
        return new DefaultDataStorageFactory();
    }

    public abstract AbstractDataStorage convertToDataStorage(
            Long id, String name, String path, DataStorageType type,
            StoragePolicy policy, String mountOptions, String mountPoint,
            List<String> allowedCidrs, Long regionId);

    public AbstractDataStorage convertToDataStorage(DataStorageVO vo) {
        AbstractDataStorage storage =
                convertToDataStorage(vo.getId(), vo.getName(), vo.getPath(), vo.getType(), vo.getStoragePolicy(),
                                     vo.getMountOptions(), vo.getMountPoint(), vo.getAllowedCidrs(), vo.getRegionId());
        storage.setDescription(vo.getDescription());
        storage.setParentFolderId(vo.getParentFolderId());
        storage.setShared(vo.isShared());
        return storage;
    }

    public abstract AbstractTemporaryCredentials temporaryCredentials(DataStorageType type);

    public static class DefaultDataStorageFactory extends AbstractDataStorageFactory {

        public DefaultDataStorageFactory() {
            //no op
        }

        @Override
        public AbstractDataStorage convertToDataStorage(final Long id, final String name,
                                                        final String path, final DataStorageType type,
                                                        final StoragePolicy policy, final String mountOptions,
                                                        final String mountPoint, final List<String> allowedCidrs,
                                                        final Long regionId) {
            switch (type) {
                case S3:
                    S3bucketDataStorage bucket = new S3bucketDataStorage(id, name, path, policy, mountPoint);
                    bucket.setAllowedCidrs(allowedCidrs);
                    bucket.setRegionId(regionId);
                    return bucket;
                case NFS:
                    NFSDataStorage storage = new NFSDataStorage(id, name, path, policy, mountPoint);
                    storage.setMountOptions(mountOptions);
                    return storage;
                default:
                    throw new IllegalArgumentException("Unsupported data storage type: " + type);
            }
        }

        @Override
        public AbstractTemporaryCredentials temporaryCredentials(DataStorageType type) {
            switch (type) {
                case S3:
                    return new S3TemporaryCredentials();
                default:
                    throw new IllegalArgumentException("Unsupported data storage type: " + type);
            }
        }
    }
}

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

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.region.CloudProvider;

import java.util.List;
import java.util.Optional;

public abstract class AbstractDataStorageFactory {

    public static AbstractDataStorageFactory getDefaultDataStorageFactory() {
        return new DefaultDataStorageFactory();
    }

    public abstract AbstractDataStorage convertToDataStorage(
            Long id, String name, String path, DataStorageType type,
            StoragePolicy policy, String mountOptions, String mountPoint,
            List<String> allowedCidrs, Long regionId, Long fileShareMountId);

    public AbstractDataStorage convertToDataStorage(DataStorageVO vo, final CloudProvider provider) {
        DataStorageType type = determineStorageType(vo, provider);
        AbstractDataStorage storage =
                convertToDataStorage(vo.getId(), vo.getName(), vo.getPath(), type, vo.getStoragePolicy(),
                                     vo.getMountOptions(), vo.getMountPoint(), vo.getAllowedCidrs(),
                                     vo.getRegionId(), vo.getFileShareMountId());
        storage.setDescription(vo.getDescription());
        storage.setParentFolderId(vo.getParentFolderId());
        storage.setShared(vo.isShared());
        return storage;
    }

    private DataStorageType determineStorageType(final DataStorageVO vo, final CloudProvider provider) {
        return Optional.ofNullable(vo.getServiceType())
                .map(serviceType -> DataStorageType.fromServiceType(provider, serviceType))
                .orElse(vo.getType());
    }

    public static class DefaultDataStorageFactory extends AbstractDataStorageFactory {

        public DefaultDataStorageFactory() {
            //no op
        }

        @Override
        public AbstractDataStorage convertToDataStorage(final Long id, final String name,
                                                        final String path, final DataStorageType type,
                                                        final StoragePolicy policy, final String mountOptions,
                                                        final String mountPoint, final List<String> allowedCidrs,
                                                        final Long regionId, Long fileShareMountId) {
            switch (type) {
                case S3:
                    S3bucketDataStorage bucket = new S3bucketDataStorage(id, name, path, policy, mountPoint);
                    bucket.setAllowedCidrs(allowedCidrs);
                    bucket.setRegionId(regionId);
                    return bucket;
                case NFS:
                    NFSDataStorage storage = new NFSDataStorage(id, name, path, policy, mountPoint);
                    storage.setMountOptions(mountOptions);
                    storage.setFileShareMountId(fileShareMountId);
                    return storage;
                case AZ:
                    final AzureBlobStorage blobStorage = new AzureBlobStorage(id, name, path, policy, mountPoint);
                    blobStorage.setStoragePolicy(null);
                    blobStorage.setRegionId(regionId);
                    return blobStorage;
                case GS:
                    final GSBucketStorage gsBucketStorage = new GSBucketStorage(id, name, path, policy,
                            mountPoint);
                    gsBucketStorage.setStoragePolicy(null);
                    gsBucketStorage.setRegionId(regionId);
                    return gsBucketStorage;
                default:
                    throw new IllegalArgumentException("Unsupported data storage type: " + type);
            }
        }
    }
}

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

package com.epam.pipeline.entity.datastorage;

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsReferenceDataStorage;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.region.CloudProvider;
import org.apache.commons.lang.BooleanUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

//TODO: refactor this code similar to AbstractCloudRegion hierarchy handling
public abstract class AbstractDataStorageFactory {

    public static AbstractDataStorageFactory getDefaultDataStorageFactory() {
        return new DefaultDataStorageFactory();
    }

    public abstract AbstractDataStorage convertToDataStorage(
            Long id, String name, String path, DataStorageType type,
            StoragePolicy policy, String mountOptions, String mountPoint,
            List<String> allowedCidrs, Long regionId, Long fileShareMountId,
            String kmsKey, String tempRole, boolean useAssumedCreds, String mountStatus,
            Set<String> masks, Long sourceStorageId);

    public AbstractDataStorage convertToDataStorage(DataStorageVO vo, final CloudProvider provider) {
        DataStorageType type = determineStorageType(vo, provider);
        AbstractDataStorage storage =
                convertToDataStorage(vo.getId(), vo.getName(), vo.getPath(), type, vo.getStoragePolicy(),
                        vo.getMountOptions(), vo.getMountPoint(), vo.getAllowedCidrs(),
                        vo.getRegionId(), vo.getFileShareMountId(),
                        vo.getKmsKeyArn(), vo.getTempCredentialsRole(), vo.isUseAssumedCredentials(),
                        NFSStorageMountStatus.ACTIVE.name(), vo.getLinkingMasks(), vo.getSourceStorageId());
        storage.setDescription(vo.getDescription());
        storage.setParentFolderId(vo.getParentFolderId());
        storage.setShared(vo.isShared());
        storage.setSensitive(vo.isSensitive());
        storage.setMountDisabled(storage.isMountDisabled() || BooleanUtils.isTrue(vo.getMountDisabled()));
        storage.setToolsToMount(vo.getToolsToMount());
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
                                                        final Long regionId, final Long fileShareMountId,
                                                        final String kmsKey, final String tempRole,
                                                        final boolean useAssumedCreds,
                                                        final String mountStatus,
                                                        final Set<String> masks,
                                                        final Long sourceStorageId) {
            final AbstractDataStorage resultStorage;
            switch (type) {
                case S3:
                    S3bucketDataStorage bucket = new S3bucketDataStorage(id, name, path, policy, mountPoint);
                    bucket.setAllowedCidrs(allowedCidrs);
                    bucket.setRegionId(regionId);
                    bucket.setKmsKeyArn(kmsKey);
                    bucket.setTempCredentialsRole(tempRole);
                    bucket.setUseAssumedCredentials(useAssumedCreds);
                    resultStorage = bucket;
                    break;
                case NFS:
                    NFSDataStorage storage = new NFSDataStorage(id, name, path, policy, mountPoint);
                    storage.setMountOptions(mountOptions);
                    storage.setFileShareMountId(fileShareMountId);
                    storage.setMountStatus(NFSStorageMountStatus.fromName(mountStatus));
                    resultStorage = storage;
                    break;
                case AZ:
                    final AzureBlobStorage blobStorage = new AzureBlobStorage(id, name, path, policy, mountPoint);
                    blobStorage.setStoragePolicy(null);
                    blobStorage.setRegionId(regionId);
                    resultStorage = blobStorage;
                    break;
                case GS:
                    final GSBucketStorage gsBucketStorage = new GSBucketStorage(id, name, path, policy,
                            mountPoint);
                    gsBucketStorage.setRegionId(regionId);
                    resultStorage = gsBucketStorage;
                    break;
                case AWS_OMICS_REF:
                    final AWSOmicsReferenceDataStorage omicsRefStore = new AWSOmicsReferenceDataStorage(id, name, path);
                    omicsRefStore.setKmsKeyArn(kmsKey);
                    omicsRefStore.setRegionId(regionId);
                    omicsRefStore.setTempCredentialsRole(tempRole);
                    omicsRefStore.setMountDisabled(true);
                    omicsRefStore.setUseAssumedCredentials(useAssumedCreds);
                    resultStorage = omicsRefStore;
                    break;
                case AWS_OMICS_SEQ:
                    final AWSOmicsSequenceDataStorage omicsSeqStore = new AWSOmicsSequenceDataStorage(id, name, path);
                    omicsSeqStore.setKmsKeyArn(kmsKey);
                    omicsSeqStore.setRegionId(regionId);
                    omicsSeqStore.setTempCredentialsRole(tempRole);
                    omicsSeqStore.setMountDisabled(true);
                    omicsSeqStore.setUseAssumedCredentials(useAssumedCreds);
                    resultStorage = omicsSeqStore;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported data storage type: " + type);
            }
            if (sourceStorageId != null) {
                resultStorage.setLinkingMasks(masks);
                resultStorage.setSourceStorageId(sourceStorageId);
            }
            return resultStorage;
        }
    }
}

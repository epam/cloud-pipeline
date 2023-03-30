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

package com.epam.pipeline.manager.billing.detail;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.utils.Lazy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Slf4j
public class StorageBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Getter
    private final BillingGrouping grouping = BillingGrouping.STORAGE;

    public static final String STORAGE_NAME = "storage_name";
    public static final String STORAGE_PATH = "storage_path";
    public static final String STORAGE_TYPE = "storage_type";
    public static final String OBJECT_STORAGE_TYPE = "object_storage_type";
    public static final String FILE_STORAGE_TYPE = "file_storage_type";

    private final DataStorageManager dataStorageManager;
    private final CloudRegionManager regionManager;
    private final FileShareMountManager fileShareMountManager;
    private final EntityBillingDetailsLoader userBillingDetailsLoader;
    private final MessageHelper messageHelper;
    private final String emptyValue;

    @Override
    public Map<String, String> loadInformation(final String id, final boolean loadDetails,
                                               final Map<String, String> defaults) {
        final Lazy<Optional<AbstractDataStorage>> storage = Lazy.of(() -> load(id));
        final Lazy<Optional<AbstractCloudRegion>> region = Lazy.of(() -> storage.get()
                .flatMap(this::resolveRegionId)
                .flatMap(this::loadRegion));
        final Map<String, String> details = new HashMap<>(defaults);
        details.put(ID, id);
        details.computeIfAbsent(NAME, key -> Optional.ofNullable(details.get(FILE_STORAGE_TYPE))
                .filter(StringUtils::isNotBlank)
                .map(type -> details.get(STORAGE_NAME))
                .orElseGet(() -> details.get(STORAGE_PATH)));
        details.computeIfAbsent(NAME, key -> storage.get().map(AbstractDataStorage::getType)
                .filter(type -> type != DataStorageType.NFS)
                .map(type -> storage.get().map(AbstractDataStorage::getPath))
                .orElseGet(() -> storage.get().map(AbstractDataStorage::getName))
                .orElse(id));
        if (loadDetails) {
            details.computeIfAbsent(OWNER, key -> storage.get().map(AbstractDataStorage::getOwner)
                    .orElse(emptyValue));
            details.computeIfAbsent(BILLING_CENTER, key -> storage.get().map(AbstractDataStorage::getOwner)
                    .map(owner -> userBillingDetailsLoader.loadDetails(owner).get(BILLING_CENTER))
                    .orElse(emptyValue));
            details.computeIfAbsent(REGION, key -> region.get().map(AbstractCloudRegion::getName).orElse(emptyValue));
            details.computeIfAbsent(PROVIDER, key -> region.get().map(AbstractCloudRegion::getProvider)
                    .map(CloudProvider::name)
                    .map(Optional::of)
                    .orElseGet(() -> storage.get().map(AbstractDataStorage::getType).map(DataStorageType::name))
                    .orElse(emptyValue));
            details.computeIfAbsent(STORAGE_TYPE, key -> Optional.ofNullable(details.get(FILE_STORAGE_TYPE))
                    .map(Optional::of)
                    .orElseGet(() -> Optional.ofNullable(details.get(OBJECT_STORAGE_TYPE)))
                    .orElse(null));
            details.computeIfAbsent(STORAGE_TYPE, key -> storage.get().map(AbstractDataStorage::getType)
                    .filter(type -> type != DataStorageType.NFS)
                    .map(DataStorageType::name)
                    .map(Optional::of)
                    .orElseGet(() -> storage.get().map(this::getFileShareStorageType))
                    .orElse(emptyValue));
            details.computeIfAbsent(CREATED, key -> storage.get().map(AbstractDataStorage::getCreatedDate)
                    .map(DateUtils::convertDateToLocalDateTime)
                    .map(DateTimeFormatter.ISO_DATE_TIME::format)
                    .orElse(emptyValue));
            details.computeIfAbsent(IS_DELETED, key -> Boolean.toString(!storage.get().isPresent()));
        }
        return details;
    }

    private Optional<AbstractDataStorage> load(final String id) {
        try {
            return Optional.of(dataStorageManager.loadByNameOrId(id));
        } catch (RuntimeException e) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_BILLING_ENTITY_FOR_DETAILS_NOT_FOUND,
                    id, getGrouping()));
            return Optional.empty();
        }
    }

    private Optional<Long> resolveRegionId(final AbstractDataStorage storage) {
        switch (storage.getType()) {
            case S3: return Optional.ofNullable(((S3bucketDataStorage) storage).getRegionId());
            case AZ: return Optional.ofNullable(((AzureBlobStorage) storage).getRegionId());
            case GS: return Optional.ofNullable(((GSBucketStorage) storage).getRegionId());
            default: return tryResolveRegionIdThroughMount(storage);
        }
    }

    private Optional<Long> tryResolveRegionIdThroughMount(final AbstractDataStorage storage) {
        return Optional.ofNullable(storage.getFileShareMountId()).flatMap(mountId -> {
            try {
                final FileShareMount mount = fileShareMountManager.load(mountId);
                return Optional.ofNullable(mount.getRegionId());
            } catch (IllegalArgumentException e) {
                log.warn("Can't load region through mount point for storage id={}!", storage.getId());
                return Optional.empty();
            }
        });
    }

    private Optional<AbstractCloudRegion> loadRegion(final Long id) {
        try {
            return Optional.of(regionManager.load(id));
        } catch (IllegalArgumentException e) {
            log.warn("Can't load from DB info about region id={}!", id);
            return Optional.empty();
        }
    }

    private String getFileShareStorageType(final AbstractDataStorage storage) {
        return fileShareMountManager.find(storage.getFileShareMountId())
                .map(FileShareMount::getMountType)
                .map(Enum::name)
                .orElse(emptyValue);
    }
}

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

package com.epam.pipeline.manager.billing;

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
import com.epam.pipeline.utils.CommonUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Slf4j
public class StorageBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Getter
    private final BillingGrouping grouping = BillingGrouping.STORAGE;

    public static final String PROVIDER = "provider";
    public static final String REGION = "region";
    public static final String CREATED = "created";
    public static final String STORAGE_TYPE = "storage_type";

    private final DataStorageManager dataStorageManager;
    private final CloudRegionManager regionManager;
    private final FileShareMountManager fileShareMountManager;
    private final UserBillingDetailsLoader userBillingDetailsLoader;
    private final MessageHelper messageHelper;
    private final String emptyValue;

    public StorageBillingDetailsLoader(final DataStorageManager dataStorageManager,
                                       final CloudRegionManager regionManager,
                                       final FileShareMountManager fileShareMountManager,
                                       final UserBillingDetailsLoader userBillingDetailsLoader,
                                       final MessageHelper messageHelper,
                                       @Value("${billing.empty.report.value:unknown}")
                                       final String emptyValue) {
        this.dataStorageManager = dataStorageManager;
        this.regionManager = regionManager;
        this.fileShareMountManager = fileShareMountManager;
        this.userBillingDetailsLoader = userBillingDetailsLoader;
        this.messageHelper = messageHelper;
        this.emptyValue = emptyValue;
    }

    @Override
    public Map<String, String> loadInformation(final String id, final boolean loadDetails,
                                               final Map<String, String> defaults) {
        final Optional<AbstractDataStorage> storage = load(id);
        final Optional<AbstractCloudRegion> region = CommonUtils.first(defaults, "cloud_region_id", "cloudRegionId")
                .map(Long::new)
                .map(Optional::of)
                .orElseGet(() -> storage.flatMap(this::resolveRegionId))
                .flatMap(this::loadRegion);
        final Optional<DataStorageType> storageType = CommonUtils.first(defaults, "storage_type")
                .map(DataStorageType::getByName)
                .map(Optional::of)
                .orElseGet(() -> storage.map(AbstractDataStorage::getType));

        final Map<String, String> details = new HashMap<>();
        details.put(NAME, storageType
                .filter(DataStorageType.NFS::equals)
                .map(type -> CommonUtils.first(defaults, "storage_name")
                        .map(Optional::of)
                        .orElseGet(() -> storage.map(AbstractDataStorage::getName)))
                .orElseGet(() -> CommonUtils.first(defaults, "storage_path")
                        .map(Optional::of)
                        .orElseGet(() -> storage.map(AbstractDataStorage::getPath)))
                .orElse(emptyValue));
        if (loadDetails) {
            details.put(OWNER, CommonUtils.first(defaults, "owner_user_name", "owner")
                    .map(Optional::of)
                    .orElseGet(() -> storage.map(AbstractDataStorage::getOwner))
                    .orElse(emptyValue));
            details.put(BILLING_CENTER, CommonUtils.first(defaults, "owner_billing_center", "billing_center")
                    .map(Optional::of)
                    .orElseGet(() -> storage.map(AbstractDataStorage::getOwner)
                            .flatMap(userBillingDetailsLoader::getUserBillingCenter))
                    .orElse(emptyValue));
            details.put(REGION, region.map(AbstractCloudRegion::getName).orElse(emptyValue));
            details.put(PROVIDER, CommonUtils.first(defaults, "storage_provider", "provider")
                    .map(Optional::of)
                    .orElseGet(() -> region.map(AbstractCloudRegion::getProvider).map(CloudProvider::name))
                    .map(Optional::of)
                    .orElseGet(() -> storage.map(AbstractDataStorage::getType).map(DataStorageType::name))
                    .orElse(emptyValue));
            details.put(STORAGE_TYPE, storageType
                    .filter(t -> t != DataStorageType.NFS)
                    .map(DataStorageType::getId)
                    .map(Optional::of)
                    .orElseGet(() -> storage.map(this::getFileShareStorageType))
                    .orElse(emptyValue));
            details.put(CREATED, CommonUtils.first(defaults, "created_date")
                    .map(Optional::of)
                    .orElseGet(() -> storage.map(AbstractDataStorage::getCreatedDate)
                            .map(DateUtils::convertDateToLocalDateTime)
                            .map(DateTimeFormatter.ISO_DATE_TIME::format))
                    .orElse(emptyValue));
        }
        details.put(IS_DELETED, Boolean.toString(!storage.isPresent()));
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
            default: return tryLoadRegionIdThroughMount(storage);
        }
    }

    private Optional<Long> tryLoadRegionIdThroughMount(final AbstractDataStorage storage) {
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

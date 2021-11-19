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
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Slf4j
public class StorageBillingDetailsLoader implements EntityBillingDetailsLoader {

    private static final String PROVIDER = "provider";
    private static final String REGION = "region";
    private static final String CREATED = "created";

    @Value("${billing.empty.report.value:unknown}")
    private String emptyValue;

    @Autowired
    private final DataStorageManager dataStorageManager;

    @Autowired
    private final CloudRegionManager regionManager;

    @Autowired
    private final FileShareMountManager fileShareMountManager;

    @Autowired
    private final UserBillingDetailsLoader userBillingDetailsLoader;

    @Autowired
    private final MessageHelper messageHelper;

    @Override
    public BillingGrouping getGrouping() {
        return BillingGrouping.STORAGE;
    }

    @Override
    public Map<String, String> loadInformation(final String entityIdentifier, final boolean loadDetails) {
        final Map<String, String> details = new HashMap<>();
        try {
            final AbstractDataStorage storage = dataStorageManager.loadByNameOrId(entityIdentifier);
            final String storageName = DataStorageType.NFS.equals(storage.getType())
                             ? storage.getName()
                             : storage.getPath();
            details.put(NAME, storageName);
            if (loadDetails) {
                details.putAll(getRegionDetails(storage));
                details.put(OWNER, storage.getOwner());
                details.put(CREATED, DateTimeFormatter.ISO_DATE_TIME.format(storage.getCreatedDate()
                                                                                .toInstant()
                                                                                .atZone(ZoneId.systemDefault())
                                                                                .toLocalDateTime()));
                details.put(BillingGrouping.BILLING_CENTER.getCorrespondingField(),
                            userBillingDetailsLoader.getUserBillingCenter(storage.getOwner()));
                details.put(BillingGrouping.STORAGE_TYPE.getCorrespondingField(), getExplicitStorageType(storage));


            }
        } catch (RuntimeException e) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_BILLING_ENTITY_FOR_DETAILS_NOT_FOUND,
                                              entityIdentifier, getGrouping()));
            details.putIfAbsent(NAME, entityIdentifier);
            if (loadDetails) {
                details.putAll(getEmptyDetails());
            }
        }
        return details;
    }

    @Override
    public Map<String, String> getEmptyDetails() {
        return Stream.of(PROVIDER, REGION, OWNER, CREATED,
                         BillingGrouping.BILLING_CENTER.getCorrespondingField(),
                         BillingGrouping.STORAGE_TYPE.getCorrespondingField())
            .collect(Collectors.toMap(Function.identity(), k -> emptyValue));
    }

    private Map<String, String> getRegionDetails(final AbstractDataStorage storage) {
        final Map<String, String> details = new HashMap<>();
        final Long regionId;
        switch (storage.getType()) {
            case S3:
                regionId = ((S3bucketDataStorage) storage).getRegionId();
                break;
            case AZ:
                regionId = ((AzureBlobStorage) storage).getRegionId();
                break;
            case GS:
                regionId = ((GSBucketStorage) storage).getRegionId();
                break;
            default:
                regionId = tryLoadRegionIdThroughMount(storage);
                break;
        }
        if (regionId != null) {
            try {
                final AbstractCloudRegion region = regionManager.load(regionId);
                details.put(PROVIDER, region.getProvider().name());
                details.put(REGION, region.getName());
                return details;
            } catch (IllegalArgumentException e) {
                log.warn("Can't load from DB info about region id={}!", regionId);
            }
        }
        details.put(PROVIDER, storage.getType().name());
        details.put(REGION, emptyValue);
        return details;
    }

    private Long tryLoadRegionIdThroughMount(final AbstractDataStorage storage) {
        final Long mountId = storage.getFileShareMountId();
        if (mountId != null) {
            try {
                final FileShareMount mount = fileShareMountManager.load(mountId);
                return mount.getRegionId();
            } catch (IllegalArgumentException e) {
                log.warn("Can't load region through mount point for storage id={}!", storage.getId());
            }
        }
        return null;
    }

    private String getExplicitStorageType(final AbstractDataStorage dataStorage) {
        final DataStorageType commonType = dataStorage.getType();
        return commonType != DataStorageType.NFS
               ? commonType.getId()
               : fileShareMountManager.find(dataStorage.getFileShareMountId())
                   .map(FileShareMount::getMountType)
                   .map(Enum::name)
                   .orElse(emptyValue);
    }
}

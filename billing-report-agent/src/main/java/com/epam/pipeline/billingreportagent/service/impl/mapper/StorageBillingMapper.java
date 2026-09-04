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

package com.epam.pipeline.billingreportagent.service.impl.mapper;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.billing.StorageBillingInfo;
import com.epam.pipeline.billingreportagent.service.AbstractEntityMapper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.search.SearchDocumentType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@SuppressWarnings("LineLength")
@RequiredArgsConstructor
@Getter
public class StorageBillingMapper extends AbstractEntityMapper<StorageBillingInfo> {

    private final SearchDocumentType documentType;
    private final String billingCenterKey;

    @Override
    public Map<String, ?> map(final EntityContainer<StorageBillingInfo> container) {
        final StorageBillingInfo billingInfo = container.getEntity();
        final AbstractDataStorage storage = billingInfo.getEntity();

        final Optional<AbstractCloudRegion> region = Optional.ofNullable(container.getRegion());

        final Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(DOC_TYPE_FIELD, documentType.name());
        jsonMap.put("created_date", asString(billingInfo.getDate())); // Document creation date: 2022-07-22
        jsonMap.put("resource_type", billingInfo.getResourceType()); // Document resource type: COMPUTE / STORAGE
        jsonMap.put("cloudRegionId", region.map(AbstractCloudRegion::getId).orElse(null));
        jsonMap.put("cloud_region_name", region.map(AbstractCloudRegion::getName).orElse(null));
        jsonMap.put("cloud_region_provider", region.map(AbstractCloudRegion::getProvider).orElse(null));

        jsonMap.put("storage_id", storage.getId());
        jsonMap.put("storage_name", storage.getName());
        jsonMap.put("storage_path", storage.getPath());
        jsonMap.put("storage_type", billingInfo.getResourceStorageType()); // Storage resource type: OBJECT_STORAGE / FILE_STORAGE
        jsonMap.put("provider", storage.getType()); // Storage common type: S3 / AZ / GS / NFS
        jsonMap.put("object_storage_type", billingInfo.getObjectStorageType()); // Object storage type: S3 / AZ / GS
        jsonMap.put("file_storage_type", billingInfo.getFileStorageType()); // File storage type: NFS / SMB / LUSTRE
        jsonMap.put("storage_created_date", asString(storage.getCreatedDate()));
        jsonMap.put("usage_bytes", billingInfo.getUsageBytes());
        jsonMap.put("usage_bytes_avg", billingInfo.getUsageBytes());
        jsonMap.put("cost", billingInfo.getCost());

        final List<StorageBillingInfo.StorageBillingInfoDetails> billingDetails = billingInfo.getBillingDetails();
        if (CollectionUtils.isNotEmpty(billingDetails)) {
            // Detailed costs and sizes by Storage Class and file versions
            for (StorageBillingInfo.StorageBillingInfoDetails storageClassDetails : billingDetails) {
                final String storageClass = storageClassDetails.getStorageClass().toLowerCase(Locale.ROOT);
                jsonMap.put(String.format("%s_cost", storageClass), storageClassDetails.getCost());
                jsonMap.put(String.format("%s_usage_bytes", storageClass), storageClassDetails.getUsageBytes());
                jsonMap.put(String.format("%s_ov_cost", storageClass), storageClassDetails.getOldVersionCost());
                jsonMap.put(String.format("%s_ov_usage_bytes", storageClass),
                        storageClassDetails.getOldVersionUsageBytes());
                jsonMap.put(String.format("%s_total_cost", storageClass),
                        storageClassDetails.getCost() + storageClassDetails.getOldVersionCost());
                jsonMap.put(String.format("%s_total_usage_bytes", storageClass),
                        storageClassDetails.getUsageBytes() + storageClassDetails.getOldVersionUsageBytes());
            }
        }

        buildUserContent(container.getOwner(), jsonMap);
        return jsonMap;
    }
}

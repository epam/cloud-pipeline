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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@SuppressWarnings("LineLength")
@RequiredArgsConstructor
@Getter
public class StorageBillingMapper extends AbstractEntityMapper<StorageBillingInfo> {

    private final SearchDocumentType documentType;
    private final String billingCenterKey;

    @Override
    public XContentBuilder map(final EntityContainer<StorageBillingInfo> container) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            final StorageBillingInfo billingInfo = container.getEntity();
            final AbstractDataStorage storage = billingInfo.getEntity();

            final Optional<AbstractCloudRegion> region = Optional.ofNullable(container.getRegion());

            jsonBuilder.startObject()
                .field(DOC_TYPE_FIELD, documentType.name())
                .field("created_date", billingInfo.getDate()) // Document creation date: 2022-07-22
                .field("resource_type", billingInfo.getResourceType()) // Document resource type: COMPUTE / STORAGE
                .field("cloudRegionId", region.map(AbstractCloudRegion::getId).orElse(null))
                .field("cloud_region_name", region.map(AbstractCloudRegion::getName).orElse(null))
                .field("cloud_region_provider", region.map(AbstractCloudRegion::getProvider).orElse(null))

                .field("storage_id", storage.getId())
                .field("storage_name", storage.getName())
                .field("storage_path", storage.getPath())
                .field("storage_type", billingInfo.getResourceStorageType()) // Storage resource type: OBJECT_STORAGE / FILE_STORAGE
                .field("provider", storage.getType()) // Storage common type: S3 / AZ / GS / NFS
                .field("object_storage_type", billingInfo.getObjectStorageType()) // Object storage type: S3 / AZ / GS
                .field("file_storage_type", billingInfo.getFileStorageType()) // File storage type: NFS / SMB / LUSTRE
                .field("storage_created_date", asString(storage.getCreatedDate()))
                .field("usage_bytes", billingInfo.getUsageBytes())
                .field("usage_bytes_avg", billingInfo.getUsageBytes())
                .field("cost", billingInfo.getCost());

            final List<StorageBillingInfo.StorageBillingInfoDetails> billingDetails = billingInfo.getBillingDetails();
            if (CollectionUtils.isNotEmpty(billingDetails)) {
                // Detailed costs and sizes by Storage Class and file versions
                for (StorageBillingInfo.StorageBillingInfoDetails storageClassDetails : billingDetails) {
                    final String storageClass = storageClassDetails.getStorageClass().toLowerCase(Locale.ROOT);
                    jsonBuilder.field(String.format("%s_cost", storageClass), storageClassDetails.getCost());
                    jsonBuilder.field(
                            String.format("%s_usage_bytes", storageClass), storageClassDetails.getUsageBytes());
                    jsonBuilder.field(
                            String.format("%s_ov_cost", storageClass), storageClassDetails.getOldVersionCost());
                    jsonBuilder.field(
                            String.format("%s_ov_usage_bytes", storageClass),
                            storageClassDetails.getOldVersionUsageBytes()
                    );
                    jsonBuilder.field(String.format("%s_total_cost", storageClass),
                            storageClassDetails.getCost() + storageClassDetails.getOldVersionCost());
                    jsonBuilder.field(
                            String.format("%s_total_usage_bytes", storageClass),
                            storageClassDetails.getUsageBytes() + storageClassDetails.getOldVersionUsageBytes());
                }
            }

            return buildUserContent(container.getOwner(), jsonBuilder)
                    .endObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for data storage: ", e);
        }
    }

}


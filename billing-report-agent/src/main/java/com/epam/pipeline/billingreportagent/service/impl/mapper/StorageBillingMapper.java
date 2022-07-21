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
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.search.SearchDocumentType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

import static com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

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

            jsonBuilder.startObject()
                .field(DOC_TYPE_FIELD, documentType.name())
                .field("resource_type", billingInfo.getResourceType())
                .field("cloudRegionId", container.getRegion().getId())
                .field("cloud_region_name", container.getRegion().getName())

                .field("storage_id", storage.getId())
                .field("storage_title", storage.getType() != DataStorageType.NFS
                        ? storage.getPath()
                        : storage.getName())
                .field("storage_name", storage.getName())
                .field("storage_path", storage.getPath())
                .field("storage_type", billingInfo.getStorageType())
                .field("storage_kind", billingInfo.getStorageKind())
                .field("provider", storage.getType())

                .field("usage_bytes", billingInfo.getUsageBytes())
                .field("usage_bytes_avg", billingInfo.getUsageBytes())
                .field("cost", billingInfo.getCost())

                .field("created_date", billingInfo.getDate());

            return buildUserContent(container.getOwner(), jsonBuilder)
                    .endObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for data storage: ", e);
        }
    }
}


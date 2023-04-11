/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.billingreportagent.model.billing.StorageBillingInfo;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.model.storage.StorageDescription;
import com.epam.pipeline.billingreportagent.service.AbstractEntityMapper;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Objects;

public class LustreToBillingRequestConverterImpl extends StorageToBillingRequestConverter {

    public LustreToBillingRequestConverterImpl(final AbstractEntityMapper<StorageBillingInfo> mapper,
                                               final StoragePricingService storagePricing,
                                               final CloudPipelineAPIClient apiClient,
                                               final boolean enableStorageHistoricalBillingGeneration) {
        super(mapper, StorageType.FILE_STORAGE, storagePricing, apiClient, enableStorageHistoricalBillingGeneration);
    }

    public LustreToBillingRequestConverterImpl(final AbstractEntityMapper<StorageBillingInfo> mapper,
                                               final StoragePricingService storagePricing,
                                               final CloudPipelineAPIClient apiClient,
                                               final FileShareMountsService fileshareMountsService,
                                               final boolean enableStorageHistoricalBillingGeneration) {
        super(mapper, StorageType.FILE_STORAGE, storagePricing, apiClient,
                fileshareMountsService, MountType.LUSTRE, enableStorageHistoricalBillingGeneration);
    }

    @Override
    protected StorageDescription loadStorageDescription(final EntityContainer<AbstractDataStorage> container) {
        final AbstractDataStorage storage = container.getEntity();
        final String mountName = storage.getPath().split("/")[1];
        final LustreFS lustre = getApiClient().getLustre(mountName, container.getRegion().getId());
        final long byteSize = (long)lustre.getCapacityGb() * StoragePriceListLoader.GB_TO_BYTES;
        final StorageUsage usage = StorageUsage.builder()
                .id(storage.getId())
                .usage(Collections.singletonMap(StoragePriceListLoader.DEFAULT_STORAGE_CLASS,
                        StorageUsage.StorageUsageStats.builder()
                                .storageClass(StoragePriceListLoader.DEFAULT_STORAGE_CLASS)
                                .size(byteSize)
                                .effectiveSize(byteSize)
                                .build()))
                .build();
        return new StorageDescription(usage, lustre.getDeploymentType(), lustre.getThroughput());
    }

    @Override
    protected boolean filterPrice(final StoragePricing.StoragePricingEntity price,
                                  final Long sizeBytes,
                                  final StorageDescription description) {
        final String type = description.getType();
        final Integer throughput = description.getThroughput();
        if (StringUtils.isNotBlank(type) && type.startsWith("PERSISTENT") && Objects.nonNull(throughput)) {
            return throughput.equals(price.getThroughput());
        }
        return throughput.equals(0);
    }
}

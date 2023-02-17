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

package com.epam.pipeline.billingreportagent.model.billing;

import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.MountType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class StorageBillingInfo extends AbstractBillingInfo<AbstractDataStorage> {

    private Long usageBytes;
    private StorageType resourceStorageType;
    private DataStorageType objectStorageType;
    private MountType fileStorageType;
    private List<StorageBillingInfoDetails> billingDetails;

    @Builder
    public StorageBillingInfo(final LocalDate date, final AbstractDataStorage storage, final Long cost,
                              final Long usageBytes, final List<StorageBillingInfoDetails> billingDetails,
                              final StorageType resourceStorageType, final DataStorageType objectStorageType,
                              final MountType fileStorageType) {
        super(date, storage, cost, ResourceType.STORAGE);
        this.usageBytes = usageBytes;
        this.billingDetails = billingDetails;
        this.resourceStorageType = resourceStorageType;
        this.objectStorageType = objectStorageType;
        this.fileStorageType = fileStorageType;
    }

    @Value
    @Builder
    public static class StorageBillingInfoDetails {
        String storageClass;
        long usageBytes;
        long cost;
        long oldVersionUsageBytes;
        long oldVersionCost;
    }
}

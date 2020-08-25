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
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class StorageBillingInfo extends AbstractBillingInfo<AbstractDataStorage> {

    private Long usageBytes;
    private StorageType storageType;
    private Long regionId;

    @Builder
    public StorageBillingInfo(final LocalDate date, final AbstractDataStorage storage, final Long cost,
                              final Long usageBytes, final StorageType storageType, final Long regionId) {
        super(date, storage, cost, ResourceType.STORAGE);
        this.usageBytes = usageBytes;
        this.storageType = storageType;
        this.regionId = regionId;
    }
}

/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class StorageBillingChartCostDetails implements BillingChartDetails {
    
    BillingChartCostDetailsType type = BillingChartCostDetailsType.STORAGE_BILLING;
    
    List<StorageBillingDetails> tiers;
    
    @Override
    public BillingChartCostDetailsType getType() {
        return BillingChartCostDetailsType.STORAGE_BILLING;
    }

    @Value
    @Builder
    public static class StorageBillingDetails {
        String storageClass;
        Long cost;
        Long avgSize;
        Long size;
        Long oldVersionCost;
        Long oldVersionAvgSize;
        Long oldVersionSize;

        public static StorageBillingDetails empty(String storageClass) {
            return StorageBillingDetails.builder().storageClass(storageClass)
                    .cost(0L).oldVersionCost(0L)
                    .size(0L).oldVersionSize(0L)
                    .avgSize(0L).oldVersionAvgSize(0L)
                    .build();
        }
    }
}

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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class StoragePricing {

    private final Map<String, List<StoragePricingEntity>> prices = new HashMap<>();

    public Set<String> getStorageClasses() {
        return prices.keySet();
    }

    public List<StoragePricingEntity> getPrices(final String storageClass) {
        return prices.get(storageClass);
    }

    public void addPrice(final String storageClass, final StoragePricingEntity entity) {
        prices.computeIfAbsent(storageClass, (key) -> new ArrayList<>()).add(entity);
    }

    public void addPrices(final String storageClass, final List<StoragePricingEntity> entities) {
        prices.computeIfAbsent(storageClass, (key) -> new ArrayList<>()).addAll(entities);
    }

    @Data
    @AllArgsConstructor
    public static class StoragePricingEntity {

        private Long beginRangeBytes;
        private Long endRangeBytes;
        private BigDecimal priceCentsPerGb;
        private Integer throughput;

        public StoragePricingEntity(final Long beginRangeBytes,
                                    final Long endRangeBytes,
                                    final BigDecimal priceCentsPerGb) {
            this.beginRangeBytes = beginRangeBytes;
            this.endRangeBytes = endRangeBytes;
            this.priceCentsPerGb = priceCentsPerGb;
        }
    }
}

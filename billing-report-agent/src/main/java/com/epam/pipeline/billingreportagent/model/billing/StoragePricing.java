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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
public class StoragePricing {

    private final List<StoragePricingEntity> prices = new ArrayList<>();

    public void addPrice(final StoragePricingEntity entity) {
        prices.add(entity);
    }

    @Data
    @AllArgsConstructor
    public static class StoragePricingEntity {

        private Long beginRangeBytes;
        private Long endRangeBytes;
        private BigDecimal priceCentsPerGb;
    }
}

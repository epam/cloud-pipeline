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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.amazonaws.regions.Regions;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class StorageServicePricingTest {

    private static final long STORAGE_LIMIT_TIER_1 = 51200L;
    private static final long STORAGE_LIMIT_TIER_2 = STORAGE_LIMIT_TIER_1 * 10;

    @Test
    public void testDefaultPriceCalculation() {
        final StoragePricing pricingUsEast1 = new StoragePricing();
        pricingUsEast1.addPrice(new StoragePricing.StoragePricingEntity(0L,
                                                                        STORAGE_LIMIT_TIER_1,
                                                                        BigDecimal.TEN));
        final StoragePricing pricingUsEast2 = new StoragePricing();
        final long endRangeBytesTier1 = STORAGE_LIMIT_TIER_1;
        final long endRangeBytesTier2 = STORAGE_LIMIT_TIER_2;
        pricingUsEast2.addPrice(new StoragePricing.StoragePricingEntity(0L,
                                                                        endRangeBytesTier1,
                                                                        BigDecimal.valueOf(7)));
        pricingUsEast2.addPrice(new StoragePricing.StoragePricingEntity(endRangeBytesTier1,
                                                                        endRangeBytesTier2,
                                                                        BigDecimal.valueOf(5)));
        pricingUsEast2.addPrice(new StoragePricing.StoragePricingEntity(endRangeBytesTier2,
                                                                        Long.MAX_VALUE,
                                                                        BigDecimal.ONE));
        final Map<String, StoragePricing> testPriceList = new HashMap<>();
        testPriceList.put(Regions.US_EAST_1.getName(), pricingUsEast1);
        testPriceList.put(Regions.US_EAST_2.getName(), pricingUsEast2);
        final StoragePricingService testStoragePricing = new StoragePricingService(testPriceList);
        Assert.assertEquals(BigDecimal.TEN, testStoragePricing.getDefaultPriceGb());
    }
}

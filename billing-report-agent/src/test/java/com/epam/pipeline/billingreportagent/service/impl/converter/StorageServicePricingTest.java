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
import com.epam.pipeline.entity.region.CloudProvider;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;
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

    @Test
    public void priceListThatCantBeLoadedShouldNotFailServiceCreation() throws Exception {
        final IllegalStateException loadError = new IllegalStateException("Duplicate key us-east-1");
        final StoragePriceListLoader loader = loader();
        Mockito.doThrow(loadError).when(loader).loadFullPriceList();

        final StoragePricingService pricingService = new StoragePricingService(loader);

        final IllegalStateException updateError =
                Assertions.assertThrows(IllegalStateException.class, pricingService::updatePrices);
        Assertions.assertSame(loadError, updateError.getCause());
    }

    @Test
    public void priceListShouldBeLoadedAtUpdateAfterFailedServiceCreation() throws Exception {
        final StoragePriceListLoader loader = loader();
        Mockito.doThrow(new IllegalStateException("Price list is unavailable"))
                .doReturn(priceList(BigDecimal.ONE))
                .when(loader).loadFullPriceList();
        final StoragePricingService pricingService = new StoragePricingService(loader);

        pricingService.updatePrices();

        Assertions.assertEquals(BigDecimal.ONE, pricingService.getDefaultPriceGb());
        Assertions.assertNotNull(pricingService.getRegionPricing(Regions.US_EAST_1.getName()));
    }

    @Test
    public void failedUpdateShouldKeepPreviousPrices() throws Exception {
        final StoragePriceListLoader loader = loader();
        final Map<String, StoragePricing> previousPriceList = priceList(BigDecimal.ONE);
        Mockito.doReturn(previousPriceList)
                .doThrow(new IllegalStateException("Price list is unavailable"))
                .when(loader).loadFullPriceList();
        final StoragePricingService pricingService = new StoragePricingService(loader);

        pricingService.updatePrices();

        Assertions.assertEquals(BigDecimal.ONE, pricingService.getDefaultPriceGb());
        Assertions.assertSame(previousPriceList.get(Regions.US_EAST_1.getName()),
                pricingService.getRegionPricing(Regions.US_EAST_1.getName()));
    }

    @Test
    public void updateWithoutPricesShouldKeepPreviousPrices() throws Exception {
        final StoragePriceListLoader loader = loader();
        final Map<String, StoragePricing> previousPriceList = priceList(BigDecimal.ONE);
        Mockito.doReturn(previousPriceList)
                .doReturn(priceList(BigDecimal.ZERO))
                .when(loader).loadFullPriceList();
        final StoragePricingService pricingService = new StoragePricingService(loader);

        pricingService.updatePrices();

        Assertions.assertEquals(BigDecimal.ONE, pricingService.getDefaultPriceGb());
        Assertions.assertSame(previousPriceList.get(Regions.US_EAST_1.getName()),
                pricingService.getRegionPricing(Regions.US_EAST_1.getName()));
    }

    private static StoragePriceListLoader loader() {
        final StoragePriceListLoader loader = Mockito.mock(StoragePriceListLoader.class);
        Mockito.doReturn(CloudProvider.AWS).when(loader).getProvider();
        return loader;
    }

    private static Map<String, StoragePricing> priceList(final BigDecimal priceCentsPerGb) {
        final StoragePricing pricing = new StoragePricing();
        pricing.addPrice(new StoragePricing.StoragePricingEntity(0L, Long.MAX_VALUE, priceCentsPerGb));
        return Collections.singletonMap(Regions.US_EAST_1.getName(), pricing);
    }
}

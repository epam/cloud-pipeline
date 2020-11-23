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

import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingEntity;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings("checkstyle:magicnumber")
public class AzurePriceListLoaderTest {

    private static final int THOUSAND = 1000;
    private static final String TEST_REGION = "TestRegion";

    @Test
    public void azureNetAppPriceListLoaderTest() {
        final AzureNetAppStoragePriceListLoader loader = new AzureNetAppStoragePriceListLoader(null, null, null, "Standard");
        final Float azureApiPrice = 2f;
        final AzurePricingEntity pricingEntity = AzurePricingEntity.builder()
                .unit(THOUSAND + " GiB/Hour")
                .meterCategory("Azure NetApp Files")
                .meterName("Standard Capacity")
                .meterRegion(TEST_REGION)
                .meterRates(Collections.singletonMap("0", azureApiPrice))
                .build();
        final Map<String, StoragePricing> storagePricing = loader.extractPrices(
                Collections.singletonList(pricingEntity
        ));
        Assert.assertEquals(BigDecimal.valueOf(
                azureApiPrice.doubleValue() * AbstractAzureStoragePriceListLoader.HRS_PER_MONTH
                / THOUSAND * StoragePriceListLoader.CENTS_IN_DOLLAR),
                storagePricing.get(TEST_REGION).getPrices().get(0).getPriceCentsPerGb()
        );
    }

    @Test
    public void azureBlobPriceListLoaderTest() {
        final AzureBlobStoragePriceListLoader loader = new AzureBlobStoragePriceListLoader(null, null, null,
                "General Block Blob", "Hot LRS");
        final Float azureApiPrice = 2.08f;
        final AzurePricingEntity pricingEntity = AzurePricingEntity.builder()
                .unit(THOUSAND + " GB/Month")
                .meterCategory("Storage")
                .meterSubCategory("General Block Blob V2 H")
                .meterName("Hot LRS Data Stored")
                .meterRegion(TEST_REGION)
                .meterRates(Collections.singletonMap("0", azureApiPrice))
                .build();
        final Map<String, StoragePricing> storagePricing = loader.extractPrices(
                Collections.singletonList(pricingEntity
                ));
        Assert.assertEquals(BigDecimal.valueOf(
                azureApiPrice.doubleValue() / THOUSAND * StoragePriceListLoader.CENTS_IN_DOLLAR),
                storagePricing.get(TEST_REGION).getPrices().get(0).getPriceCentsPerGb()
        );
    }

    @Test
    public void azureFilesPriceListLoaderTest() {
        final AzureFilesStoragePriceListLoader loader = new AzureFilesStoragePriceListLoader(
                null, null, null, "Hot LRS"
        );
        final Float azureApiPrice = 2.08f;
        final AzurePricingEntity pricingEntity = AzurePricingEntity.builder()
                .unit(THOUSAND + " GB/Month")
                .meterCategory("Storage")
                .meterSubCategory("Files v2")
                .meterName("Hot LRS Data Stored")
                .meterRegion(TEST_REGION)
                .meterRates(Collections.singletonMap("0", azureApiPrice))
                .build();
        final Map<String, StoragePricing> storagePricing = loader.extractPrices(
                Collections.singletonList(pricingEntity
                ));
        Assert.assertEquals(BigDecimal.valueOf(
                azureApiPrice.doubleValue() / THOUSAND * StoragePriceListLoader.CENTS_IN_DOLLAR),
                storagePricing.get(TEST_REGION).getPrices().get(0).getPriceCentsPerGb()
        );
    }

}

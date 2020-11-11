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
import com.epam.pipeline.billingreportagent.model.pricing.AzureEAPricingMeter;
import com.epam.pipeline.billingreportagent.model.pricing.AzureRateCardPricingMeter;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AzureBlobStoragePriceListLoader extends AbstractAzureStoragePriceListLoader {

    private static final float ONE_THS_SCALE_FACTOR = 0.001f;
    private final String redundancyType;
    private final String blobStorageCategory;

    public AzureBlobStoragePriceListLoader(final CloudRegionLoader regionLoader,
                                           final AzureRateCardRawPriceLoader rawPriceLoader,
                                           final AzureEARawPriceLoader rawEAPriceLoader,
                                           final String blobStorageCategory,
                                           final String redundancyType) {
        super(regionLoader, rawPriceLoader, rawEAPriceLoader);
        this.redundancyType = redundancyType;
        this.blobStorageCategory = blobStorageCategory;
    }

    @Override
    protected Map<String, StoragePricing> extractRateCardPrices(final List<AzureRateCardPricingMeter> pricingMeters) {
        return pricingMeters.stream()
            .filter(meter -> GB_MONTH_UNIT.equals(meter.getUnit()))
            .filter(meter -> meter.getMeterSubCategory().equals(blobStorageCategory))
            .filter(meter -> meter.getMeterName().startsWith(String.format(DATA_STORE_METER_TEMPLATE, redundancyType)))
            .collect(Collectors.toMap(AzureRateCardPricingMeter::getMeterRegion, this::convertAzureRateCardPricing));
    }

    @Override
    protected Map<String, StoragePricing> extractEAPrices(List<AzureEAPricingMeter> pricingMeters) {
        return pricingMeters.stream()
                .filter(meter -> ONE_THS_GB_MONTH_UNIT.equals(meter.getUnit()))
                .filter(meter -> meter.getMeterSubCategory().contains(blobStorageCategory))
                .filter(meter -> meter.getMeterName().startsWith(String.format(DATA_STORE_METER_TEMPLATE, redundancyType)))
                .collect(Collectors.toMap(AzureEAPricingMeter::getMeterRegion,
                        azureEAPricingMeter -> convertAzureEAPricing(azureEAPricingMeter, ONE_THS_SCALE_FACTOR)));
    }
}

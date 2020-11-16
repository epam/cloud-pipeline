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
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingMeter;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AzureFilesStoragePriceListLoader extends AbstractAzureStoragePriceListLoader {

    private static final String FILES_SUBCATEGORY = "Files v2";

    private final String storageTier;

    public AzureFilesStoragePriceListLoader(final CloudRegionLoader regionLoader,
                                            final AzureRawPriceLoader rawPriceLoader,
                                            final String storageTier) {
        super(regionLoader, rawPriceLoader);
        this.storageTier = storageTier;
    }

    @Override
    protected Map<String, StoragePricing> extractPrices(final List<AzurePricingMeter> pricingMeters) {
        return pricingMeters
            .stream()
            .filter(meter -> GB_MONTH_UNIT.equals(meter.getUnit()))
            .filter(meter -> STORAGE_CATEGORY.equals(meter.getMeterCategory()))
            .filter(meter -> FILES_SUBCATEGORY.equals(meter.getMeterSubCategory()))
            .filter(meter -> meter.getMeterName().equals(String.format(DATA_STORE_METER_TEMPLATE, storageTier)))
            .collect(Collectors.toMap(AzurePricingMeter::getMeterRegion, this::convertAzurePricing));
    }
}

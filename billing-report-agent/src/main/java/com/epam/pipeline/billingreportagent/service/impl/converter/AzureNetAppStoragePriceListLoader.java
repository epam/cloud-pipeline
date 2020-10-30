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
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AzureNetAppStoragePriceListLoader extends AbstractAzureStoragePriceListLoader {

    private static final String AZURE_NETAPP_CATEGORY = "Azure NetApp Files";
    private static final String AZURE_CAPACITY_METER_TEMPLATE = "%s Capacity";
    private static final int HRS_PER_DAY = 24;

    private final String netAppTier;

    public AzureNetAppStoragePriceListLoader(final CloudRegionLoader regionLoader, final String netAppTier) {
        super(regionLoader);
        this.netAppTier = netAppTier;
    }

    @Override
    protected Map<String, StoragePricing> extractPrices(final List<AzurePricingMeter> pricingMeters) {
        return pricingMeters.stream()
            .filter(meter -> AZURE_NETAPP_CATEGORY.equals(meter.getMeterCategory()))
            .filter(meter -> meter.getMeterName().equals(String.format(AZURE_CAPACITY_METER_TEMPLATE, netAppTier)))
            .filter(meter -> StringUtils.isNotEmpty(meter.getMeterRegion()))
            .collect(Collectors.toMap(AzurePricingMeter::getMeterRegion, this::convertAzurePricingHourlyToMonthly));
    }

    private StoragePricing convertAzurePricingHourlyToMonthly(final AzurePricingMeter azurePricing) {
        return convertAzurePricing(azurePricing, HRS_PER_DAY * countDaysInCurrentMonth());
    }

    private int countDaysInCurrentMonth() {
        final LocalDate date = LocalDate.now();
        final YearMonth yearMonthObject = YearMonth.of(date.getYear(), date.getMonthValue());
        return yearMonthObject.lengthOfMonth();
    }
}

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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class StoragePricingService {

    private final Map<String, StoragePricing> storagePriceListGb = new HashMap<>();
    private BigDecimal defaultPriceGb;
    private StoragePriceListLoader priceListLoader;

    public StoragePricingService(@NonNull final StoragePriceListLoader priceListLoader) {
        this.priceListLoader = priceListLoader;
        updatePrices();
    }

    public StoragePricingService(final Map<String, StoragePricing> fixedPriceList) {
        this.storagePriceListGb.putAll(fixedPriceList);
        this.defaultPriceGb = calculateDefaultPriceGb();
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void updatePrices() {
        try {
            if (priceListLoader != null) {
                final Map<String, StoragePricing> priceList = priceListLoader.loadFullPriceList();
                storagePriceListGb.putAll(priceList);
                defaultPriceGb = calculateDefaultPriceGb();
            }
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Can't instantiate %s storage price list!",
                                                          priceListLoader.getProvider().name()));
        }
    }

    public StoragePricing getRegionPricing(final String region) {
        return storagePriceListGb.get(region);
    }

    public BigDecimal getDefaultPriceGb() {
        return this.defaultPriceGb;
    }

    private BigDecimal calculateDefaultPriceGb() {
        return storagePriceListGb.values()
            .stream()
            .flatMap(pricing -> pricing.getPrices().stream())
            .map(StoragePricing.StoragePricingEntity::getPriceCentsPerGb)
            .filter(price -> !BigDecimal.ZERO.equals(price))
            .max(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalStateException(String.format("No %s storage prices loaded!",
                                                                       priceListLoader.getProvider().name())));
    }
}

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

    /**
     * A price list that can't be loaded here doesn't stop the agent: the run billing and the other storage
     * billings don't need it, and every storage synchronization calls {@link #updatePrices()} again.
     */
    public StoragePricingService(@NonNull final StoragePriceListLoader priceListLoader) {
        this.priceListLoader = priceListLoader;
        try {
            updatePrices();
        } catch (IllegalStateException e) {
            log.error("{} It will be loaded again at the next storage billing synchronization.",
                    e.getMessage(), e);
        }
    }

    public StoragePricingService(final Map<String, StoragePricing> fixedPriceList) {
        this.storagePriceListGb.putAll(fixedPriceList);
        this.defaultPriceGb = calculateDefaultPriceGb(storagePriceListGb);
    }

    /**
     * Reloads the price list. If that fails, the previously loaded prices stay in use.
     *
     * @throws IllegalStateException if the price list fails to load and no prices were loaded before
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void updatePrices() {
        if (priceListLoader == null) {
            return;
        }
        try {
            final Map<String, StoragePricing> loadedPriceList = priceListLoader.loadFullPriceList();
            final Map<String, StoragePricing> updatedPriceList = new HashMap<>(storagePriceListGb);
            updatedPriceList.putAll(loadedPriceList);
            final BigDecimal updatedDefaultPrice = calculateDefaultPriceGb(updatedPriceList);
            storagePriceListGb.putAll(loadedPriceList);
            defaultPriceGb = updatedDefaultPrice;
        } catch (Exception e) {
            final String provider = priceListLoader.getProvider().name();
            if (defaultPriceGb == null) {
                throw new IllegalStateException(
                        String.format("Can't instantiate %s storage price list!", provider), e);
            }
            log.error(String.format("Can't update %s storage price list, the previously loaded prices are used.",
                                    provider), e);
        }
    }

    public StoragePricing getRegionPricing(final String region) {
        return storagePriceListGb.get(region);
    }

    public BigDecimal getDefaultPriceGb() {
        return this.defaultPriceGb;
    }

    private BigDecimal calculateDefaultPriceGb(final Map<String, StoragePricing> priceList) {
        return priceList.values()
            .stream()
            .flatMap(pricing -> pricing.getPrices().stream())
            .map(StoragePricing.StoragePricingEntity::getPriceCentsPerGb)
            .filter(price -> !BigDecimal.ZERO.equals(price))
            .max(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalStateException(String.format("No %s storage prices loaded!",
                                                                       priceListLoader.getProvider().name())));
    }
}

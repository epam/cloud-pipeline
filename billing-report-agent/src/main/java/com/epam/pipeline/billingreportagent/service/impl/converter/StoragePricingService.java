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
import com.epam.pipeline.utils.StreamUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class StoragePricingService {

    private static final String DEFAULT_STORAGE_CLASS = "STANDARD";
    private final Map<String, StoragePricing> storagePriceListGb = new HashMap<>();
    private Map<String, BigDecimal> defaultPriceGbByStorageClass;
    private StoragePriceListLoader priceListLoader;

    public StoragePricingService(@NonNull final StoragePriceListLoader priceListLoader) {
        this.priceListLoader = priceListLoader;
        updatePrices();
    }

    public StoragePricingService(final Map<String, StoragePricing> fixedPriceList) {
        this.storagePriceListGb.putAll(fixedPriceList);
        this.defaultPriceGbByStorageClass = calculateDefaultPriceGb();
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void updatePrices() {
        try {
            if (priceListLoader != null) {
                final Map<String, StoragePricing> priceList = priceListLoader.loadFullPriceList();
                storagePriceListGb.putAll(priceList);
                defaultPriceGbByStorageClass = calculateDefaultPriceGb();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(String.format("Can't instantiate %s storage price list!",
                                                          priceListLoader.getProvider().name()));
        }
    }

    public StoragePricing getRegionPricing(final String region) {
        return storagePriceListGb.get(region);

    }

    public BigDecimal getDefaultPriceGb() {
        return getDefaultPriceGb(DEFAULT_STORAGE_CLASS);
    }

    public BigDecimal getDefaultPriceGb(final String storageClass) {
        return Optional.ofNullable(this.defaultPriceGbByStorageClass.get(storageClass))
                .orElseGet(() -> defaultPriceGbByStorageClass.get(DEFAULT_STORAGE_CLASS));
    }

    private Map<String, BigDecimal> calculateDefaultPriceGb() {
        return StreamUtils.grouped(
                storagePriceListGb.values()
                        .stream()
                        .flatMap(pricing ->
                                pricing.getStorageClasses().stream()
                                        .map(sc -> ImmutablePair.of(sc, pricing.getPrices(sc)))
                        ).sorted(Comparator.comparing(Pair::getKey)),
                Comparator.comparing(Pair::getKey)
        ).filter(CollectionUtils::isNotEmpty)
        .map(allRegionPricesForStorageClass -> {
            final String sc = allRegionPricesForStorageClass.stream().findAny().map(Pair::getKey).orElse(null);
            final BigDecimal maxPrice = allRegionPricesForStorageClass.stream().map(Pair::getValue)
                    .flatMap(Collection::stream)
                    .map(StoragePricing.StoragePricingEntity::getPriceCentsPerGb)
                    .filter(price -> !BigDecimal.ZERO.equals(price))
                    .max(Comparator.naturalOrder()).orElse(null);
            if (sc == null || maxPrice == null) {
                return null;
            }
            return ImmutablePair.of(sc, maxPrice);
        }).filter(Objects::nonNull)
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}

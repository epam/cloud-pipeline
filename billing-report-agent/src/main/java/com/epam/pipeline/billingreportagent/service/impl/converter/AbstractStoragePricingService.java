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
import com.epam.pipeline.entity.region.CloudProvider;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@SuppressWarnings("checkstyle:magicNumber")
public abstract class AbstractStoragePricingService {

    public static final int CENTS_IN_DOLLAR = 100;
    public static final int BYTES_TO_GB = 1 << 30;
    public static final int PRECISION = 5;

    private final Map<String, StoragePricing> storagePriceListGb = new HashMap<>();
    private BigDecimal defaultPriceGb;
    private final String storageServiceGroup;

    public AbstractStoragePricingService(final String storageServiceGroup) {
        this.storageServiceGroup = storageServiceGroup;
        updatePrices();
    }

    protected abstract void loadFullPriceList() throws Exception;
    protected abstract CloudProvider getProvider();

    public AbstractStoragePricingService(final String storageServiceGroup,
                                         final Map<String, StoragePricing> initialPriceList) {
        this.storagePriceListGb.putAll(initialPriceList);
        this.storageServiceGroup = storageServiceGroup;
        this.defaultPriceGb = calculateDefaultPriceGb();
    }

    public void updatePrices() {
        try {
            loadFullPriceList();
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Can't instantiate %s storage price list!",
                                                          getProvider().name()));
        }
        this.defaultPriceGb = calculateDefaultPriceGb();
    }

    public StoragePricing getRegionPricing(final String region) {
        return storagePriceListGb.get(region);
    }

    public BigDecimal getDefaultPriceGb() {
        return this.defaultPriceGb;
    }

    protected String getStorageServiceGroup() {
        return storageServiceGroup;
    }

    protected StoragePricing putRegionPricing(final String region, final StoragePricing pricing) {
        return storagePriceListGb.put(region, pricing);
    }

    private BigDecimal calculateDefaultPriceGb() {
        return storagePriceListGb.values()
            .stream()
            .flatMap(pricing -> pricing.getPrices().stream())
            .map(StoragePricing.StoragePricingEntity::getPriceCentsPerGb)
            .filter(price -> !BigDecimal.ZERO.equals(price))
            .max(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalStateException(String.format("No %s storage prices loaded!",
                                                                       getProvider().name())));
    }
}

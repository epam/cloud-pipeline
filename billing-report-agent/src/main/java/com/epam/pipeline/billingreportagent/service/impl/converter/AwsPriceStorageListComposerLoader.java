/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.apache.commons.collections4.ListUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composition class to aggregate prices from more than 1 price list loader.
 * */
@Slf4j
public class AwsPriceStorageListComposerLoader implements StoragePriceListLoader {

    private final List<AwsStoragePriceListLoader> listLoaders;

    public AwsPriceStorageListComposerLoader(AwsStoragePriceListLoader... listLoaders) {
        this.listLoaders = Arrays.asList(listLoaders);
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public Map<String, StoragePricing> loadFullPriceList() {
        final Map<String, StoragePricing> mergedPrices = new HashMap<>();
        for (AwsStoragePriceListLoader loader : ListUtils.emptyIfNull(listLoaders)) {
            final Map<String, StoragePricing> pricesByRegion = loader.loadFullPriceList();
            pricesByRegion.forEach((region, prices) -> {
                final StoragePricing toMergeWith = mergedPrices.computeIfAbsent(region, (r) -> new StoragePricing());
                prices.getPrices().forEach(toMergeWith::addPrices);
            });
        }
        return mergedPrices;
    }

}

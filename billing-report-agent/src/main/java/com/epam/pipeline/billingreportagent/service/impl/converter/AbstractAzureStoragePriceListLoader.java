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

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingMeter;
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingResult;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public abstract class AbstractAzureStoragePriceListLoader implements StoragePriceListLoader {

    protected static final String GB_MONTH_UNIT = "1 GB/Month";
    protected static final String STORAGE_CATEGORY = "Storage";
    protected static final String DATA_STORE_METER_TEMPLATE = "%s Data Stored";

    private CloudRegionLoader regionLoader;
    private AzureRawPriceLoader rawPriceLoader;
    private final Logger logger;

    public AbstractAzureStoragePriceListLoader(final CloudRegionLoader regionLoader,
                                               final AzureRawPriceLoader rawPriceLoader) {
        this.regionLoader = regionLoader;
        this.rawPriceLoader = rawPriceLoader;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    @Override
    public Map<String, StoragePricing> loadFullPriceList() throws Exception {
        final List<AzureRegion> activeAzureRegions = regionLoader.loadAllEntities().stream()
            .map(EntityContainer::getEntity)
            .filter(AzureRegion.class::isInstance)
            .map(AzureRegion.class::cast)
            .collect(Collectors.toList());
        Assert.isTrue(CollectionUtils.isNotEmpty(activeAzureRegions), "No Azure regions found in current deployment!");
        assertOffersAndRegionMetersAreUnique(activeAzureRegions);
        final HashSet<String> regionCodes = new HashSet<>();
        return activeAzureRegions.stream()
            .filter(region -> regionCodes.add(region.getRegionCode()))
            .collect(Collectors.toMap(AbstractCloudRegion::getRegionCode, this::getPricingForSpecificRegion));
    }

    protected abstract Map<String, StoragePricing> extractPrices(List<AzurePricingMeter> pricingMeters);

    protected StoragePricing convertAzurePricing(final AzurePricingMeter azurePricing) {
        return convertAzurePricing(azurePricing, 1);
    }

    protected StoragePricing convertAzurePricing(final AzurePricingMeter azurePricing, final int scaleFactor) {
        final Map<String, Float> rates = azurePricing.getMeterRates();
        logger.debug("Reading price from {}", azurePricing);
        final StoragePricing storagePricing = new StoragePricing();
        final List<StoragePricing.StoragePricingEntity> pricing = rates.entrySet().stream()
            .map(e -> {
                final long rangeStart = Float.valueOf(e.getKey()).longValue();
                final BigDecimal cost = BigDecimal.valueOf(e.getValue().doubleValue() * scaleFactor * CENTS_IN_DOLLAR);
                final long beginRangeBytes = rangeStart * BYTES_TO_GB;
                return new StoragePricing.StoragePricingEntity(beginRangeBytes, null, cost);
            })
            .sorted(Comparator.comparing(StoragePricing.StoragePricingEntity::getBeginRangeBytes))
            .collect(Collectors.toList());
        final int pricingSize = pricing.size();
        for (int i = 0; i < pricingSize - 1; i++) {
            pricing.get(i).setEndRangeBytes(pricing.get(i + 1).getBeginRangeBytes());
        }
        pricing.get(pricingSize - 1).setEndRangeBytes(Long.MAX_VALUE);
        storagePricing.getPrices().addAll(pricing);
        return storagePricing;
    }

    private void assertOffersAndRegionMetersAreUnique(final List<AzureRegion> activeAzureRegions) {
        final Map<String, Set<String>> regionOfferMapping =
            mapRegionsOnFunctionResult(activeAzureRegions, rawPriceLoader::getRegionOfferAndSubscription);
        final Map<String, Set<String>> regionMeterMapping =
            mapRegionsOnFunctionResult(activeAzureRegions, AzureRegion::getMeterRegionName);
        for (final String regionName : regionOfferMapping.keySet()) {
            if (regionOfferMapping.get(regionName).size() > 1) {
                throw new IllegalArgumentException(
                    String.format("Regions with the same name [%s] specifies different"
                                  + " offerId or subscription!", regionName));
            }
            if (regionMeterMapping.get(regionName).size() > 1) {
                throw new IllegalArgumentException(
                    String.format("Regions with the same name [%s] specifies different"
                                  + " meter region name!", regionName));
            }
        }
    }

    private Map<String, Set<String>> mapRegionsOnFunctionResult(final List<AzureRegion> activeAzureRegions,
                                                                final Function<AzureRegion, String> function) {
        return activeAzureRegions.stream()
            .collect(Collectors.groupingBy(AbstractCloudRegion::getRegionCode,
                                           Collector.of(HashSet::new,
                                               (set, region) -> set.add(function.apply(region)),
                                               (left, right) -> {
                                                   left.addAll(right);
                                                   return left;
                                               },
                                               Function.identity())));
    }

    private StoragePricing getStoragePricingForRegion(final Response<AzurePricingResult> pricingResponse,
                                                      final String meterRegion) {
        final List<AzurePricingMeter> azurePricingMeters = Optional.ofNullable(pricingResponse.body())
            .map(AzurePricingResult::getMeters)
            .orElse(Collections.emptyList());
        final Map<String, StoragePricing> fullStoragePricingMap = extractPrices(azurePricingMeters);
        final StoragePricing storagePricing = fullStoragePricingMap
            .computeIfAbsent(meterRegion, region -> {
                logger.warn(String.format("No price is found for [%s], searching for the default value.", meterRegion));
                return fullStoragePricingMap.getOrDefault(StringUtils.EMPTY, null);
            });
        if (storagePricing == null) {
            throw new IllegalArgumentException(
                String.format("No [%s] region/default value is presented in prices retrieved!", meterRegion));
        }
        return storagePricing;
    }

    private StoragePricing getPricingForSpecificRegion(final AzureRegion region) {
        try {
            final Response<AzurePricingResult> azureRegionPricing =
                rawPriceLoader.getRawPricesUsingPipelineRegion(region);
            logger.info("Reading prices for [{}, meterName={}]", region.getName(), region.getMeterRegionName());
            return getStoragePricingForRegion(azureRegionPricing, region.getMeterRegionName());
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Error during prices loading: %s", e.getMessage()));
        }
    }
}

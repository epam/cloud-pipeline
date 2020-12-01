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
import com.epam.pipeline.billingreportagent.model.pricing.*;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public abstract class AbstractAzureStoragePriceListLoader implements StoragePriceListLoader {

    protected static final String GB_MONTH_UNIT = "GB/Month";
    protected static final int HRS_PER_MONTH = 730;
    protected static final String GB_HOUR_DIMENSION = "GB/Hour";
    protected static final String GIB_HOUR_DIMENSION = "GiB/Hour";
    protected static final String STORAGE_CATEGORY = "Storage";
    protected static final String DATA_STORE_METER_TEMPLATE = "%s Data Stored";
    private final static Pattern AZURE_UNIT_PATTERN = Pattern.compile("(\\d+)\\s([\\w/]+)");

    private CloudRegionLoader regionLoader;
    private AzureRateCardRawPriceLoader rawRateCardPriceLoader;
    private AzureEARawPriceLoader rawEAPriceLoader;

    private final Logger logger;

    public AbstractAzureStoragePriceListLoader(final CloudRegionLoader regionLoader,
                                               final AzureRateCardRawPriceLoader rawRateCardPriceLoader,
                                               AzureEARawPriceLoader rawEAPriceLoader) {
        this.regionLoader = regionLoader;
        this.rawRateCardPriceLoader = rawRateCardPriceLoader;
        this.rawEAPriceLoader = rawEAPriceLoader;
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

    protected abstract Map<String, StoragePricing> extractPrices(List<AzurePricingEntity> pricingMeters);

    protected StoragePricing convertAzurePricing(final AzurePricingEntity azurePricing) {
        return convertAzurePricing(azurePricing, 1L);
    }

    protected StoragePricing convertAzurePricing(final AzurePricingEntity azurePricing, final double scaleFactor) {
        final Map<String, Float> rates = azurePricing.getMeterRates();
        logger.debug("Converting price from {}", azurePricing);
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
            mapRegionsOnFunctionResult(activeAzureRegions, rawRateCardPriceLoader::getRegionOfferAndSubscription);
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

    private StoragePricing getStoragePricingForRegion(final List<AzurePricingEntity> pricingResponse,
                                                      final String meterRegion) {
        final List<AzurePricingEntity> azureRateCardPricingMeters = Optional.ofNullable(pricingResponse)
            .orElse(Collections.emptyList());
        final Map<String, StoragePricing> fullStoragePricingMap = extractPrices(azureRateCardPricingMeters);
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
            List<AzurePricingEntity> priceList;
            if (BooleanUtils.isTrue(region.getEnterpriseAgreements())) {
                logger.info("Reading EA prices for [{}, meterName={}]", region.getName(), region.getMeterRegionName());
                priceList = rawEAPriceLoader.getRawPricesUsingPipelineRegion(region);
            } else {
                logger.info("Reading RateCard prices for [{}, meterName={}]", region.getName(), region.getMeterRegionName());
                priceList = rawRateCardPriceLoader.getRawPricesUsingPipelineRegion(region);
            }
            return getStoragePricingForRegion(priceList, region.getMeterRegionName());
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Error during prices loading: %s", e.getMessage()));
        }
    }
    protected double getScaleFactor(String unit) {
        double scaleFactor = 1d;
        Matcher match = AZURE_UNIT_PATTERN.matcher(unit);
        if (match.matches()) {
            int amount = Integer.parseInt(match.group(1));
            if (amount > 1) {
                scaleFactor = scaleFactor / amount;
            }

            if (match.group(2).contains(GB_HOUR_DIMENSION) || match.group(2).contains(GIB_HOUR_DIMENSION)) {
                scaleFactor = scaleFactor * HRS_PER_MONTH;
            }
            return scaleFactor;
        } else {
            throw new IllegalArgumentException("Wrong Azure Unit string: " + unit);
        }
    }

}

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

import com.amazonaws.regions.Regions;
import com.amazonaws.services.pricing.AWSPricing;
import com.amazonaws.services.pricing.AWSPricingClientBuilder;
import com.amazonaws.services.pricing.model.Filter;
import com.amazonaws.services.pricing.model.GetProductsRequest;
import com.amazonaws.services.pricing.model.GetProductsResult;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class AwsStoragePricingService extends AbstractStoragePricingService {

    public AwsStoragePricingService(final String awsStorageServiceName) {
        super(awsStorageServiceName);
    }

    public AwsStoragePricingService(final String awsStorageServiceName,
                                    final Map<String, StoragePricing> initialPriceList) {
        super(awsStorageServiceName, initialPriceList);
    }

    @Override
    public void loadFullPriceList() {
        loadPricesInJson(getStorageServiceGroup()).forEach(price -> {
            try {
                final JsonNode regionInfo = new ObjectMapper().readTree(price);
                final String regionName = regionInfo.path("product").path("attributes").path("location").asText();
                getRegionFromFullLocation(regionName).ifPresent(region -> fillPricingInfoForRegion(region, regionInfo));
            } catch (IOException e) {
                log.error("Can't instantiate AWS storage price list!");
            }
        });
    }

    @Override
    protected CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    private void fillPricingInfoForRegion(final Regions region, final JsonNode regionInfo) {
        final StoragePricing pricing = new StoragePricing();
        regionInfo.findParents("pricePerUnit").stream()
            .map(this::extractPricingFromJson)
            .forEach(pricing::addPrice);
        putRegionPricing(region.getName(), pricing);
    }

    private List<String> loadPricesInJson(final String awsStorageServiceName) {
        final List<String> allPrices = new ArrayList<>();
        final Filter filter = new Filter();
        filter.setType("TERM_MATCH");
        filter.setField("productFamily");
        filter.setValue("Storage");
        filter.setField("storageClass");
        filter.setValue("General Purpose");

        String nextToken = StringUtils.EMPTY;
        do {
            final GetProductsRequest request = new GetProductsRequest()
                .withServiceCode(awsStorageServiceName)
                .withFilters(filter)
                .withNextToken(nextToken)
                .withFormatVersion("aws_v1");

            final AWSPricing awsPricingService = AWSPricingClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
                .build();

            final GetProductsResult result = awsPricingService.getProducts(request);
            allPrices.addAll(result.getPriceList());
            nextToken = result.getNextToken();
        } while (nextToken != null);
        return allPrices;
    }

    private Optional<Regions> getRegionFromFullLocation(final String location) {
        for (Regions region : Regions.values()) {
            if (region.getDescription().equals(location)) {
                return Optional.of(region);
            }
        }
        log.warn("Can't parse location: " + location);
        return Optional.empty();
    }

    private StoragePricing.StoragePricingEntity extractPricingFromJson(final JsonNode priceDimension) {
        final BigDecimal priceGb =
            new BigDecimal(priceDimension.path("pricePerUnit").path("USD").asDouble(), new MathContext(
                PRECISION))
                .multiply(BigDecimal.valueOf(CENTS_IN_DOLLAR));
        final long beginRange =
            priceDimension.path("beginRange").asLong() * BYTES_TO_GB;
        final long endRange = priceDimension.path("endRange").asLong();
        return new StoragePricing.StoragePricingEntity(beginRange,
                                                       endRange == 0
                                                       ? Long.MAX_VALUE
                                                       : endRange * BYTES_TO_GB,
                                                       priceGb);
    }
}

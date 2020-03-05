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
import com.epam.pipeline.billingreportagent.model.pricing.AwsPriceRate;
import com.epam.pipeline.billingreportagent.model.pricing.AwsPricingCard;
import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class AwsStoragePriceListLoader implements StoragePriceListLoader {

    private static final String DOLLARS = "USD";

    private final String awsStorageServiceName;
    private final ObjectMapper mapper;

    public AwsStoragePriceListLoader(final String awsStorageServiceName) {
        this.awsStorageServiceName = awsStorageServiceName;
        this.mapper = new ObjectMapper();
    }

    @Override
    public Map<String, StoragePricing> loadFullPriceList() {
        final Map<String, StoragePricing> fullPriceList = new HashMap<>();
        loadAwsPricingCards(awsStorageServiceName).forEach(price -> {
            final String regionName = price.getProduct().getAttributes().get("location");
            getRegionFromFullLocation(regionName)
                .ifPresent(region -> fullPriceList.put(region.getName(),
                                                       convertAwsPricing(price.getPriceDimensions())));
        });
        return fullPriceList;
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    private StoragePricing convertAwsPricing(final List<AwsPriceRate> rates) {
        final StoragePricing pricing = new StoragePricing();
        rates.forEach(rate -> {
            final BigDecimal priceGb = new BigDecimal(rate.getPricePerUnit().get(DOLLARS), new MathContext(PRECISION))
                .multiply(BigDecimal.valueOf(CENTS_IN_DOLLAR));
            final Long beginRange = rate.getBeginRange() * BYTES_TO_GB;
            final Long endRange = rate.getEndRange().equals(Long.MAX_VALUE)
                                  ? Long.MAX_VALUE
                                  : rate.getEndRange() * BYTES_TO_GB;
            final StoragePricing.StoragePricingEntity pricingEntity =
                new StoragePricing.StoragePricingEntity(beginRange, endRange, priceGb);
            pricing.addPrice(pricingEntity);
        });
        return pricing;
    }

    private List<AwsPricingCard> loadAwsPricingCards(final String awsStorageServiceName) {
        final List<AwsPricingCard> allPrices = new ArrayList<>();
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
            result.getPriceList().stream()
                .map(jsonStr -> {
                    try {
                        final AwsPricingCard pricingCard = mapper.readValue(jsonStr, AwsPricingCard.class);
                        final JsonNode jsonNode = mapper.readTree(jsonStr);
                        final List<AwsPriceRate> prices = jsonNode.findParents("pricePerUnit").stream()
                            .map(JsonNode::toString)
                            .map(priceRate -> {
                                try {
                                    final AwsPriceRate awsPriceRate = mapper.readValue(priceRate, AwsPriceRate.class);
                                    return awsPriceRate;
                                } catch (IOException e) {
                                    throw new IllegalStateException("Error during pricing rates parsing!");
                                }
                            }).collect(Collectors.toList());
                        pricingCard.setPriceDimensions(prices);
                        return pricingCard;
                    } catch (IOException e) {
                        throw new IllegalStateException("Error during general pricing info parsing!");
                    }
                })
                .forEach(allPrices::add);
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
}

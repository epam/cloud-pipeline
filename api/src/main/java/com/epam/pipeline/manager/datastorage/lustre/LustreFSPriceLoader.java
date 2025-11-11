/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.lustre;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.pricing.AWSPricing;
import com.amazonaws.services.pricing.AWSPricingClientBuilder;
import com.amazonaws.services.pricing.model.AWSPricingException;
import com.amazonaws.services.pricing.model.Filter;
import com.amazonaws.services.pricing.model.GetProductsRequest;
import com.amazonaws.services.pricing.model.GetProductsResult;
import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.entity.pricing.aws.AwsPriceDimensions;
import com.epam.pipeline.entity.pricing.aws.AwsPriceRate;
import com.epam.pipeline.entity.pricing.aws.AwsPricingCard;
import com.epam.pipeline.entity.region.AwsRegion;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class LustreFSPriceLoader {
    private static final String FSX_SERVICE_CODE = "AmazonFSx";
    private static final String TERM_MATCH = "TERM_MATCH";
    private static final String AWS_PRICE_FORMAT_VERSION = "aws_v1";
    private static final int HOURS_IN_MONTH = 730;
    private static final int SCALE = 7;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public BigDecimal getPricePerHour(final LustreFS lustre, final AwsRegion region) {
        final List<AwsPricingCard> prices = loadPrices(lustre, region);

        if (CollectionUtils.isEmpty(prices)) {
            return null;
        }

        if (prices.size() > 1) {
            log.debug("Multiple price cards were loaded but only one was expected");
        }

        final double priceGbPerMonth = prices.get(0).getTerms().getOnDemand().values().stream()
                .map(AwsPriceDimensions::getPriceDimensions)
                .map(Map::values)
                .flatMap(Collection::stream)
                .map(AwsPriceRate::getPricePerUnit)
                .mapToDouble(pricePerUnit -> pricePerUnit.getOrDefault("USD", 0.0))
                .sum();

        return BigDecimal.valueOf(lustre.getCapacityGb())
                .multiply(BigDecimal.valueOf(priceGbPerMonth))
                .divide(BigDecimal.valueOf(HOURS_IN_MONTH), SCALE, RoundingMode.HALF_EVEN)
                .setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    private List<AwsPricingCard> loadPrices(final LustreFS lustre, final AwsRegion region) {
        try{
            final AWSPricing pricingClient = AWSPricingClientBuilder
                    .standard()
                    .withRegion(Regions.EU_CENTRAL_1)
                    .build();

            final List<AwsPricingCard> allPrices = new ArrayList<>();
            String nextToken = null;
            do {
                final GetProductsRequest request = new GetProductsRequest()
                        .withServiceCode(FSX_SERVICE_CODE)
                        .withFormatVersion(AWS_PRICE_FORMAT_VERSION)
                        .withNextToken(nextToken)
                        .withFilters(getFilters(lustre, region));

                final GetProductsResult result = pricingClient.getProducts(request);
                result.getPriceList().stream()
                        .map(this::parseAwsPricingCard)
                        .forEach(allPrices::add);

                nextToken = result.getNextToken();
            } while (nextToken != null);

            return allPrices;
        } catch (final AWSPricingException exception) {
            log.error(exception.getMessage(), exception);
            return Collections.emptyList();
        }
    }

    private List<Filter> getFilters(final LustreFS lustre, final AwsRegion region) {
        final List<Filter> filters = new ArrayList<>();
        filters.add(filter("productFamily", "Storage"));
        filters.add(filter("fileSystemType", "Lustre"));
        filters.add(filter("storageType", "SSD"));
        filters.add(filter("regionCode", region.getRegionCode()));
        filters.add(filter("throughputCapacity", getThroughputCapacityFilter(lustre)));
        return filters;
    }

    private Filter filter(final String filed, final String value) {
        return new Filter()
                .withType(TERM_MATCH)
                .withField(filed)
                .withValue(value);
    }

    private AwsPricingCard parseAwsPricingCard(final String jsonStr) {
        try {
            return MAPPER.readValue(jsonStr, AwsPricingCard.class);
        } catch (IOException e) {
            throw new IllegalStateException("Error during AWS general pricing info parsing!");
        }
    }

    private String getThroughputCapacityFilter(final LustreFS lustre) {
        return StringUtils.isNotBlank(lustre.getDeploymentType()) && Objects.nonNull(lustre.getThroughput())
                && lustre.getDeploymentType().startsWith("PERSISTENT_")
                ? lustre.getThroughput().toString()
                : "N/A";
    }
}

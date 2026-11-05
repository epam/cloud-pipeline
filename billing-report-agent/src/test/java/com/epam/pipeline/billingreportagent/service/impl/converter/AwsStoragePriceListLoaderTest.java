/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.amazonaws.services.pricing.model.Filter;
import com.amazonaws.services.pricing.model.GetProductsRequest;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.model.pricing.AwsPricingCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AwsStoragePriceListLoaderTest {

    private static final String US_EAST_1_REGION = "us-east-1";
    private static final String US_EAST_1_LOCATION = "US East (N. Virginia)";
    private static final String EU_CENTRAL_1_REGION = "eu-central-1";
    private static final String EU_CENTRAL_1_LOCATION = "EU (Frankfurt)";
    private static final String GENERAL_PURPOSE = "General Purpose";
    private static final String S3_SERVICE = "AmazonS3";
    private static final String EFS_SERVICE = "AmazonEFS";
    private static final String ENDPOINT_TEMPLATE = "https://localhost/%s/index.json";
    private static final String TERM_KEY = "term";
    private static final String SKU_KEY = "sku";
    private static final String STANDARD_SKU = "STANDARD1";
    private static final String US_EAST_1_STANDARD_USAGE_TYPE = "TimedStorage-ByteHrs";
    private static final String INF = "Inf";
    private static final String S3_STANDARD_TIER_1_USD = "0.023";
    private static final double S3_STANDARD_TIER_1_CENTS = 2.3;
    private static final double S3_STANDARD_TIER_2_CENTS = 2.2;
    private static final double S3_STANDARD_TIER_3_CENTS = 2.1;
    private static final double S3_STANDARD_EU_CENTRAL_1_CENTS = 2.45;
    private static final double EFS_STANDARD_CENTS = 30.0;

    @Test
    public void loadS3FullPriceListShouldTakeOnlyStandardTimedStorageTest() throws IOException {
        final String priceList = priceList(S3_SERVICE,
                product(STANDARD_SKU, US_EAST_1_LOCATION, GENERAL_PURPOSE, US_EAST_1_STANDARD_USAGE_TYPE,
                        tier(0, "51200", S3_STANDARD_TIER_1_USD), tier(51200, "512000", "0.022"),
                        tier(512000, INF, "0.021")),
                product("ANNOTATIONS1", US_EAST_1_LOCATION, GENERAL_PURPOSE, "Annotation-TimedStorage-ByteHrs",
                        tier(0, INF, S3_STANDARD_TIER_1_USD)),
                product("GLACIER1", US_EAST_1_LOCATION, "Archive", "TimedStorage-GlacierByteHrs",
                        tier(0, INF, "0.0036")),
                product("STANDARD2", EU_CENTRAL_1_LOCATION, GENERAL_PURPOSE, "EUC1-TimedStorage-ByteHrs",
                        tier(0, INF, "0.0245")),
                product("ANNOTATIONS2", EU_CENTRAL_1_LOCATION, GENERAL_PURPOSE,
                        "EUC1-Annotation-TimedStorage-ByteHrs", tier(0, INF, "0.0245")));

        final Map<String, StoragePricing> pricingMap = loader(S3_SERVICE, priceList).loadFullPriceList();

        Assertions.assertEquals(2, pricingMap.size());
        assertPrices(pricingMap.get(US_EAST_1_REGION),
                S3_STANDARD_TIER_1_CENTS, S3_STANDARD_TIER_2_CENTS, S3_STANDARD_TIER_3_CENTS);
        assertPrices(pricingMap.get(EU_CENTRAL_1_REGION), S3_STANDARD_EU_CENTRAL_1_CENTS);
    }

    @Test
    public void loadEfsFullPriceListShouldKeepRegionPrefixedStandardTimedStorageTest() throws IOException {
        final String priceList = priceList(EFS_SERVICE,
                product("EFS1", US_EAST_1_LOCATION, GENERAL_PURPOSE, "USE1-TimedStorage-ByteHrs",
                        tier(0, INF, "0.30")),
                product("EFS2", US_EAST_1_LOCATION, "Archive", "USE1-ArchiveTimedStorage-ByteHrs",
                        tier(0, INF, "0.008")),
                product("EFS3", US_EAST_1_LOCATION, "One Zone-General Purpose", "USE1-TimedStorage-Z-ByteHrs",
                        tier(0, INF, "0.16")));

        final Map<String, StoragePricing> pricingMap = loader(EFS_SERVICE, priceList).loadFullPriceList();

        Assertions.assertEquals(1, pricingMap.size());
        assertPrices(pricingMap.get(US_EAST_1_REGION), EFS_STANDARD_CENTS);
    }

    @Test
    public void loadFullPriceListShouldFailOnTwoStandardTimedStorageProductsForOneRegionTest() throws IOException {
        final String priceList = priceList(S3_SERVICE,
                product(STANDARD_SKU, US_EAST_1_LOCATION, GENERAL_PURPOSE, US_EAST_1_STANDARD_USAGE_TYPE,
                        tier(0, INF, S3_STANDARD_TIER_1_USD)),
                product("STANDARD2", US_EAST_1_LOCATION, GENERAL_PURPOSE, US_EAST_1_STANDARD_USAGE_TYPE,
                        tier(0, INF, S3_STANDARD_TIER_1_USD)));
        final AwsStoragePriceListLoader loader = loader(S3_SERVICE, priceList);

        final IllegalStateException error =
                Assertions.assertThrows(IllegalStateException.class, loader::loadFullPriceList);
        Assertions.assertEquals("More than one AmazonS3 storage price product for the region us-east-1!",
                error.getMessage());
    }

    @Test
    public void apiProductsRequestShouldFilterByProductFamilyAndStorageClassTest() {
        final AwsStoragePriceListLoader loader =
                new AwsStoragePriceListLoader(S3_SERVICE, PriceLoadingMode.API, ENDPOINT_TEMPLATE);

        final GetProductsRequest request = loader.buildProductsRequest(S3_SERVICE, "token");

        Assertions.assertEquals(S3_SERVICE, request.getServiceCode());
        Assertions.assertEquals("token", request.getNextToken());
        final Map<String, String> filters = request.getFilters().stream()
                .peek(filter -> Assertions.assertEquals("TERM_MATCH", filter.getType()))
                .collect(Collectors.toMap(Filter::getField, Filter::getValue));
        final Map<String, String> expectedFilters = new LinkedHashMap<>();
        expectedFilters.put("productFamily", "Storage");
        expectedFilters.put("storageClass", GENERAL_PURPOSE);
        Assertions.assertEquals(expectedFilters, filters);
    }

    @Test
    public void apiPriceListShouldTakeOnlyStandardTimedStorageTest() throws IOException {
        final AwsStoragePriceListLoader loader =
                new AwsStoragePriceListLoader(S3_SERVICE, PriceLoadingMode.API, ENDPOINT_TEMPLATE);
        final List<String> priceList = Arrays.asList(
                apiPricingCard(product(STANDARD_SKU, US_EAST_1_LOCATION, GENERAL_PURPOSE, US_EAST_1_STANDARD_USAGE_TYPE,
                        tier(0, INF, S3_STANDARD_TIER_1_USD))),
                apiPricingCard(product("ANNOTATIONS1", US_EAST_1_LOCATION, GENERAL_PURPOSE,
                        "Annotation-TimedStorage-ByteHrs", tier(0, INF, S3_STANDARD_TIER_1_USD))));

        final List<AwsPricingCard> cards = loader.parseStandardTimedStorageCards(priceList);

        Assertions.assertEquals(Collections.singletonList(STANDARD_SKU), cards.stream()
                .map(card -> card.getProduct().getSku())
                .collect(Collectors.toList()));
    }

    private static AwsStoragePriceListLoader loader(final String service,
                                                    final String priceList) throws IOException {
        final AwsStoragePriceListLoader loader = Mockito.spy(
                new AwsStoragePriceListLoader(service, PriceLoadingMode.JSON, ENDPOINT_TEMPLATE));
        Mockito.doReturn(priceList).when(loader).readStringFromURL(Mockito.anyString());
        return loader;
    }

    private static void assertPrices(final StoragePricing pricing, final double... expectedCentsPerGb) {
        Assertions.assertNotNull(pricing);
        Assertions.assertArrayEquals(expectedCentsPerGb, pricing.getPrices().stream()
                .mapToDouble(price -> price.getPriceCentsPerGb().doubleValue())
                .toArray());
    }

    private static Map<String, Object> tier(final long beginRange, final String endRange, final String usd) {
        final Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("beginRange", String.valueOf(beginRange));
        tier.put("endRange", endRange);
        tier.put("unit", "GB-Mo");
        tier.put("pricePerUnit", Collections.singletonMap("USD", usd));
        return tier;
    }

    @SafeVarargs
    private static Map<String, Object> product(final String sku, final String location, final String storageClass,
                                               final String usageType, final Map<String, Object>... tiers) {
        final Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("location", location);
        attributes.put("storageClass", storageClass);
        attributes.put("usagetype", usageType);
        final Map<String, Object> product = new LinkedHashMap<>();
        product.put(SKU_KEY, sku);
        product.put("productFamily", "Storage");
        product.put("attributes", attributes);
        final Map<String, Object> dimensions = new LinkedHashMap<>();
        for (int i = 0; i < tiers.length; i++) {
            dimensions.put(sku + ".TERM." + i, tiers[i]);
        }
        final Map<String, Object> term = new LinkedHashMap<>();
        term.put(SKU_KEY, sku);
        term.put("priceDimensions", dimensions);
        product.put(TERM_KEY, Collections.singletonMap(sku + ".TERM", term));
        return product;
    }

    private static String apiPricingCard(final Map<String, Object> product) throws IOException {
        final Map<String, Object> productOnly = new LinkedHashMap<>(product);
        final Object term = productOnly.remove(TERM_KEY);
        final Map<String, Object> card = new LinkedHashMap<>();
        card.put("product", productOnly);
        card.put("serviceCode", S3_SERVICE);
        card.put("terms", Collections.singletonMap("OnDemand", term));
        return new ObjectMapper().writeValueAsString(card);
    }

    @SafeVarargs
    private static String priceList(final String offerCode,
                                    final Map<String, Object>... products) throws IOException {
        final Map<String, Object> productsBySku = new LinkedHashMap<>();
        final Map<String, Object> termsBySku = new LinkedHashMap<>();
        for (final Map<String, Object> product : products) {
            final Map<String, Object> productOnly = new LinkedHashMap<>(product);
            termsBySku.put((String) product.get(SKU_KEY), productOnly.remove(TERM_KEY));
            productsBySku.put((String) product.get(SKU_KEY), productOnly);
        }
        final Map<String, Object> priceList = new LinkedHashMap<>();
        priceList.put("offerCode", offerCode);
        priceList.put("version", "20261104174445");
        priceList.put("products", productsBySku);
        priceList.put("terms", Collections.singletonMap("OnDemand", termsBySku));
        return new ObjectMapper().writeValueAsString(priceList);
    }
}

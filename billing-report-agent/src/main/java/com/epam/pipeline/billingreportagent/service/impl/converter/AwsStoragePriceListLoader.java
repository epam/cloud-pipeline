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
import com.epam.pipeline.billingreportagent.model.pricing.AwsPriceDimensions;
import com.epam.pipeline.billingreportagent.model.pricing.AwsPriceList;
import com.epam.pipeline.billingreportagent.model.pricing.AwsPriceRate;
import com.epam.pipeline.billingreportagent.model.pricing.AwsPricingCard;
import com.epam.pipeline.billingreportagent.model.pricing.AwsTerms;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.utils.StreamUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class AwsStoragePriceListLoader implements StoragePriceListLoader {

    private static final String AWS_PRICE_FORMAT_VERSION = "aws_v1";
    private static final String LOCATION_KEY = "location";
    private static final String TERM_MATCH_FILTER = "TERM_MATCH";
    private static final String PRODUCT_FAMILY_KEY = "productFamily";
    private static final String STORAGE = "Storage";
    private static final String STORAGE_CLASS_KEY = "storageClass";
    private static final String GENERAL_STORAGE = "General Purpose";
    private static final String ARCHIVE_STORAGE = "Archive";
    private static final String ARCHIVE_IR_STORAGE = "Archive Instant Retrieval";

    private static final String US_DOLLAR_CODE = "USD";
    private static final String VOLUME_TYPE_KEY = "volumeType";

    private static final Map<String, String> PRICE_LIST_STORAGE_CLASS_MAPPING = new HashMap<String, String>() {{
            put("Standard", "STANDARD");
            put("Glacier Instant Retrieval", "GLACIER_IR");
            put("Amazon Glacier", "GLACIER");
            put("Glacier Deep Archive", "DEEP_ARCHIVE");
        }};
    public static final String USAGETYPE = "usagetype";
    public static final String TIMED_STORAGE_BYTE_HRS_USAGETYPE = ".*TimedStorage.*ByteHrs";
    public static final String AMAZON_S3_SERVICE_NAME = "AmazonS3";

    private final String awsStorageServiceName;
    private final ObjectMapper mapper;
    private final PriceLoadingMode priceLoadingMode;
    private final String priceLoadingEndpoint;

    public AwsStoragePriceListLoader(final String awsStorageServiceName,
                                     final PriceLoadingMode mode,
                                     final String jsonPriceListEndpointTemplate) {
        this.awsStorageServiceName = awsStorageServiceName;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.priceLoadingMode = mode;
        if (mode.equals(PriceLoadingMode.JSON)
            && StringUtils.isNotEmpty(jsonPriceListEndpointTemplate)
            && !jsonPriceListEndpointTemplate.endsWith(".json")) {
            throw new IllegalArgumentException("Given endpoint template is incorrect for JSON mode!");
        }
        this.priceLoadingEndpoint = String.format(jsonPriceListEndpointTemplate, awsStorageServiceName);
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public Map<String, StoragePricing> loadFullPriceList() {
        return StreamUtils.grouped(
                loadAwsPricingCards(awsStorageServiceName, priceLoadingMode)
                        .stream()
                        .map(this::extractEntryFromAwsPricing)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .sorted(Comparator.comparing(Pair::getKey)),
                    Comparator.comparing(Pair::getKey)
        ).map(this::mergeRegionPrices)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private Optional<Pair<String, StoragePricing>> mergeRegionPrices(
            final List<Pair<String, StoragePricing>> entries) {
        return ListUtils.emptyIfNull(entries).stream().findAny().map(Map.Entry::getKey).map(r -> {
            StoragePricing pricing = new StoragePricing();
            entries.stream()
                    .map(Map.Entry::getValue)
                    .flatMap(storagePricing -> storagePricing.getPrices().entrySet().stream())
                    .forEach(p -> pricing.addPrices(p.getKey(), p.getValue()));
            return ImmutablePair.of(r, pricing);
        });
    }

    private Optional<Pair<String, StoragePricing>> extractEntryFromAwsPricing(final AwsPricingCard price) {
        final String regionName = price.getProduct().getAttributes().get(LOCATION_KEY);
        return getRegionFromFullLocation(regionName)
            .map(region -> {
                String storageClass = matchStorageClass(price);
                if (storageClass == null) {
                    return null;
                }
                final StoragePricing storagePricing = convertAwsPricing(storageClass, price.getTerms().getOnDemand());
                if (CollectionUtils.isEmpty(storagePricing.getPrices().values())) {
                    log.warn(String.format("Region [%s] doesn't have price rates specified in USD, will be skipped.",
                                           region.getName()));
                    return null;
                }
                return ImmutablePair.of(region.getName(), storagePricing);
            });
    }

    private static String matchStorageClass(AwsPricingCard price) {
        return Optional.ofNullable(price.getProduct().getAttributes().get(VOLUME_TYPE_KEY)).map(
                PRICE_LIST_STORAGE_CLASS_MAPPING::get
        ).orElse(null);
    }

    private StoragePricing convertAwsPricing(String storageClass, final Map<String, AwsPriceDimensions> allPrices) {
        final StoragePricing pricing = new StoragePricing();
        final List<AwsPriceRate> rates = CollectionUtils.emptyIfNull(allPrices.values()).stream()
            .map(AwsPriceDimensions::getPriceDimensions)
            .map(Map::values)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
        rates.stream()
            .filter(rate -> MapUtils.isNotEmpty(rate.getPricePerUnit()))
            .filter(rate -> rate.getPricePerUnit().containsKey(US_DOLLAR_CODE))
            .forEach(rate -> {
                final BigDecimal priceGb = new BigDecimal(rate.getPricePerUnit().get(US_DOLLAR_CODE),
                                                          new MathContext(PRECISION))
                    .multiply(BigDecimal.valueOf(CENTS_IN_DOLLAR));
                final Long beginRange = rate.getBeginRange() * BYTES_TO_GB;
                final Long endRange = rate.getEndRange().equals(Long.MAX_VALUE)
                                      ? Long.MAX_VALUE
                                      : rate.getEndRange() * BYTES_TO_GB;
                final StoragePricing.StoragePricingEntity pricingEntity =
                    new StoragePricing.StoragePricingEntity(beginRange, endRange, priceGb);
                pricing.addPrice(storageClass, pricingEntity);
            });
        return pricing;
    }

    private List<AwsPricingCard> loadAwsPricingCards(final String awsStorageServiceName, final PriceLoadingMode mode) {
        switch (mode) {
            case API:
                return getAwsPricingCardsViaApi(awsStorageServiceName);
            case JSON:
                return getAwsPricingCardsFromJson();
            default:
                throw new IllegalArgumentException(
                    String.format("Chosen mode [%s] isn't supported by AwsPriceListLoader!", mode));
        }
    }

    private List<AwsPricingCard> getAwsPricingCardsViaApi(final String awsStorageServiceName) {
        final List<Pair<String, String>> commonFilters = new ArrayList<>();
        if (AMAZON_S3_SERVICE_NAME.equals(awsStorageServiceName)) {
            commonFilters.add(ImmutablePair.of(PRODUCT_FAMILY_KEY, STORAGE));
        }
        return Stream.of(
                getAwsPricingCardsWithFiltersViaApi(
                    awsStorageServiceName,
                    ListUtils.sum(
                        commonFilters, Collections.singletonList(ImmutablePair.of(STORAGE_CLASS_KEY, GENERAL_STORAGE))
                    )
                ).stream(),
                getAwsPricingCardsWithFiltersViaApi(
                    awsStorageServiceName,
                    ListUtils.sum(
                        commonFilters,
                        Collections.singletonList(ImmutablePair.of(STORAGE_CLASS_KEY, ARCHIVE_STORAGE))
                    )
                ).stream(),
                getAwsPricingCardsWithFiltersViaApi(
                    awsStorageServiceName,
                    ListUtils.sum(
                        commonFilters,
                        Collections.singletonList(ImmutablePair.of(STORAGE_CLASS_KEY, ARCHIVE_IR_STORAGE))
                    )
                ).stream()
        ).reduce(Stream::concat)
        .orElse(Stream.empty())
        .filter(
                price -> price.getProduct().getAttributes()
                        .get(USAGETYPE).matches(TIMED_STORAGE_BYTE_HRS_USAGETYPE)
        ).collect(Collectors.toList());
    }

    private List<AwsPricingCard> getAwsPricingCardsWithFiltersViaApi(final String awsStorageServiceName,
                                                                     final List<Pair<String, String>> filters) {
        final List<AwsPricingCard> allPrices = new ArrayList<>();
        String nextToken = StringUtils.EMPTY;

        final AWSPricing awsPricingService = AWSPricingClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
                .build();
        do {
            final GetProductsRequest request = new GetProductsRequest()
                    .withServiceCode(awsStorageServiceName)
                    .withFilters(
                        filters.stream()
                            .map(f -> new Filter()
                                    .withType(TERM_MATCH_FILTER)
                                    .withField(f.getKey())
                                    .withValue(f.getValue())
                            ).collect(Collectors.toList())
                    ).withNextToken(nextToken)
                    .withFormatVersion(AWS_PRICE_FORMAT_VERSION);

            final GetProductsResult result = awsPricingService.getProducts(request);
            result.getPriceList().stream()
                    .map(this::parseAwsPricingCard)
                    .forEach(allPrices::add);
            nextToken = result.getNextToken();
        } while (nextToken != null);
        return allPrices;
    }


    private List<AwsPricingCard> getAwsPricingCardsFromJson() {
        try {
            final String jsonPriceList = readStringFromURL(priceLoadingEndpoint);
            final AwsPriceList fullPriceList = mapper.readValue(jsonPriceList, AwsPriceList.class);
            return fullPriceList.getProducts().values().stream()
                .filter(awsProduct -> {
                    if (awsStorageServiceName.equals(AMAZON_S3_SERVICE_NAME)) {
                        return STORAGE.equals(awsProduct.getProductFamily());
                    }
                    return true;
                })
                .filter(awsProduct -> {
                    final String storageClass = awsProduct.getAttributes().get(STORAGE_CLASS_KEY);
                    return GENERAL_STORAGE.equals(storageClass) || ARCHIVE_STORAGE.equals(storageClass)
                            || ARCHIVE_IR_STORAGE.equals(storageClass);
                }).filter(awsProduct -> {
                    final String usageType = awsProduct.getAttributes().get(USAGETYPE);
                    return usageType.matches(TIMED_STORAGE_BYTE_HRS_USAGETYPE);
                }).map(awsProduct -> {
                    final AwsPricingCard card = new AwsPricingCard();
                    card.setProduct(awsProduct);
                    card.setServiceCode(fullPriceList.getOfferCode());
                    card.setVersion(fullPriceList.getVersion());
                    card.setPublicationDate(fullPriceList.getPublicationDate());
                    final AwsTerms awsTerms = new AwsTerms();
                    awsTerms.setOnDemand(MapUtils.emptyIfNull(fullPriceList.getFullTerms().getOnDemand())
                                             .get(awsProduct.getSku()));
                    awsTerms.setSpot(MapUtils.emptyIfNull(fullPriceList.getFullTerms().getSpot())
                                         .get(awsProduct.getSku()));
                    card.setTerms(awsTerms);
                    return card;
                })
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Can't load AWS price list from given endpoint!", e);
        }
    }

    String readStringFromURL(String url) throws IOException {
        try (InputStream inputStream = new URL(url).openStream()) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        }
    }

    private AwsPricingCard parseAwsPricingCard(final String jsonStr) {
        try {
            return mapper.readValue(jsonStr, AwsPricingCard.class);
        } catch (IOException e) {
            throw new IllegalStateException("Error during AWS general pricing info parsing!");
        }
    }

    private Optional<Regions> getRegionFromFullLocation(final String location) {
        final Optional<Regions> awsRegion = Stream.of(Regions.values())
            .filter(region -> location.equals(region.getDescription()))
            .findAny();
        if (!awsRegion.isPresent()) {
            log.warn("Can't parse {} location: {}", getProvider().name(), location);
        }
        return awsRegion;
    }
}

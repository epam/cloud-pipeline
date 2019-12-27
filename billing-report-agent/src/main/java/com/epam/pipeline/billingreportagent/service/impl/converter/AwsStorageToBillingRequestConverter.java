/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.billingreportagent.model.billing.StorageBillingInfo;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.EntityMapper;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class AwsStorageToBillingRequestConverter implements EntityToBillingRequestConverter<AbstractDataStorage> {

    private static final String STORAGE_SIZE_AGG_NAME = "sizeSumSearch";
    private static final String SIZE_FIELD = "size";
    private static final String REGION_FIELD = "storage_region";
    private static final int BYTES_TO_GB = 1 << 30;
    private static final int CENTS_IN_DOLLAR = 100;
    private static final int PRECISION = 5;
    private static final String ES_FILE_INDEX_PATTERN = "cp-%s-file-%d";

    private final EntityMapper<StorageBillingInfo> mapper;
    private final ElasticsearchServiceClient elasticsearchService;
    private final Map<Regions, BigDecimal> storagePriceListGb = new HashMap<>();
    private BigDecimal defaultPriceGb;
    private final StorageType storageType;

    public AwsStorageToBillingRequestConverter(final EntityMapper<StorageBillingInfo> mapper,
                                               final ElasticsearchServiceClient elasticsearchService,
                                               final String awsStorageServiceName,
                                               final StorageType storageType) {
        this.mapper = mapper;
        this.elasticsearchService = elasticsearchService;
        this.storageType = storageType;
        initPrices(awsStorageServiceName);
    }

    @Override
    public List<DocWriteRequest> convertEntityToRequests(final EntityContainer<AbstractDataStorage> storageContainer,
                                                         final String indexName,
                                                         final LocalDateTime previousSync,
                                                         final LocalDateTime syncStart) {
        final SearchRequest searchRequest = new SearchRequest();
        final Long storageId = storageContainer.getEntity().getId();
        final DataStorageType storageType = storageContainer.getEntity().getType();
        searchRequest.indices(String.format(ES_FILE_INDEX_PATTERN, storageType.toString().toLowerCase(), storageId));

        final SumAggregationBuilder sizeSumAgg = AggregationBuilders.sum(STORAGE_SIZE_AGG_NAME)
            .field(SIZE_FIELD);

        final SearchSourceBuilder sizeSumSearch = new SearchSourceBuilder().aggregation(sizeSumAgg);
        searchRequest.source(sizeSumSearch);

        final SearchResponse search = elasticsearchService.search(searchRequest);

        final ParsedSum sumAggResult = search.getAggregations().get(STORAGE_SIZE_AGG_NAME);
        final long storageSize = new Double(sumAggResult.getValue()).longValue();

        if (storageSize == 0
            || search.getHits().getTotalHits() == 0) {
            return Collections.emptyList();
        }

        final LocalDate localDate = syncStart.toLocalDate().minusDays(1);
        final String fullIndex = String.format("%s-daily-%d-%s",
                                               indexName,
                                               storageId,
                                               parseDateToString(localDate));
        final String regionLocation = (String) search.getHits().getAt(0).getSourceAsMap().get(REGION_FIELD);
        final StorageBillingInfo storageBilling = createBilling(storageContainer,
                                                                storageSize,
                                                                regionLocation,
                                                                syncStart.toLocalDate().minusDays(1));
        return Collections.singletonList(getDocWriteRequest(fullIndex,
                                                            storageContainer.getOwner(),
                                                            storageBilling));
    }

    private DocWriteRequest getDocWriteRequest(final String fullIndex,
                                               final PipelineUser owner,
                                               final StorageBillingInfo billing) {
        final EntityContainer<StorageBillingInfo> entity = EntityContainer.<StorageBillingInfo>builder()
            .owner(owner)
            .entity(billing)
            .build();
        return new IndexRequest(fullIndex, INDEX_TYPE).source(mapper.map(entity));
    }

    private StorageBillingInfo createBilling(final EntityContainer<AbstractDataStorage> storageContainer,
                                             final Long byteSize,
                                             final String regionLocation,
                                             final LocalDate billingDate) {
        final StorageBillingInfo.StorageBillingInfoBuilder billing =
            StorageBillingInfo.builder()
                .storage(storageContainer.getEntity())
                .usageBytes(byteSize)
                .date(billingDate)
                .storageType(storageType);

        try {
            final Regions region = Regions.fromName(regionLocation);
            billing
                .regionName(region.getName())
                .cost(calculateDailyCost(byteSize, storagePriceListGb.get(region), billingDate));
        } catch (IllegalArgumentException e) {
            billing.cost(calculateDailyCost(byteSize, defaultPriceGb, billingDate));
        }

        return billing.build();
    }

    /**
     * Calculate daily spending on files storing in hundredths of a cent
     * @param sizeBytes storage size
     * @param monthlyPriceGb price Gb/month in cents
     * @param date billing date
     * @return daily cost
     */
    private Long calculateDailyCost(final Long sizeBytes, final BigDecimal monthlyPriceGb, final LocalDate date) {
        final BigDecimal sizeGb = BigDecimal.valueOf(sizeBytes)
            .divide(BigDecimal.valueOf(BYTES_TO_GB), PRECISION, RoundingMode.CEILING);

        final int daysInMonth = YearMonth.of(date.getYear(), date.getMonthValue()).lengthOfMonth();

        final long hundredthsOfCentPrice = sizeGb.multiply(monthlyPriceGb)
            .divide(BigDecimal.valueOf(daysInMonth), RoundingMode.CEILING)
            .scaleByPowerOfTen(2)
            .longValue();
        return hundredthsOfCentPrice == 0
               ? 1
               : hundredthsOfCentPrice;
    }


    private void initPrices(final String awsStorageServiceName) {
        loadFullPriceList(awsStorageServiceName).forEach(price -> {
            try {
                final JsonNode priceJson = new ObjectMapper().readTree(price);
                final String regionName = priceJson.path("product").path("attributes").path("location").asText();

                getRegionFromFullLocation(regionName)
                    .ifPresent(region -> priceJson.findValues("pricePerUnit").stream()
                        .map(unitPrice ->
                                 new BigDecimal(unitPrice.path("USD").asDouble(), new MathContext(PRECISION)))
                        .max(Comparator.naturalOrder())
                        .map(priceInDollars -> priceInDollars.multiply(BigDecimal.valueOf(CENTS_IN_DOLLAR)))
                        .ifPresent(maxPrice -> storagePriceListGb.put(region, maxPrice)));
            } catch (IOException e) {
                log.error("Can't instantiate AWS storage price list!");
            }
        });
        defaultPriceGb = storagePriceListGb.values()
            .stream()
            .max(Comparator.naturalOrder())
            .orElseThrow(() -> new RuntimeException("No AWS storage prices loaded!"));
    }

    private List<String> loadFullPriceList(final String awsStorageServiceName) {
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
}

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
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.billingreportagent.model.billing.StorageBillingInfo;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.EntityMapper;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.user.PipelineUser;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class AwsStorageToBillingRequestConverter implements EntityToBillingRequestConverter<AbstractDataStorage> {

    public static final int BYTES_TO_GB = 1 << 30;
    public static final int PRECISION = 5;
    private static final String STORAGE_SIZE_AGG_NAME = "sizeSumSearch";
    private static final String SIZE_FIELD = "size";
    private static final String REGION_FIELD = "storage_region";
    private static final String ES_FILE_INDEX_PATTERN = "cp-%s-file-%d";
    private static final String INDEX_PATTERN = "%s-daily-%s";
    private static final RoundingMode ROUNDING_MODE = RoundingMode.CEILING;

    private final EntityMapper<StorageBillingInfo> mapper;
    private final ElasticsearchServiceClient elasticsearchService;
    private final StorageType storageType;
    private final AwsStorageServicePricing storagePricing;

    public AwsStorageToBillingRequestConverter(final EntityMapper<StorageBillingInfo> mapper,
                                               final ElasticsearchServiceClient elasticsearchService,
                                               final String awsStorageServiceName,
                                               final StorageType storageType) {
        this.mapper = mapper;
        this.elasticsearchService = elasticsearchService;
        this.storageType = storageType;
        this.storagePricing = new AwsStorageServicePricing(awsStorageServiceName);
    }

    public AwsStorageToBillingRequestConverter(final EntityMapper<StorageBillingInfo> mapper,
                                               final ElasticsearchServiceClient elasticsearchService,
                                               final StorageType storageType,
                                               final AwsStorageServicePricing storagePricing) {
        this.mapper = mapper;
        this.elasticsearchService = elasticsearchService;
        this.storageType = storageType;
        this.storagePricing = storagePricing;
    }

    @Override
    public List<DocWriteRequest> convertEntityToRequests(final EntityContainer<AbstractDataStorage> storageContainer,
                                                         final String indexName,
                                                         final LocalDateTime previousSync,
                                                         final LocalDateTime syncStart) {
        final Long storageId = storageContainer.getEntity().getId();
        final DataStorageType storageType = storageContainer.getEntity().getType();
        final SearchResponse searchResponse = requestSumAggregationForStorage(storageId, storageType);
        final LocalDate reportDate = syncStart.toLocalDate().minusDays(1);
        final String fullIndex = String.format(INDEX_PATTERN, indexName, parseDateToString(reportDate));
        return buildRequestFromAggregation(storageContainer, syncStart, searchResponse, fullIndex);
    }

    @Override
    public List<DocWriteRequest> convertEntitiesToRequests(final List<EntityContainer<AbstractDataStorage>> containers,
                                                           final String indexName,
                                                           final LocalDateTime previousSync,
                                                           final LocalDateTime syncStart) {
        storagePricing.updatePrices();
        return EntityToBillingRequestConverter.super
            .convertEntitiesToRequests(containers, indexName, previousSync, syncStart);
    }

    private SearchResponse requestSumAggregationForStorage(final Long storageId, final DataStorageType storageType) {
        final SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(String.format(ES_FILE_INDEX_PATTERN, storageType.toString().toLowerCase(), storageId));
        final SumAggregationBuilder sizeSumAgg = AggregationBuilders.sum(STORAGE_SIZE_AGG_NAME).field(SIZE_FIELD);
        final SearchSourceBuilder sizeSumSearch = new SearchSourceBuilder().aggregation(sizeSumAgg);
        searchRequest.source(sizeSumSearch);
        return elasticsearchService.search(searchRequest);
    }

    private List<DocWriteRequest> buildRequestFromAggregation(final EntityContainer<AbstractDataStorage> container,
                                                              final LocalDateTime syncStart,
                                                              final SearchResponse response,
                                                              final String fullIndex) {
        return extractStorageSize(response).map(storageSize -> {
            final String regionLocation =
                (String) response.getHits().getAt(0).getSourceAsMap().get(REGION_FIELD);
            return createBilling(container, storageSize, regionLocation, syncStart.toLocalDate().minusDays(1));
        })
            .map(billing -> getDocWriteRequest(fullIndex, container.getOwner(), billing))
            .map(Collections::singletonList)
            .orElse(Collections.emptyList());
    }

    private Optional<Long> extractStorageSize(final SearchResponse response) {
        final ParsedSum sumAggResult = response.getAggregations().get(STORAGE_SIZE_AGG_NAME);
        final long storageSize = new Double(sumAggResult.getValue()).longValue();
        final long totalMatches = response.getHits().getTotalHits();
        return (storageSize == 0 || totalMatches == 0)
               ? Optional.empty()
               : Optional.of(storageSize);
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
                .cost(calculateDailyCost(byteSize, region, billingDate));
        } catch (IllegalArgumentException e) {
            billing.cost(calculateDailyCost(byteSize, storagePricing.getDefaultPriceGb(), billingDate));
        }

        return billing.build();
    }

    /**
     * Calculate daily spending on files storing in hundredths of a cent. The minimal result, possibly returned by this
     * function is 1, due to hundredths of cents granularity.
     *
     * @param sizeBytes      storage size
     * @param monthlyPriceGb price Gb/month in cents
     * @param date           billing date
     * @return daily cost
     */
    Long calculateDailyCost(final Long sizeBytes, final BigDecimal monthlyPriceGb, final LocalDate date) {
        final BigDecimal sizeGb = BigDecimal.valueOf(sizeBytes)
            .divide(BigDecimal.valueOf(BYTES_TO_GB), PRECISION, ROUNDING_MODE);

        final int daysInMonth = YearMonth.of(date.getYear(), date.getMonthValue()).lengthOfMonth();

        final long hundredthsOfCentPrice = sizeGb.multiply(monthlyPriceGb)
            .divide(BigDecimal.valueOf(daysInMonth), ROUNDING_MODE)
            .scaleByPowerOfTen(2)
            .longValue();
        return hundredthsOfCentPrice == 0
               ? 1
               : hundredthsOfCentPrice;
    }

    Long calculateDailyCost(final Long sizeBytes, final Regions region, final LocalDate date) {
        return storagePricing.getRegionPricing(region).getPrices().stream()
            .filter(entity -> entity.getBeginRangeBytes() <= sizeBytes)
            .mapToLong(entity -> {
                final Long beginRange = entity.getBeginRangeBytes();
                final Long endRange = entity.getEndRangeBytes();
                final long bytesForCurrentTierPrice = Math.min(sizeBytes - beginRange,
                                                               endRange - beginRange);
                return calculateDailyCost(bytesForCurrentTierPrice, entity.getPriceCentsPerGb(), date);
            }).sum();
    }
}

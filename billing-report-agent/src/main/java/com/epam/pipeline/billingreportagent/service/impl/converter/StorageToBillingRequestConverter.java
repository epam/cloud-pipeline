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
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.model.StorageType;
import com.epam.pipeline.billingreportagent.model.billing.StorageBillingInfo;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.AbstractEntityMapper;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.user.PipelineUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class StorageToBillingRequestConverter implements EntityToBillingRequestConverter<AbstractDataStorage> {

    private static final String STORAGE_SIZE_AGG_NAME = "sizeSumSearch";
    private static final String SIZE_FIELD = "size";
    private static final String REGION_FIELD = "storage_region";
    private static final RoundingMode ROUNDING_MODE = RoundingMode.CEILING;

    private final AbstractEntityMapper<StorageBillingInfo> mapper;
    private final ElasticsearchServiceClient elasticsearchService;
    private final StorageType storageType;
    private final StoragePricingService storagePricing;
    private final String esFileIndexPattern;
    private final Optional<FileShareMountsService> fileshareMountsService;
    private final MountType desiredMountType;
    private boolean enableStorageHistoricalBillingGeneration;

    public StorageToBillingRequestConverter(final AbstractEntityMapper<StorageBillingInfo> mapper,
                                            final ElasticsearchServiceClient elasticsearchService,
                                            final StorageType storageType,
                                            final StoragePricingService storagePricing,
                                            final String esFileIndexPattern,
                                            final boolean enableStorageHistoricalBillingGeneration) {
        this(mapper, elasticsearchService, storageType, storagePricing, esFileIndexPattern, null, null,
             enableStorageHistoricalBillingGeneration);
    }

    public StorageToBillingRequestConverter(final AbstractEntityMapper<StorageBillingInfo> mapper,
                                            final ElasticsearchServiceClient elasticsearchService,
                                            final StorageType storageType,
                                            final StoragePricingService storagePricing,
                                            final String esFileIndexPattern,
                                            final FileShareMountsService fileshareMountsService,
                                            final MountType desiredMountType,
                                            final boolean enableStorageHistoricalBillingGeneration) {
        this.mapper = mapper;
        this.elasticsearchService = elasticsearchService;
        this.storageType = storageType;
        this.storagePricing = storagePricing;
        this.esFileIndexPattern = esFileIndexPattern;
        this.fileshareMountsService = Optional.ofNullable(fileshareMountsService);
        this.desiredMountType = desiredMountType;
        this.enableStorageHistoricalBillingGeneration = enableStorageHistoricalBillingGeneration;
    }

    @Override
    public List<DocWriteRequest> convertEntityToRequests(final EntityContainer<AbstractDataStorage> storageContainer,
                                                         final String indexPrefix,
                                                         final LocalDateTime previousSync,
                                                         final LocalDateTime syncStart) {
        final Long storageId = storageContainer.getEntity().getId();
        final DataStorageType storageType = storageContainer.getEntity().getType();
        return requestSumAggregationForStorage(storageId, storageType)
            .map(searchResponse -> enableStorageHistoricalBillingGeneration
                                   ? buildRequestsForGivenPeriod(storageContainer, indexPrefix, previousSync, syncStart,
                                                                 searchResponse)
                                   : buildRequestsForGivenDate(storageContainer, indexPrefix, searchResponse,
                                                               syncStart))
            .orElse(Collections.emptyList());
    }

    @Override
    public List<DocWriteRequest> convertEntitiesToRequests(final List<EntityContainer<AbstractDataStorage>> containers,
                                                           final String indexName,
                                                           final LocalDateTime previousSync,
                                                           final LocalDateTime syncStart) {
        storagePricing.updatePrices();
        fileshareMountsService.ifPresent(service -> {
            service.updateSharesRegions();
            containers.removeIf(container -> {
                final Long fileShareMountId = container.getEntity().getFileShareMountId();
                return !desiredMountType.equals(service.getMountTypeForShare(fileShareMountId));
            });
        });
        return EntityToBillingRequestConverter.super
            .convertEntitiesToRequests(containers, indexName, previousSync, syncStart);
    }

    private Optional<SearchResponse> requestSumAggregationForStorage(final Long storageId,
                                                                     final DataStorageType storageType) {
        final String searchIndex = String.format(esFileIndexPattern,
                                                 storageType.toString().toLowerCase(),
                                                 DataStorageType.AZ.equals(storageType) ? "blob" : "file",
                                                 storageId);
        if (elasticsearchService.isIndexExists(searchIndex)) {
            final SearchRequest searchRequest = new SearchRequest();
            searchRequest.indices(searchIndex);
            final SumAggregationBuilder sizeSumAgg = AggregationBuilders.sum(STORAGE_SIZE_AGG_NAME).field(SIZE_FIELD);
            final SearchSourceBuilder sizeSumSearch = new SearchSourceBuilder().aggregation(sizeSumAgg);
            searchRequest.source(sizeSumSearch);
            return Optional.of(elasticsearchService.search(searchRequest));
        } else {
            return Optional.empty();
        }
    }

    private List<DocWriteRequest> buildRequestFromAggregation(final EntityContainer<AbstractDataStorage> container,
                                                              final LocalDateTime syncStart,
                                                              final SearchResponse response,
                                                              final String fullIndex) {
        return extractStorageSize(response).map(storageSize -> {
            final SearchHits hits = response.getHits();
            final String regionLocation =
                Objects.isNull(hits)
                ? null
                : (String) MapUtils.emptyIfNull(hits.getAt(0).getSourceAsMap()).get(REGION_FIELD);
            return createBilling(container, storageSize, regionLocation, syncStart.toLocalDate().minusDays(1));
        })
            .map(billing -> getDocWriteRequest(fullIndex, container.getOwner(), container.getRegion(), billing))
            .map(Collections::singletonList)
            .orElse(Collections.emptyList());
    }

    private Optional<Long> extractStorageSize(final SearchResponse response) {
        final long totalMatches = Optional.ofNullable(response.getHits().getHits())
            .map(hits -> hits.length)
            .orElse(0);
        if (totalMatches == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.getAggregations())
            .map(aggregations -> aggregations.get(STORAGE_SIZE_AGG_NAME))
            .map(ParsedSum.class::cast)
            .map(ParsedSum::getValue)
            .map(Double::longValue)
            .filter(val -> !val.equals(0L));
    }

    private DocWriteRequest getDocWriteRequest(final String fullIndex,
                                               final EntityWithMetadata<PipelineUser> owner,
                                               final AbstractCloudRegion region,
                                               final StorageBillingInfo billing) {
        final EntityContainer<StorageBillingInfo> entity = EntityContainer.<StorageBillingInfo>builder()
            .owner(owner)
            .entity(billing)
            .region(region)
            .build();
        final String docId = billing.getEntity().getId().toString();
        return new IndexRequest(fullIndex, INDEX_TYPE).id(docId).source(mapper.map(entity));
    }

    private StorageBillingInfo createBilling(final EntityContainer<AbstractDataStorage> storageContainer,
                                             final Long byteSize,
                                             final String regionLocation,
                                             final LocalDate billingDate) {
        final DataStorageType objectStorageType = getObjectStorageType(storageContainer);
        final MountType fileStorageType = objectStorageType != null ? null : getFileStorageType(storageContainer);
        final StorageBillingInfo.StorageBillingInfoBuilder billing =
            StorageBillingInfo.builder()
                .storage(storageContainer.getEntity())
                .usageBytes(byteSize)
                .date(billingDate)
                .resourceStorageType(storageType)
                .objectStorageType(objectStorageType)
                .fileStorageType(fileStorageType);

        try {
            billing.cost(calculateDailyCost(byteSize, regionLocation, billingDate));
        } catch (IllegalArgumentException e) {
            billing.cost(calculateDailyCost(byteSize, storagePricing.getDefaultPriceGb(), billingDate));
        }

        return billing.build();
    }

    private DataStorageType getObjectStorageType(final EntityContainer<AbstractDataStorage> storageContainer) {
        return Optional.ofNullable(storageContainer.getEntity().getType())
                .filter(type -> type != DataStorageType.NFS)
                .orElse(null);
    }

    private MountType getFileStorageType(final EntityContainer<AbstractDataStorage> storageContainer) {
        return Optional.ofNullable(storageContainer.getEntity().getFileShareMountId())
                .flatMap(fileShareId -> fileshareMountsService.map(s -> s.getMountTypeForShare(fileShareId)))
                .orElse(null);
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
            .divide(BigDecimal.valueOf(StoragePriceListLoader.BYTES_TO_GB),
                    StoragePriceListLoader.PRECISION,
                    ROUNDING_MODE);

        final int daysInMonth = YearMonth.of(date.getYear(), date.getMonthValue()).lengthOfMonth();

        final long hundredthsOfCentPrice = sizeGb.multiply(monthlyPriceGb)
            .divide(BigDecimal.valueOf(daysInMonth), ROUNDING_MODE)
            .scaleByPowerOfTen(2)
            .longValue();
        return hundredthsOfCentPrice == 0
               ? 1
               : hundredthsOfCentPrice;
    }

    Long calculateDailyCost(final Long sizeBytes, final String region, final LocalDate date) {
        return Optional.ofNullable(storagePricing.getRegionPricing(region))
            .orElseGet(() -> {
                final StoragePricing pricing = new StoragePricing();
                pricing.addPrice(new StoragePricing.StoragePricingEntity(0L,
                                                                         Long.MAX_VALUE,
                                                                         storagePricing.getDefaultPriceGb()));
                return pricing;
            })
            .getPrices().stream()
            .filter(entity -> entity.getBeginRangeBytes() <= sizeBytes)
            .mapToLong(entity -> {
                final Long beginRange = entity.getBeginRangeBytes();
                final Long endRange = entity.getEndRangeBytes();
                final long bytesForCurrentTierPrice = Math.min(sizeBytes - beginRange,
                                                               endRange - beginRange);
                return calculateDailyCost(bytesForCurrentTierPrice, entity.getPriceCentsPerGb(), date);
            }).sum();
    }

    private List<DocWriteRequest> buildRequestsForGivenPeriod(final EntityContainer<AbstractDataStorage> container,
                                                              final String indexPrefix,
                                                              final LocalDateTime previousSync,
                                                              final LocalDateTime syncStart,
                                                              final SearchResponse searchResponse) {
        final LocalDateTime previousSyncDayStart = Optional.ofNullable(previousSync)
                .map(LocalDateTime::toLocalDate)
                .orElse(LocalDate.now())
                .atStartOfDay();
        return Stream.iterate(previousSyncDayStart, date -> date.plusDays(1))
            .limit(Math.max(1, ChronoUnit.DAYS.between(previousSyncDayStart, syncStart)))
            .filter(reportDate -> storageExistsOnBillingDate(container, reportDate))
            .map(date -> buildRequestsForGivenDate(container, indexPrefix, searchResponse, date))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    private boolean storageExistsOnBillingDate(final EntityContainer<AbstractDataStorage> storageContainer,
                                               final LocalDateTime reportDate) {
        return reportDate.isAfter(storageContainer.getEntity()
                                      .getCreatedDate()
                                      .toInstant()
                                      .atZone(ZoneId.systemDefault())
                                      .toLocalDateTime());
    }

    private List<DocWriteRequest> buildRequestsForGivenDate(final EntityContainer<AbstractDataStorage> storageContainer,
                                                            final String indexPrefix,
                                                            final SearchResponse searchResponse,
                                                            final LocalDateTime date) {
        final LocalDate reportDate = date.toLocalDate().minusDays(1);
        final String fullIndex = indexPrefix + parseDateToString(reportDate);
        return buildRequestFromAggregation(storageContainer, date, searchResponse, fullIndex);
    }
}

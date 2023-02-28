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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class StorageToBillingRequestConverter implements EntityToBillingRequestConverter<AbstractDataStorage> {

    public static final String STORAGE_SIZE_AGG_NAME = "sizeSumSearch";
    public static final String OBJECT_SIZE_AGG_NAME = "CurrentObjectSizeAggr";
    private static final String SIZE_FIELD = "size";
    private static final String REGION_FIELD = "storage_region";
    private static final RoundingMode ROUNDING_MODE = RoundingMode.CEILING;
    public static final String STORAGE_CLASS_FIELD = "storage_class";
    public static final String OLD_VERSION_SIZE_FIELD_TEMPLATE = "ov_%s_size";

    private final AbstractEntityMapper<StorageBillingInfo> mapper;
    private final ElasticsearchServiceClient elasticsearchService;
    private final StorageType storageType;
    private final StoragePricingService storagePricing;
    private final String esFileAliasIndexPattern;
    private final String esFileIndexPattern;
    private final Optional<FileShareMountsService> fileshareMountsService;
    private final MountType desiredMountType;
    private boolean enableStorageHistoricalBillingGeneration;

    public StorageToBillingRequestConverter(final AbstractEntityMapper<StorageBillingInfo> mapper,
                                            final ElasticsearchServiceClient elasticsearchService,
                                            final StorageType storageType,
                                            final StoragePricingService storagePricing,
                                            final String esFileAliasIndexPattern,
                                            final String esFileIndexPattern,
                                            final boolean enableStorageHistoricalBillingGeneration) {
        this(mapper, elasticsearchService, storageType, storagePricing, esFileAliasIndexPattern,
                esFileIndexPattern, null, null, enableStorageHistoricalBillingGeneration);
    }

    public StorageToBillingRequestConverter(final AbstractEntityMapper<StorageBillingInfo> mapper,
                                            final ElasticsearchServiceClient elasticsearchService,
                                            final StorageType storageType,
                                            final StoragePricingService storagePricing,
                                            final String esFileAliasIndexPattern,
                                            final String esFileIndexPattern,
                                            final FileShareMountsService fileshareMountsService,
                                            final MountType desiredMountType,
                                            final boolean enableStorageHistoricalBillingGeneration) {
        this.mapper = mapper;
        this.elasticsearchService = elasticsearchService;
        this.storageType = storageType;
        this.storagePricing = storagePricing;
        this.esFileAliasIndexPattern = esFileAliasIndexPattern;
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
        final String indexNameByAlias = getIndexName(storageId, storageType);
        if (elasticsearchService.isIndexExists(indexNameByAlias)) {
            final SearchRequest searchRequest = new SearchRequest();
            searchRequest.indices(indexNameByAlias);
            final TermsAggregationBuilder currentSizeSumAgg = AggregationBuilders
                    .terms(OBJECT_SIZE_AGG_NAME).field(STORAGE_CLASS_FIELD)
                    .subAggregation(AggregationBuilders.sum(STORAGE_SIZE_AGG_NAME).field(SIZE_FIELD));
            final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().aggregation(currentSizeSumAgg);
            for (String storageClass : storageType.getStorageClasses()) {
                final String ovSizeFieldName =
                        String.format(OLD_VERSION_SIZE_FIELD_TEMPLATE, storageClass.toLowerCase(Locale.ROOT));
                sourceBuilder.aggregation(AggregationBuilders.sum(ovSizeFieldName + "_agg").field(ovSizeFieldName));
            }
            searchRequest.source(sourceBuilder);
            return Optional.of(elasticsearchService.search(searchRequest));
        } else {
            return Optional.empty();
        }
    }

    private String getIndexName(final Long storageId, final DataStorageType storageType) {
        final String indexNameByAlias = getIndexNameByAlias(storageId, storageType);
        if (indexNameByAlias == null) {
            final String indexNamePattern = String.format(esFileIndexPattern, storageType.toString().toLowerCase(),
                    DataStorageType.AZ.equals(storageType) ? "blob" : "file", storageId);
            log.warn("Fail to found index by alias will use name pattern: {} for storage: {}, " +
                    "billing can be calculated incorrectly!", indexNamePattern, storageId);
            return indexNamePattern;
        }
        return indexNameByAlias;
    }

    private String getIndexNameByAlias(final Long storageId, final DataStorageType storageType) {
        if (esFileAliasIndexPattern == null) {
            return null;
        }
        final String searchIndex = String.format(esFileAliasIndexPattern,
                storageType.toString().toLowerCase(),
                DataStorageType.AZ.equals(storageType) ? "blob" : "file",
                storageId);
        try {
            return elasticsearchService.getIndexNameByAlias(searchIndex);
        } catch (ElasticsearchException e) {
            log.warn(String.format("There is a problem to find index for storage %d by alias: %s!",
                    storageId,  searchIndex), e);
            return null;
        }
    }

    private List<DocWriteRequest> buildRequestFromAggregation(final EntityContainer<AbstractDataStorage> container,
                                                              final LocalDateTime syncStart,
                                                              final SearchResponse response,
                                                              final String fullIndex) {

        final Map<Boolean, Map<String, Long>> storageSizes =
                extractStorageSize(container.getEntity().getType(), response);
        if (MapUtils.isEmpty(storageSizes)) {
            return Collections.emptyList();
        }
        final SearchHits hits = response.getHits();
        final String regionLocation =
            Objects.isNull(hits) || hits.totalHits == 0
            ? null
            : (String) MapUtils.emptyIfNull(hits.getAt(0).getSourceAsMap()).get(REGION_FIELD);

        final StorageBillingInfo billing = createBilling(
                container, storageSizes, regionLocation, syncStart.toLocalDate().minusDays(1)
        );
        return Collections.singletonList(
                getDocWriteRequest(fullIndex, container.getOwner(), container.getRegion(), billing));
    }

    private Map<Boolean, Map<String, Long>> extractStorageSize(final DataStorageType datastorageType,
                                                               final SearchResponse response) {
        final long totalMatches = Optional.ofNullable(response.getHits().getHits())
            .map(hits -> hits.length)
            .orElse(0);
        final Aggregations aggregations = response.getAggregations();
        if (totalMatches == 0 || aggregations == null) {
            return Collections.emptyMap();
        }
        final Map<Boolean, Map<String, Long>> results = new HashMap<>();

        final Map<String, Long> currentVersionsFileSizesByStorageClass = ListUtils.emptyIfNull(
                aggregations.<ParsedTerms>get(OBJECT_SIZE_AGG_NAME).getBuckets()
        ).stream()
                .map(b -> {
                    String storageClass = b.getKeyAsString();
                    long size = new Double(
                            b.getAggregations().<ParsedSum>get(STORAGE_SIZE_AGG_NAME).getValue()
                    ).longValue();
                    return ImmutablePair.of(storageClass, size);
                }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        results.put(true, currentVersionsFileSizesByStorageClass);

        final Map<String, Long> oldVersionsFileSizesByStorageClass = datastorageType.getStorageClasses()
            .stream().map(sc -> {
                final String ovSizeFieldName =
                        String.format(OLD_VERSION_SIZE_FIELD_TEMPLATE, sc.toLowerCase(Locale.ROOT));
                return ImmutablePair.of(
                        sc,
                        Optional.ofNullable(aggregations.<ParsedSum>get(ovSizeFieldName + "_agg"))
                                .map(ParsedSum::getValue).orElse((double) 0)
                                .longValue()
                );
            }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        results.put(false, oldVersionsFileSizesByStorageClass);
        return results;
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
        return new IndexRequest(fullIndex, INDEX_TYPE)
                .id(billing.getEntity().getId().toString()).source(mapper.map(entity));
    }

    StorageBillingInfo createBilling(final EntityContainer<AbstractDataStorage> storageContainer,
                                             final Map<Boolean, Map<String, Long>> storageSizes,
                                             final String regionLocation,
                                             final LocalDate billingDate) {
        final DataStorageType objectStorageType = getObjectStorageType(storageContainer);
        final MountType fileStorageType = objectStorageType != null ? null : getFileStorageType(storageContainer);
        final StorageBillingInfo.StorageBillingInfoBuilder billing =
                StorageBillingInfo.builder()
                        .storage(storageContainer.getEntity())
                        .date(billingDate)
                        .resourceStorageType(storageType)
                        .objectStorageType(objectStorageType)
                        .fileStorageType(fileStorageType);
        final List<StorageBillingInfo.StorageBillingInfoDetails> details = buildBillingDetails(
                storageSizes, regionLocation, billingDate);
        billing.billingDetails(details)
            .cost(details.stream()
                .map(dc -> dc.getCost() + dc.getOldVersionCost())
                .reduce(0L, Long::sum)
            )
            .usageBytes(details.stream()
                .map(dc -> dc.getUsageBytes() + dc.getOldVersionUsageBytes())
                .reduce(0L, Long::sum)
            );
        return billing.build();
    }

    private List<StorageBillingInfo.StorageBillingInfoDetails> buildBillingDetails(
            final Map<Boolean, Map<String, Long>> storageSizes,
            final String regionLocation, final LocalDate billingDate) {
        final List<StorageBillingInfo.StorageBillingInfoDetails> current =
                buildBillingDetails(storageSizes.get(true), true, regionLocation, billingDate);
        final List<StorageBillingInfo.StorageBillingInfoDetails> oldVersions =
                buildBillingDetails(storageSizes.get(false), false, regionLocation, billingDate);
        return mergeBillingDetailsMaps(current, oldVersions);
    }

    private List<StorageBillingInfo.StorageBillingInfoDetails> mergeBillingDetailsMaps(
            final List<StorageBillingInfo.StorageBillingInfoDetails> left,
            final List<StorageBillingInfo.StorageBillingInfoDetails> right) {
        if (CollectionUtils.isEmpty(left) || CollectionUtils.isEmpty(right)) {
            return CollectionUtils.isNotEmpty(right)
                    ? right
                    : CollectionUtils.isNotEmpty(left) ? left : Collections.emptyList();
        }
        return Stream.concat(left.stream(), right.stream())
                .map(StorageBillingInfo.StorageBillingInfoDetails::getStorageClass)
                .distinct()
                .map(storageClass -> mergeBillingDetails(
                        left.stream().filter(bd -> storageClass.equals(bd.getStorageClass())).findFirst(),
                        right.stream().filter(bd -> storageClass.equals(bd.getStorageClass())).findFirst()
                        )
                ).collect(Collectors.toList());
    }

    private StorageBillingInfo.StorageBillingInfoDetails mergeBillingDetails(
            final Optional<StorageBillingInfo.StorageBillingInfoDetails> leftOp,
            final Optional<StorageBillingInfo.StorageBillingInfoDetails> rightOp) {

        final StorageBillingInfo.StorageBillingInfoDetails.StorageBillingInfoDetailsBuilder result =
                StorageBillingInfo.StorageBillingInfoDetails.builder();
        if (!leftOp.isPresent() || !rightOp.isPresent()) {
            return rightOp.orElseGet(() -> leftOp.orElseGet(result::build));
        }

        StorageBillingInfo.StorageBillingInfoDetails left = leftOp.get();
        StorageBillingInfo.StorageBillingInfoDetails right = rightOp.get();

        return result
                .storageClass(left.getStorageClass())
                .cost(left.getCost() + right.getCost())
                .usageBytes(left.getUsageBytes() + right.getUsageBytes())
                .oldVersionUsageBytes(left.getOldVersionUsageBytes() + right.getOldVersionUsageBytes())
                .oldVersionCost(left.getOldVersionCost() + right.getOldVersionCost())
                .build();
    }

    private List<StorageBillingInfo.StorageBillingInfoDetails> buildBillingDetails(
        final Map<String, Long> sizesByStorageType, final boolean isCurrentObject,
        final String regionLocation, final LocalDate billingDate) {
        if (sizesByStorageType == null) {
            return Collections.emptyList();
        }
        return sizesByStorageType.entrySet()
            .stream()
            .filter(tierSize -> tierSize.getValue() > 0)
            .map(storageClassSize -> {
                final String storageClass = storageClassSize.getKey();
                final Long size = storageClassSize.getValue();

                Long cost;
                try {
                    cost = calculateDailyCost(size, storageClass, regionLocation, billingDate);
                } catch (IllegalArgumentException e) {
                    cost = calculateDailyCost(
                            size, storagePricing.getDefaultPriceGb(storageClass), billingDate);
                }
                return ImmutablePair.of(storageClass, ImmutablePair.of(size, cost));
            }).map(stats -> {
                StorageBillingInfo.StorageBillingInfoDetails.StorageBillingInfoDetailsBuilder builder
                        = StorageBillingInfo.StorageBillingInfoDetails.builder()
                        .storageClass(stats.getKey());
                if (isCurrentObject) {
                    builder.usageBytes(stats.getValue().getKey()).cost(stats.getValue().getValue());
                } else {
                    builder.oldVersionUsageBytes(stats.getValue().getKey())
                            .oldVersionCost(stats.getValue().getValue());
                }
                return builder.build();
            }).collect(Collectors.toList());
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

    Long calculateDailyCost(final Long sizeBytes, final String storageClass,
                            final String region, final LocalDate date) {
        return Optional.ofNullable(storagePricing.getRegionPricing(region))
            .orElseGet(() -> {
                final StoragePricing pricing = new StoragePricing();
                pricing.addPrice(storageClass,
                        new StoragePricing.StoragePricingEntity(
                                0L, Long.MAX_VALUE, storagePricing.getDefaultPriceGb(storageClass))
                );
                return pricing;
            })
            .getPrices(storageClass).stream()
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

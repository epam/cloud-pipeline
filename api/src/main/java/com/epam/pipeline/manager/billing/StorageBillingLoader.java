package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.StorageBilling;
import com.epam.pipeline.entity.billing.StorageBillingMetrics;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageBillingLoader implements BillingLoader<StorageBilling> {

    private final BillingHelper billingHelper;
    private final StorageBillingDetailsLoader storageBillingDetailsLoader;

    @Override
    public Stream<StorageBilling> billings(final RestHighLevelClient elasticSearchClient,
                                           final BillingExportRequest request) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        final BillingDiscount discount = Optional.ofNullable(request.getDiscount()).orElseGet(BillingDiscount::empty);
        return billings(elasticSearchClient, from, to, filters, discount);
    }

    private Stream<StorageBilling> billings(final RestHighLevelClient elasticSearchClient,
                                            final LocalDate from,
                                            final LocalDate to,
                                            final Map<String, List<String>> filters,
                                            final BillingDiscount discount) {
        return Optional.of(getRequest(from, to, filters, discount))
                .map(billingHelper.searchWith(elasticSearchClient))
                .map(this::billings)
                .orElseGet(Stream::empty);
    }

    private SearchRequest getRequest(final LocalDate from,
                                     final LocalDate to,
                                     final Map<String, List<String>> filters,
                                     final BillingDiscount discount) {
        return new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.storageIndicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(NumberUtils.INTEGER_ZERO)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(billingHelper.aggregateBy(BillingGrouping.STORAGE.getCorrespondingField())
                                .size(Integer.MAX_VALUE)
                                .subAggregation(aggregateBillingsByMonth(discount))
                                .subAggregation(billingHelper.aggregateCostSumBucket())
                                .subAggregation(billingHelper.aggregateStorageUsageAverageBucket())
                                .subAggregation(billingHelper.aggregateLastByDateDoc())
                                .subAggregation(billingHelper.aggregateCostSortBucket())));
    }

    private DateHistogramAggregationBuilder aggregateBillingsByMonth(final BillingDiscount discount) {
        return billingHelper.aggregateByMonth()
                .subAggregation(billingHelper.aggregateCostSum(discount.getStorages()))
                .subAggregation(billingHelper.aggregateStorageUsageAvg())
                .subAggregation(billingHelper.aggregateLastByDateDoc());
    }

    private Stream<StorageBilling> billings(final SearchResponse response) {
        return billingHelper.termBuckets(response.getAggregations(), BillingGrouping.STORAGE.getCorrespondingField())
                .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                .map(this::withDetails);
    }

    private StorageBilling getBilling(final String id, final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        return StorageBilling.builder()
                .id(NumberUtils.toLong(id))
                .owner(BillingUtils.asString(topHitFields.get(BillingUtils.OWNER_FIELD)))
                .billingCenter(BillingUtils.asString(topHitFields.get(BillingUtils.BILLING_CENTER_FIELD)))
                .type(DataStorageType.getByName(BillingUtils.asString(topHitFields.get(BillingUtils.PROVIDER_FIELD))))
                .totalMetrics(StorageBillingMetrics.builder()
                        .cost(billingHelper.getCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .averageVolume(billingHelper.getStorageUsageAvg(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .currentVolume(Long.valueOf(BillingUtils.asString(topHitFields.get(BillingUtils.STORAGE_USAGE_FIELD))))
                        .build())
                .periodMetrics(getPeriodMetrics(aggregations))
                .build();
    }

    private Map<YearMonth, StorageBillingMetrics> getPeriodMetrics(final Aggregations aggregations) {
        return billingHelper.histogramBuckets(aggregations, BillingUtils.HISTOGRAM_AGGREGATION_NAME)
                .map(bucket -> getPeriodMetrics(bucket.getKeyAsString(), bucket.getAggregations()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<YearMonth, StorageBillingMetrics> getPeriodMetrics(final String ym, final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        return Pair.of(
                YearMonth.parse(ym, DateTimeFormatter.ofPattern(BillingUtils.HISTOGRAM_AGGREGATION_FORMAT)),
                StorageBillingMetrics.builder()
                        .cost(billingHelper.getCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .averageVolume(billingHelper.getStorageUsageAvg(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .currentVolume(Long.valueOf(BillingUtils.asString(topHitFields.get(BillingUtils.STORAGE_USAGE_FIELD))))
                        .build());
    }

    private StorageBilling withDetails(final StorageBilling billing) {
        final Map<String, String> details = storageBillingDetailsLoader.loadDetails(billing.getId().toString());
        return billing.toBuilder()
                .name(details.get(StorageBillingDetailsLoader.NAME))
                .region(details.get(StorageBillingDetailsLoader.REGION))
                .provider(details.get(StorageBillingDetailsLoader.PROVIDER))
                .created(asDateTime(details.get(StorageBillingDetailsLoader.CREATED)))
                .build();
    }

    private LocalDateTime asDateTime(final String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

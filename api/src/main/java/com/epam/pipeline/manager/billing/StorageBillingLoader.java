package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
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
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.avg.AvgBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.sum.SumBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketsort.BucketSortPipelineAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
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
        return billings(elasticSearchClient, from, to, filters);
    }

    private Stream<StorageBilling> billings(final RestHighLevelClient elasticSearchClient,
                                            final LocalDate from,
                                            final LocalDate to,
                                            final Map<String, List<String>> filters) {
        return Optional.of(getRequest(from, to, filters))
                .map(billingHelper.searchWith(elasticSearchClient))
                .map(this::billings)
                .orElseGet(Stream::empty);
    }

    private Stream<StorageBilling> billings(final SearchResponse response) {
        return Optional.ofNullable(response.getAggregations())
                .map(it -> it.get(BillingGrouping.STORAGE.getCorrespondingField()))
                .filter(ParsedStringTerms.class::isInstance)
                .map(ParsedStringTerms.class::cast)
                .map(ParsedTerms::getBuckets)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                .map(this::withDetails);
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

    private StorageBilling getBilling(final String id, final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        return StorageBilling.builder()
                .id(NumberUtils.toLong(id))
                .owner(BillingUtils.asString(topHitFields.get(BillingHelper.OWNER_FIELD)))
                .billingCenter(BillingUtils.asString(topHitFields.get(BillingHelper.BILLING_CENTER_FIELD)))
                .type(DataStorageType.getByName(BillingUtils.asString(topHitFields.get(BillingHelper.PROVIDER_FIELD))))
                .totalMetrics(StorageBillingMetrics.builder()
                        .cost(billingHelper.getCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .averageVolume(billingHelper.getStorageUsageAvg(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .currentVolume(Long.valueOf(BillingUtils.asString(topHitFields.get(BillingHelper.STORAGE_USAGE_FIELD))))
                        .build())
                .periodMetrics(getPeriodMetrics(aggregations))
                .build();
    }

    private Map<YearMonth, StorageBillingMetrics> getPeriodMetrics(final Aggregations aggregations) {
        return Optional.ofNullable(aggregations)
                .map(it -> it.get(BillingHelper.HISTOGRAM_AGGREGATION_NAME))
                .filter(ParsedDateHistogram.class::isInstance)
                .map(ParsedDateHistogram.class::cast)
                .map(ParsedDateHistogram::getBuckets)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(bucket -> getPeriodMetrics(bucket.getKeyAsString(), bucket.getAggregations()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<YearMonth, StorageBillingMetrics> getPeriodMetrics(final String ym,
                                                                    final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        return Pair.of(YearMonth.parse(ym, DateTimeFormatter.ofPattern(BillingHelper.HISTOGRAM_AGGREGATION_FORMAT)),
                StorageBillingMetrics.builder()
                .cost(billingHelper.getCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                .averageVolume(billingHelper.getStorageUsageAvg(aggregations).orElse(NumberUtils.LONG_ZERO))
                .currentVolume(Long.valueOf(BillingUtils.asString(topHitFields.get(BillingHelper.STORAGE_USAGE_FIELD))))
                .build());
    }

    private SearchRequest getRequest(final LocalDate from,
                                     final LocalDate to,
                                     final Map<String, List<String>> filters) {
        return new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.storageIndicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(0)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(billingHelper.aggregateBy(BillingGrouping.STORAGE.getCorrespondingField())
                                .size(Integer.MAX_VALUE)
                                .subAggregation(aggregateBillingsByMonth())
                                .subAggregation(aggregateCostSumBucket())
                                .subAggregation(aggregateStorageUsageAverageBucket())
                                .subAggregation(billingHelper.aggregateLastByDateDoc())
                                .subAggregation(aggregateCostSortBucket())));
    }

    private DateHistogramAggregationBuilder aggregateBillingsByMonth() {
        return billingHelper.aggregateByMonth()
                .subAggregation(billingHelper.aggregateCostSum())
                .subAggregation(aggregateStorageUsageAvg())
                .subAggregation(billingHelper.aggregateLastByDateDoc());
    }

    private AvgAggregationBuilder aggregateStorageUsageAvg() {
        return AggregationBuilders.avg(BillingHelper.STORAGE_USAGE_AGG)
                .field(BillingHelper.STORAGE_USAGE_FIELD);
    }

    private SumBucketPipelineAggregationBuilder aggregateCostSumBucket() {
        return PipelineAggregatorBuilders
                .sumBucket(BillingHelper.COST_FIELD,
                        String.join(BillingHelper.ES_DOC_AGGS_SEPARATOR, BillingHelper.HISTOGRAM_AGGREGATION_NAME, BillingHelper.COST_FIELD));
    }

    private AvgBucketPipelineAggregationBuilder aggregateStorageUsageAverageBucket() {
        return PipelineAggregatorBuilders
                .avgBucket(BillingHelper.STORAGE_USAGE_AGG,
                        String.join(BillingHelper.ES_DOC_AGGS_SEPARATOR, BillingHelper.HISTOGRAM_AGGREGATION_NAME, BillingHelper.STORAGE_USAGE_AGG));
    }

    private BucketSortPipelineAggregationBuilder aggregateCostSortBucket() {
        return PipelineAggregatorBuilders.bucketSort(BillingHelper.SORT_AGG,
                Collections.singletonList(new FieldSortBuilder(BillingHelper.COST_FIELD)
                        .order(SortOrder.DESC)));
    }
}

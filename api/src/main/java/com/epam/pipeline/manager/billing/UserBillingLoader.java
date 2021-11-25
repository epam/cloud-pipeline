package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.GeneralBillingMetrics;
import com.epam.pipeline.entity.billing.UserGeneralBilling;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBillingLoader implements BillingLoader<UserGeneralBilling> {

    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;
    private final UserBillingDetailsLoader userBillingDetailsLoader;

    @Override
    public Stream<UserGeneralBilling> billings(final RestHighLevelClient elasticSearchClient,
                                               final BillingExportRequest request) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        final int numberOfPartitions = getEstimatedNumberOfBillingPartitions(elasticSearchClient, from, to, filters);
        final BillingDiscount discount = Optional.ofNullable(request.getDiscount()).orElseGet(BillingDiscount::empty);
        return billings(elasticSearchClient, from, to, filters, discount, numberOfPartitions);
    }

    private int getEstimatedNumberOfBillingPartitions(final RestHighLevelClient elasticSearchClient,
                                                      final LocalDate from,
                                                      final LocalDate to,
                                                      final Map<String, List<String>> filters) {
        return BigDecimal.valueOf(getEstimatedNumberOfBillings(elasticSearchClient, from, to, filters))
                .divide(BigDecimal.valueOf(getPartitionSize()), RoundingMode.CEILING)
                .max(BigDecimal.ONE)
                .intValue();
    }

    private int getEstimatedNumberOfBillings(final RestHighLevelClient elasticsearchClient,
                                             final LocalDate from,
                                             final LocalDate to,
                                             final Map<String, List<String>> filters) {
        return Optional.of(getEstimatedNumberOfBillingsRequest(from, to, filters))
                .map(billingHelper.searchWith(elasticsearchClient))
                .map(SearchResponse::getAggregations)
                .flatMap(billingHelper::getCardinality)
                .orElse(NumberUtils.INTEGER_ZERO);
    }

    private SearchRequest getEstimatedNumberOfBillingsRequest(final LocalDate from,
                                                              final LocalDate to,
                                                              final Map<String, List<String>> filters) {
        return new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.storageIndicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(NumberUtils.INTEGER_ZERO)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(billingHelper.aggregateCardinalityBy(BillingUtils.OWNER_FIELD)));
    }

    private int getPartitionSize() {
        return Optional.of(SystemPreferences.BILLING_EXPORT_PERIOD_AGGREGATION_PARTITION_SIZE)
                .map(preferenceManager::getPreference)
                .orElse(BillingUtils.FALLBACK_EXPORT_PERIOD_AGGREGATION_PARTITION_SIZE);
    }

    private Stream<UserGeneralBilling> billings(final RestHighLevelClient elasticSearchClient,
                                                final LocalDate from,
                                                final LocalDate to,
                                                final Map<String, List<String>> filters,
                                                final BillingDiscount discount,
                                                final int numberOfPartitions) {
        return IntStream.range(0, numberOfPartitions)
                .mapToObj(partition -> getBillingsRequest(from, to, filters, discount, partition, numberOfPartitions))
                .map(billingHelper.searchWith(elasticSearchClient))
                .flatMap(this::billings);
    }

    private SearchRequest getBillingsRequest(final LocalDate from,
                                             final LocalDate to,
                                             final Map<String, List<String>> filters,
                                             final BillingDiscount discount,
                                             final int partition,
                                             final int numberOfPartitions) {
        return new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.indicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(NumberUtils.INTEGER_ZERO)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(billingHelper.aggregatePartitionBy(BillingUtils.OWNER_FIELD,
                                        partition, numberOfPartitions)
                                .size(Integer.MAX_VALUE)
                                .subAggregation(aggregateBillingsByMonth(discount))
                                .subAggregation(billingHelper.aggregateRunCountSumBucket())
                                .subAggregation(billingHelper.aggregateRunUsageSumBucket())
                                .subAggregation(billingHelper.aggregateRunCostSumBucket())
                                .subAggregation(billingHelper.aggregateStorageCostSumBucket()))
                        .aggregation(aggregateBillingsByMonth(discount))
                        .aggregation(billingHelper.aggregateRunCountSumBucket())
                        .aggregation(billingHelper.aggregateRunUsageSumBucket())
                        .aggregation(billingHelper.aggregateRunCostSumBucket())
                        .aggregation(billingHelper.aggregateStorageCostSumBucket()));
    }

    private DateHistogramAggregationBuilder aggregateBillingsByMonth(final BillingDiscount discount) {
        return billingHelper.aggregateByMonth()
                .subAggregation(billingHelper.aggregateUniqueRunsCount())
                .subAggregation(billingHelper.aggregateRunUsageSum())
                .subAggregation(billingHelper.aggregateFilteredRuns()
                        .subAggregation(billingHelper.aggregateCostSum(discount.getComputes())))
                .subAggregation(billingHelper.aggregateFilteredStorages()
                        .subAggregation(billingHelper.aggregateCostSum(discount.getStorages())));
    }

    private Stream<UserGeneralBilling> billings(final SearchResponse response) {
        return Stream.concat(
                billingHelper.termBuckets(response.getAggregations(), BillingUtils.OWNER_FIELD)
                        .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                        .map(this::withDetails),
                Stream.of(getBilling(BillingUtils.SYNTHETIC_TOTAL_BILLING, response.getAggregations())));
    }

    private UserGeneralBilling getBilling(final String name, final Aggregations aggregations) {
        return UserGeneralBilling.builder()
                .name(name)
                .totalMetrics(GeneralBillingMetrics.builder()
                        .runsNumber(billingHelper.getRunCount(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .runsDuration(billingHelper.getRunUsageSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .runsCost(billingHelper.getRunCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .storagesCost(billingHelper.getStorageCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .build())
                .periodMetrics(getPeriodMetrics(aggregations))
                .build();
    }

    private Map<YearMonth, GeneralBillingMetrics> getPeriodMetrics(final Aggregations aggregations) {
        return billingHelper.histogramBuckets(aggregations, BillingUtils.HISTOGRAM_AGGREGATION_NAME)
                .map(bucket -> getPeriodMetrics(bucket.getKeyAsString(), bucket.getAggregations()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<YearMonth, GeneralBillingMetrics> getPeriodMetrics(final String ym, final Aggregations aggregations) {
        return Pair.of(
                YearMonth.parse(ym, DateTimeFormatter.ofPattern(BillingUtils.HISTOGRAM_AGGREGATION_FORMAT)),
                GeneralBillingMetrics.builder()
                        .runsNumber(billingHelper.getRunCount(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .runsDuration(billingHelper.getRunUsageSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .runsCost(billingHelper.getFilteredRunCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .storagesCost(billingHelper.getFilteredStorageCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .build());
    }

    private UserGeneralBilling withDetails(final UserGeneralBilling billing) {
        final Map<String, String> details = userBillingDetailsLoader.loadDetails(billing.getName());
        return billing.toBuilder()
                .billingCenter(details.get(BillingUtils.BILLING_CENTER_FIELD))
                .build();
    }
}

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingCenterGeneralBilling;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.GeneralBillingMetrics;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.StreamUtils;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCenterBillingLoader implements BillingLoader<BillingCenterGeneralBilling> {

    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;

    @Override
    public Stream<BillingCenterGeneralBilling> billings(final RestHighLevelClient elasticSearchClient,
                                                        final BillingExportRequest request) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        final BillingDiscount discount = Optional.ofNullable(request.getDiscount()).orElseGet(BillingDiscount::empty);
        return billings(elasticSearchClient, from, to, filters, discount, getPageSize());
    }

    private int getPageSize() {
        return Optional.of(SystemPreferences.BILLING_EXPORT_PERIOD_AGGREGATION_PAGE_SIZE)
                .map(preferenceManager::getPreference)
                .orElse(BillingUtils.FALLBACK_EXPORT_PERIOD_AGGREGATION_PAGE_SIZE);
    }

    private Stream<BillingCenterGeneralBilling> billings(final RestHighLevelClient elasticSearchClient,
                                                         final LocalDate from,
                                                         final LocalDate to,
                                                         final Map<String, List<String>> filters,
                                                         final BillingDiscount discount,
                                                         final int pageSize) {
        return StreamUtils.from(billingsIterator(elasticSearchClient, from, to, filters, discount, pageSize))
                .flatMap(this::billings);
    }

    private Iterator<SearchResponse> billingsIterator(final RestHighLevelClient elasticSearchClient,
                                                      final LocalDate from,
                                                      final LocalDate to,
                                                      final Map<String, List<String>> filters,
                                                      final BillingDiscount discount,
                                                      final int pageSize) {
        return new ElasticMultiBucketsIterator(BillingUtils.BILLING_CENTER_FIELD, pageSize,
                pageOffset -> getBillingsRequest(from, to, filters, discount, pageOffset, pageSize),
                billingHelper.searchWith(elasticSearchClient),
                billingHelper::getTerms);
    }

    private SearchRequest getBillingsRequest(final LocalDate from,
                                             final LocalDate to,
                                             final Map<String, List<String>> filters,
                                             final BillingDiscount discount,
                                             final int pageOffset,
                                             final int pageSize) {
        return new SearchRequest()
                .indicesOptions(IndicesOptions.strictExpandOpen())
                .indices(billingHelper.indicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(NumberUtils.INTEGER_ZERO)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(billingHelper.aggregateBy(BillingUtils.BILLING_CENTER_FIELD)
                                .size(Integer.MAX_VALUE)
                                .missing(BillingUtils.MISSING_VALUE)
                                .subAggregation(aggregateBillingsByMonth(discount))
                                .subAggregation(billingHelper.aggregateUniqueRunsCount())
                                .subAggregation(billingHelper.aggregateRunUsageSumBucket())
                                .subAggregation(billingHelper.aggregateRunCostSumBucket())
                                .subAggregation(billingHelper.aggregateStorageCostSumBucket())
                                .subAggregation(billingHelper.aggregateCostSumBucket())
                                .subAggregation(billingHelper.aggregateCostSortBucket(pageOffset, pageSize)))
                        .aggregation(aggregateBillingsByMonth(discount))
                        .aggregation(billingHelper.aggregateUniqueRunsCount())
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
                        .subAggregation(billingHelper.aggregateCostSum(discount.getStorages())))
                .subAggregation(billingHelper.aggregateCostSum());
    }

    private Stream<BillingCenterGeneralBilling> billings(final SearchResponse response) {
        return Stream.concat(
                billingHelper.termBuckets(response.getAggregations(), BillingUtils.BILLING_CENTER_FIELD)
                        .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations())),
                Stream.of(getBilling(BillingUtils.SYNTHETIC_TOTAL_BILLING, response.getAggregations())));
    }

    private BillingCenterGeneralBilling getBilling(final String name, final Aggregations aggregations) {
        return BillingCenterGeneralBilling.builder()
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
}

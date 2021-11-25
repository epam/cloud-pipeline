package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.InstanceBilling;
import com.epam.pipeline.entity.billing.InstanceBillingMetrics;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceBillingLoader implements BillingLoader<InstanceBilling> {

    private final BillingHelper billingHelper;

    @Override
    public Stream<InstanceBilling> billings(final RestHighLevelClient elasticSearchClient,
                                            final BillingExportRequest request) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        final BillingDiscount discount = Optional.ofNullable(request.getDiscount()).orElseGet(BillingDiscount::empty);
        return billings(elasticSearchClient, from, to, filters, discount);
    }

    private Stream<InstanceBilling> billings(final RestHighLevelClient elasticSearchClient,
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
                .indices(billingHelper.runIndicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(NumberUtils.INTEGER_ZERO)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(billingHelper.aggregateBy(BillingGrouping.RUN_INSTANCE_TYPE.getCorrespondingField())
                                .size(Integer.MAX_VALUE)
                                .subAggregation(aggregateBillingsByMonth(discount))
                                .subAggregation(billingHelper.aggregateRunCountSumBucket())
                                .subAggregation(billingHelper.aggregateRunUsageSumBucket())
                                .subAggregation(billingHelper.aggregateCostSumBucket())
                                .subAggregation(billingHelper.aggregateCostSortBucket())));
    }

    private DateHistogramAggregationBuilder aggregateBillingsByMonth(final BillingDiscount discount) {
        return billingHelper.aggregateByMonth()
                .subAggregation(billingHelper.aggregateUniqueRunsCount())
                .subAggregation(billingHelper.aggregateRunUsageSum())
                .subAggregation(billingHelper.aggregateCostSum(discount.getComputes()));
    }

    private Stream<InstanceBilling> billings(final SearchResponse response) {
        return billingHelper.termBuckets(response.getAggregations(),
                        BillingGrouping.RUN_INSTANCE_TYPE.getCorrespondingField())
                .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()));
    }

    private InstanceBilling getBilling(final String name, final Aggregations aggregations) {
        return InstanceBilling.builder()
                .name(name)
                .totalMetrics(InstanceBillingMetrics.builder()
                        .runsNumber(billingHelper.getRunCount(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .runsDuration(billingHelper.getRunUsageSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .runsCost(billingHelper.getCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .build())
                .periodMetrics(getPeriodMetrics(aggregations))
                .build();
    }

    private Map<YearMonth, InstanceBillingMetrics> getPeriodMetrics(final Aggregations aggregations) {
        return billingHelper.histogramBuckets(aggregations, BillingUtils.HISTOGRAM_AGGREGATION_NAME)
                .map(bucket -> getPeriodMetrics(bucket.getKeyAsString(), bucket.getAggregations()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<YearMonth, InstanceBillingMetrics> getPeriodMetrics(final String ym, final Aggregations aggregations) {
        return Pair.of(
                YearMonth.parse(ym, DateTimeFormatter.ofPattern(BillingUtils.HISTOGRAM_AGGREGATION_FORMAT)),
                InstanceBillingMetrics.builder()
                        .runsNumber(billingHelper.getRunCount(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .runsDuration(billingHelper.getRunUsageSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .runsCost(billingHelper.getCostSum(aggregations).orElse(NumberUtils.LONG_ZERO))
                        .build());
    }
}

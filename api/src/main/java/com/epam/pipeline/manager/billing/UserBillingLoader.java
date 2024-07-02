package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.GeneralBillingMetrics;
import com.epam.pipeline.entity.billing.UserGeneralBilling;
import com.epam.pipeline.manager.billing.detail.EntityBillingDetailsLoader;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.time.temporal.Temporal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBillingLoader implements BillingLoader<UserGeneralBilling> {

    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;
    private final EntityBillingDetailsLoader userBillingDetailsLoader;

    @Override
    public Stream<UserGeneralBilling> billings(final RestHighLevelClient elasticSearchClient,
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

    private Stream<UserGeneralBilling> billings(final RestHighLevelClient elasticSearchClient,
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
        return new ElasticMultiBucketsIterator(BillingUtils.OWNER_FIELD, pageSize,
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
                        .aggregation(billingHelper.aggregateBy(BillingUtils.OWNER_FIELD)
                                .size(Integer.MAX_VALUE)
                                .subAggregation(aggregateBillingsByMonth(discount))
                                .subAggregation(billingHelper.aggregateUniqueRunsCount())
                                .subAggregation(billingHelper.aggregateRunUsageSumBucket())
                                .subAggregation(billingHelper.aggregateRunCostSumBucket())
                                .subAggregation(billingHelper.aggregateStorageCostSumBucket())
                                .subAggregation(billingHelper.aggregateCostSumBucket())
                                .subAggregation(billingHelper.aggregateLastByDateDoc())
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

    private Stream<UserGeneralBilling> billings(final SearchResponse response) {
        return Stream.concat(
                billingHelper.termBuckets(response.getAggregations(), BillingUtils.OWNER_FIELD)
                        .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                        .map(this::withDetails),
                Stream.of(getBilling(BillingUtils.SYNTHETIC_TOTAL_BILLING, response.getAggregations())));
    }

    private UserGeneralBilling getBilling(final String name, final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        return UserGeneralBilling.builder()
                .name(name)
                .billingCenter(BillingUtils.asString(topHitFields.get(BillingUtils.BILLING_CENTER_FIELD)))
                .totalMetrics(GeneralBillingMetrics.builder()
                        .runsNumber(billingHelper.getRunCount(aggregations))
                        .runsDuration(billingHelper.getRunUsageSum(aggregations))
                        .runsCost(billingHelper.getRunCostSum(aggregations))
                        .storagesCost(billingHelper.getStorageCostSum(aggregations))
                        .build())
                .periodMetrics(getMetrics(aggregations))
                .build();
    }

    private Map<Temporal, GeneralBillingMetrics> getMetrics(final Aggregations aggregations) {
        return billingHelper.histogramBuckets(aggregations, BillingUtils.HISTOGRAM_AGGREGATION_NAME)
                .map(bucket -> getMetrics(bucket.getKeyAsString(), bucket.getAggregations()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<Temporal, GeneralBillingMetrics> getMetrics(final String period, final Aggregations aggregations) {
        return Pair.of(
                YearMonth.parse(period, DateTimeFormatter.ofPattern(BillingUtils.HISTOGRAM_AGGREGATION_FORMAT)),
                GeneralBillingMetrics.builder()
                        .runsNumber(billingHelper.getRunCount(aggregations))
                        .runsDuration(billingHelper.getRunUsageSum(aggregations))
                        .runsCost(billingHelper.getFilteredRunCostSum(aggregations))
                        .storagesCost(billingHelper.getFilteredStorageCostSum(aggregations))
                        .build());
    }

    private UserGeneralBilling withDetails(final UserGeneralBilling billing) {
        if (StringUtils.isNotBlank(billing.getBillingCenter())) {
            return billing;
        }
        final Map<String, String> details = userBillingDetailsLoader.loadDetails(billing.getName());
        return billing.toBuilder()
                .billingCenter(StringUtils.isNotBlank(billing.getBillingCenter())
                        ? billing.getBillingCenter()
                        : details.get(BillingUtils.BILLING_CENTER_FIELD))
                .build();
    }
}

/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.ToolBilling;
import com.epam.pipeline.entity.billing.ToolBillingMetrics;
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
public class ToolBillingLoader implements BillingLoader<ToolBilling> {

    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;
    private final EntityBillingDetailsLoader toolBillingDetailsLoader;

    @Override
    public Stream<ToolBilling> billings(final RestHighLevelClient elasticSearchClient,
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

    private Stream<ToolBilling> billings(final RestHighLevelClient elasticSearchClient,
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
        return new ElasticMultiBucketsIterator(BillingUtils.TOOL_FIELD, pageSize,
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
                .indices(billingHelper.runIndicesByDate(from, to))
                .source(new SearchSourceBuilder()
                        .size(NumberUtils.INTEGER_ZERO)
                        .query(billingHelper.queryByDateAndFilters(from, to, filters))
                        .aggregation(billingHelper.aggregateBy(BillingUtils.TOOL_FIELD)
                                .size(Integer.MAX_VALUE)
                                .subAggregation(aggregateBillingsByMonth(discount))
                                .subAggregation(billingHelper.aggregateUniqueRunsCount())
                                .subAggregation(billingHelper.aggregateRunUsageSumBucket())
                                .subAggregation(billingHelper.aggregateCostSumBucket())
                                .subAggregation(billingHelper.aggregateDiskCostSumBucket())
                                .subAggregation(billingHelper.aggregateComputeCostSumBucket())
                                .subAggregation(billingHelper.aggregateLastByDateDoc())
                                .subAggregation(billingHelper.aggregateCostSortBucket(pageOffset, pageSize))));
    }

    private DateHistogramAggregationBuilder aggregateBillingsByMonth(final BillingDiscount discount) {
        return billingHelper.aggregateByMonth()
                .subAggregation(billingHelper.aggregateUniqueRunsCount())
                .subAggregation(billingHelper.aggregateRunUsageSum())
                .subAggregation(billingHelper.aggregateCostSum(discount.getComputes()))
                .subAggregation(billingHelper.aggregateDiskCostSum(discount.getComputes()))
                .subAggregation(billingHelper.aggregateComputeCostSum(discount.getComputes()));
    }

    private Stream<ToolBilling> billings(final SearchResponse response) {
        return billingHelper.termBuckets(response.getAggregations(), BillingUtils.TOOL_FIELD)
                .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()))
                .map(this::withDetails);
    }

    private ToolBilling getBilling(final String name, final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        return ToolBilling.builder()
                .name(name)
                .owner(BillingUtils.asString(topHitFields.get(BillingUtils.OWNER_FIELD)))
                .totalMetrics(ToolBillingMetrics.builder()
                        .runsNumber(billingHelper.getRunCount(aggregations))
                        .runsDuration(billingHelper.getRunUsageSum(aggregations))
                        .runsCost(billingHelper.getCostSum(aggregations))
                        .runsDiskCost(billingHelper.getDiskCostSum(aggregations))
                        .runsComputeCost(billingHelper.getComputeCostSum(aggregations))
                        .build())
                .periodMetrics(getMetrics(aggregations))
                .build();
    }

    private Map<Temporal, ToolBillingMetrics> getMetrics(final Aggregations aggregations) {
        return billingHelper.histogramBuckets(aggregations, BillingUtils.HISTOGRAM_AGGREGATION_NAME)
                .map(bucket -> getMetrics(bucket.getKeyAsString(), bucket.getAggregations()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<Temporal, ToolBillingMetrics> getMetrics(final String period, final Aggregations aggregations) {
        return Pair.of(
                YearMonth.parse(period, DateTimeFormatter.ofPattern(BillingUtils.HISTOGRAM_AGGREGATION_FORMAT)),
                ToolBillingMetrics.builder()
                        .runsNumber(billingHelper.getRunCount(aggregations))
                        .runsDuration(billingHelper.getRunUsageSum(aggregations))
                        .runsCost(billingHelper.getCostSum(aggregations))
                        .runsDiskCost(billingHelper.getDiskCostSum(aggregations))
                        .runsComputeCost(billingHelper.getComputeCostSum(aggregations))
                        .build());
    }

    private ToolBilling withDetails(final ToolBilling billing) {
        if (StringUtils.isNotBlank(billing.getOwner())) {
            return billing;
        }
        final Map<String, String> details = toolBillingDetailsLoader.loadDetails(billing.getName());
        return billing.toBuilder()
                .owner(StringUtils.isNotBlank(billing.getOwner())
                        ? billing.getOwner()
                        : details.get(EntityBillingDetailsLoader.OWNER))
                .build();
    }
}

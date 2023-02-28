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
import com.epam.pipeline.entity.billing.RunBilling;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunBillingLoader implements BillingLoader<RunBilling> {

    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;

    @Override
    public Stream<RunBilling> billings(final RestHighLevelClient elasticSearchClient,
                                       final BillingExportRequest request) {
        final LocalDate from = request.getFrom();
        final LocalDate to = request.getTo();
        final Map<String, List<String>> filters = billingHelper.getFilters(request.getFilters());
        final BillingDiscount discount = Optional.ofNullable(request.getDiscount()).orElseGet(BillingDiscount::empty);
        return billings(elasticSearchClient, from, to, filters, discount, getPageSize());
    }

    private int getPageSize() {
        return Optional.of(SystemPreferences.BILLING_EXPORT_AGGREGATION_PAGE_SIZE)
                .map(preferenceManager::getPreference)
                .orElse(BillingUtils.FALLBACK_EXPORT_AGGREGATION_PAGE_SIZE);
    }

    private Stream<RunBilling> billings(final RestHighLevelClient elasticSearchClient,
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
        return new ElasticMultiBucketsIterator(BillingUtils.RUN_ID_FIELD, pageSize,
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
                        .aggregation(billingHelper.aggregateBy(BillingUtils.RUN_ID_FIELD)
                                .size(Integer.MAX_VALUE)
                                .subAggregation(billingHelper.aggregateCostSum(discount.getComputes()))
                                .subAggregation(BillingUtils.aggregateDiscountCostSum(
                                        BillingUtils.DISK_COST_FIELD, discount.getComputes()))
                                .subAggregation(BillingUtils.aggregateDiscountCostSum(
                                        BillingUtils.COMPUTE_COST_FIELD, discount.getComputes()))
                                .subAggregation(billingHelper.aggregateRunUsageSum())
                                .subAggregation(billingHelper.aggregateLastByDateDoc())
                                .subAggregation(billingHelper.aggregateCostSortBucket(pageOffset, pageSize))));
    }

    private Stream<RunBilling> billings(final SearchResponse response) {
        return billingHelper.termBuckets(response.getAggregations(), BillingUtils.RUN_ID_FIELD)
                .map(bucket -> getBilling(bucket.getKeyAsString(), bucket.getAggregations()));
    }

    private RunBilling getBilling(final String id, final Aggregations aggregations) {
        final Map<String, Object> topHitFields = billingHelper.getLastByDateDocFields(aggregations);
        return RunBilling.builder()
                .runId(NumberUtils.toLong(id))
                .owner(BillingUtils.asString(topHitFields.get(BillingUtils.OWNER_FIELD)))
                .billingCenter(BillingUtils.asString(topHitFields.get(BillingUtils.BILLING_CENTER_FIELD)))
                .pipeline(BillingUtils.asString(topHitFields.get(BillingUtils.PIPELINE_NAME_FIELD)))
                .tool(BillingUtils.asString(topHitFields.get(BillingUtils.TOOL_FIELD)))
                .computeType(BillingUtils.asString(topHitFields.get(BillingUtils.COMPUTE_TYPE_FIELD)))
                .instanceType(BillingUtils.asString(topHitFields.get(BillingUtils.INSTANCE_TYPE_FIELD)))
                .started(BillingUtils.asDateTime(topHitFields.get(BillingUtils.STARTED_FIELD)))
                .finished(BillingUtils.asDateTime(topHitFields.get(BillingUtils.FINISHED_FIELD)))
                .duration(billingHelper.getRunUsageSum(aggregations))
                .cost(billingHelper.getCostSum(aggregations))
                .diskCost(billingHelper.getLongValue(aggregations, BillingUtils.DISK_COST_FIELD))
                .computeCost(billingHelper.getLongValue(aggregations, BillingUtils.COMPUTE_COST_FIELD))
                .build();
    }
}

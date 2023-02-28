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

package com.epam.pipeline.manager.billing.billingdetails;

import com.epam.pipeline.controller.vo.billing.BillingCostDetailsRequest;
import com.epam.pipeline.entity.billing.BillingChartDetails;
import com.epam.pipeline.entity.billing.BillingDiscount;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.ComputeBillingChartCostDetails;
import com.epam.pipeline.manager.billing.BillingUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.ParsedSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.manager.billing.BillingUtils.COMPUTE_GROUP;
import static com.epam.pipeline.manager.billing.BillingUtils.RESOURCE_TYPE;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ComputeBillingCostDetailsLoader {

    public static boolean isComputeCostDetailsShallBeLoaded(final BillingCostDetailsRequest request) {
        if (request.isHistogram()) {
            return MapUtils.emptyIfNull(request.getFilters())
                    .getOrDefault(RESOURCE_TYPE, Collections.emptyList()).contains(COMPUTE_GROUP);
        }
        final BillingGrouping grouping = request.getGrouping();
        if (grouping != null) {
            return BillingGrouping.TOOL.equals(grouping) || BillingGrouping.PIPELINE.equals(grouping)
                    || BillingGrouping.RUN_COMPUTE_TYPE.equals(grouping)
                    || BillingGrouping.RUN_INSTANCE_TYPE.equals(grouping);
        }
        return false;
    }

    public static void buildQuery(final BillingCostDetailsRequest request,
                                  final AggregationBuilder topLevelAggregation) {
        topLevelAggregation
                .subAggregation(aggregateDiskCostSum())
                .subAggregation(aggregateComputeCostSum());
        if (request.isHistogram()) {
            topLevelAggregation
                    .subAggregation(PipelineAggregatorBuilders.cumulativeSum(BillingUtils.ACCUMULATED_DISK_COST,
                            BillingUtils.DISK_COST_FIELD))
                    .subAggregation(PipelineAggregatorBuilders.cumulativeSum(BillingUtils.ACCUMULATED_COMPUTE_COST,
                            BillingUtils.COMPUTE_COST_FIELD));
        }
    }

    public static BillingChartDetails parseResponse(final BillingCostDetailsRequest request,
                                                    final Aggregations aggregations) {
        final ComputeBillingChartCostDetails.ComputeBillingChartCostDetailsBuilder builder =
                ComputeBillingChartCostDetails.builder()
                        .diskCost(parseSum(aggregations, BillingUtils.DISK_COST_FIELD))
                        .computeCost(parseSum(aggregations, BillingUtils.COMPUTE_COST_FIELD));
        if (request.isHistogram()) {
            return builder
                    .accumulatedDiskCost(parseAccumulatedSum(aggregations, BillingUtils.ACCUMULATED_DISK_COST))
                    .accumulatedComputeCost(parseAccumulatedSum(aggregations, BillingUtils.ACCUMULATED_COMPUTE_COST))
                    .build();
        }
        return builder.build();
    }

    public static List<String> getCostDetailsAggregations() {
        return Arrays.asList(aggregateDiskCostSum().getName(), aggregateComputeCostSum().getName());
    }

    public static SumAggregationBuilder aggregateDiskCostSum() {
        return AggregationBuilders.sum(BillingUtils.DISK_COST_FIELD).field(BillingUtils.DISK_COST_FIELD);
    }

    public static SumAggregationBuilder aggregateComputeCostSum() {
        return AggregationBuilders.sum(BillingUtils.COMPUTE_COST_FIELD).field(BillingUtils.COMPUTE_COST_FIELD);
    }

    public static AggregationBuilder aggregateDiskCostSum(final BillingDiscount discount) {
        return BillingUtils.aggregateDiscountCostSum(BillingUtils.DISK_COST_FIELD, discount.getComputes(),
                ComputeBillingCostDetailsLoader::aggregateDiskCostSum);
    }

    public static AggregationBuilder aggregateComputeCostSum(final BillingDiscount discount) {
        return BillingUtils.aggregateDiscountCostSum(BillingUtils.COMPUTE_COST_FIELD, discount.getComputes(),
                ComputeBillingCostDetailsLoader::aggregateComputeCostSum);
    }

    private static Long parseSum(final Aggregations aggregations, final String field) {
        final ParsedSum sumAggResult = aggregations.get(field);
        return Optional.ofNullable(sumAggResult)
                .map(result -> new Double(result.getValue()).longValue())
                .orElse(null);
    }

    private static Long parseAccumulatedSum(final Aggregations aggregations, final String field) {
        final ParsedSimpleValue accumulatedSumAggResult = aggregations.get(field);
        return Optional.ofNullable(accumulatedSumAggResult)
                .map(result -> new Double(result.getValueAsString()).longValue())
                .orElse(null);
    }
}

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
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.StorageBillingChartCostDetails;
import com.epam.pipeline.manager.billing.BillingUtils;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.ParsedSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.sum.SumBucketPipelineAggregationBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StorageBillingCostDetailsHelper {

    public static final String STORAGES_COST_DETAILS = "storages_cost_details";

    private StorageBillingCostDetailsHelper() {}

    public static final List<String> S3_STORAGE_CLASSES =
            Arrays.asList("STANDARD", "GLACIER", "GLACIER_IR", "DEEP_ARCHIVE");

    private static final String STORAGE_CLASS_COST_TEMPLATE = "%s_cost";
    private static final String STORAGE_CLASS_USAGE_TEMPLATE = "%s_usage_bytes";
    private static final String STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE = "%s_ov_cost";
    private static final String STORAGE_CLASS_OLD_VERSIONS_USAGE_TEMPLATE = "%s_ov_usage_bytes";

    public static final List<String> STORAGE_COST_DETAILS_AGGREGATION_MASKS = Arrays.asList(
            "_cost", "_ov_cost", "_usage_bytes", "_ov_usage_bytes"
    );

    public static void buildQuery(final AggregationBuilder topLevelAggregation) {
        List<AggregationBuilder> storageDetailsAggregations = S3_STORAGE_CLASSES.stream()
                .map(sc -> sc.toLowerCase(Locale.ROOT))
                .flatMap(sc -> Stream.of(
                        buildSumAggregation(STORAGE_CLASS_COST_TEMPLATE, sc),
                        buildSumAggregation(STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE, sc),
                        buildAvgAggregation(STORAGE_CLASS_USAGE_TEMPLATE, sc),
                        buildAvgAggregation(STORAGE_CLASS_OLD_VERSIONS_USAGE_TEMPLATE, sc)
                )).collect(Collectors.toList());
        final TermsAggregationBuilder usageByStorageAgg = AggregationBuilders.terms(STORAGES_COST_DETAILS)
            .field(BillingUtils.STORAGE_ID_FIELD).size(Integer.MAX_VALUE);
        for (AggregationBuilder storageDetailsAggregation : storageDetailsAggregations) {
            usageByStorageAgg.subAggregation(storageDetailsAggregation);
        }
        topLevelAggregation.subAggregation(usageByStorageAgg);
        S3_STORAGE_CLASSES.stream()
            .map(sc -> sc.toLowerCase(Locale.ROOT))
            .flatMap(sc -> Stream.of(
                buildPipelineSumAggregation(STORAGES_COST_DETAILS, STORAGE_CLASS_COST_TEMPLATE, sc),
                buildPipelineSumAggregation(STORAGES_COST_DETAILS, STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE, sc),
                buildPipelineSumAggregation(STORAGES_COST_DETAILS, STORAGE_CLASS_USAGE_TEMPLATE, sc),
                buildPipelineSumAggregation(STORAGES_COST_DETAILS, STORAGE_CLASS_OLD_VERSIONS_USAGE_TEMPLATE, sc)
            )
        ).forEach(topLevelAggregation::subAggregation);
    }

    public static BillingChartDetails parseResponse(final Aggregations aggregations) {
        return StorageBillingChartCostDetails.builder().tiers(
            S3_STORAGE_CLASSES.stream()
                .map(field ->
                StorageBillingChartCostDetails.StorageBillingDetails.builder()
                    .storageClass(field)
                    .cost(fetchSimpleAggregationValue(STORAGE_CLASS_COST_TEMPLATE, field, aggregations))
                    .size(fetchSimpleAggregationValue(STORAGE_CLASS_USAGE_TEMPLATE, field, aggregations))
                    .oldVersionCost(
                        fetchSimpleAggregationValue(STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE, field, aggregations))
                    .oldVersionSize(
                        fetchSimpleAggregationValue(STORAGE_CLASS_OLD_VERSIONS_USAGE_TEMPLATE, field, aggregations)
                ).build())
                .filter(details -> !isDetailsEntryEmpty(details))
                .collect(Collectors.toList())
        ).build();
    }

    static boolean isStorageBillingDetailsShouldBeLoaded(final BillingCostDetailsRequest request) {
        if (!request.isEnabled()) {
            return false;
        }
        final BillingGrouping grouping = request.getGrouping();
        if (BillingGrouping.STORAGE.equals(grouping) || BillingGrouping.STORAGE_TYPE.equals(grouping)) {
            return true;
        } else {
            final Map<String, List<String>> filters = MapUtils.emptyIfNull(request.getFilters());
            final Boolean onlyStorageRTypeRequestedOrNothing = Optional.ofNullable(filters.get("resource_type"))
                    .map(values -> (values.contains("STORAGE") && values.size() == 1) || values.isEmpty())
                    .orElse(true);
            final Boolean onlyObjectStorageSTypeRequested = Optional.ofNullable(filters.get("storage_type"))
                    .map(values -> values.contains("OBJECT_STORAGE") && values.size() == 1).orElse(false);
            return onlyObjectStorageSTypeRequested && onlyStorageRTypeRequestedOrNothing;
        }
    }

    private static boolean isDetailsEntryEmpty(StorageBillingChartCostDetails.StorageBillingDetails details) {
        return details.getCost() == 0 && details.getSize() == 0
                && details.getOldVersionCost() == 0 && details.getOldVersionSize() == 0;
    }

    private static long fetchSimpleAggregationValue(final String template, final String field,
                                                    final Aggregations aggregations) {
        final String costAggName = String.format(template, field.toLowerCase(Locale.ROOT));
        return Optional.ofNullable(aggregations).map(aggs -> aggs.<ParsedSimpleValue>get(costAggName))
                .map(ParsedSimpleValue::value).orElse(0.0).longValue();
    }

    private static SumAggregationBuilder buildSumAggregation(final String template, final String storageClass) {
        final String agg = getAggregationField(template, storageClass);
        return AggregationBuilders.sum(agg).field(agg);
    }

    private static AvgAggregationBuilder buildAvgAggregation(final String template, final String storageClass) {
        final String agg = getAggregationField(template, storageClass);
        return AggregationBuilders.avg(agg).field(agg);
    }

    private static SumBucketPipelineAggregationBuilder buildPipelineSumAggregation(final String parentAgg,
                                                                                   final String template,
                                                                                   final String storageClass) {
        final String agg = getAggregationField(template, storageClass);
        return PipelineAggregatorBuilders.sumBucket(agg, fieldsPath(parentAgg, agg));
    }

    private static String getAggregationField(String template, String field) {
        return String.format(template, field);
    }

    private static String fieldsPath(final String... paths) {
        return bucketsPath(BillingUtils.ES_DOC_FIELDS_SEPARATOR, paths);
    }

    private static String bucketsPath(final String separator, final String[] paths) {
        return String.join(separator, paths);
    }
}

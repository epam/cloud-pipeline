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
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class StorageBillingCostDetailsHelper {

    private StorageBillingCostDetailsHelper() {}

    public static final List<String> S3_STORAGE_CLASSES =
            Arrays.asList("STANDARD", "GLACIER", "GLACIER_IR", "DEEP_ARCHIVE");

    private static final String STORAGE_CLASS_COST_TEMPLATE = "%s_cost";
    private static final String STORAGE_CLASS_USAGE_BYTES_TEMPLATE = "%s_usage_bytes";
    private static final String STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE = "%s_ov_cost";
    private static final String STORAGE_CLASS_OLD_VERSIONS_USAGE_BYTES_TEMPLATE = "%s_ov_usage_bytes";
    public static final List<String> STORAGE_CLASS_AGGREGATION_TEMPLATES =
            Arrays.asList(STORAGE_CLASS_COST_TEMPLATE, STORAGE_CLASS_USAGE_BYTES_TEMPLATE,
                    STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE, STORAGE_CLASS_OLD_VERSIONS_USAGE_BYTES_TEMPLATE);

    public static final List<String> STORAGE_CLASS_AGGREGATION_MASKS = Arrays.asList(
            "*_cost", "*_ov_cost", "*_usage_bytes", "*_ov_usage_bytes"
    );

    public static List<AggregationBuilder> buildQuery() {
        return S3_STORAGE_CLASSES.stream()
                .map(sc -> sc.toLowerCase(Locale.ROOT))
                .flatMap(sc -> STORAGE_CLASS_AGGREGATION_TEMPLATES.stream().map(t -> buildSumAggregation(t, sc)))
                .collect(Collectors.toList());
    }


    public static BillingChartDetails parseResponse(final Aggregations aggregations) {
        return StorageBillingChartCostDetails.builder().tiers(
            S3_STORAGE_CLASSES.stream()
                .map(field ->
                StorageBillingChartCostDetails.StorageBillingDetails.builder()
                    .storageClass(field)
                    .cost(fetchAggregationValue(STORAGE_CLASS_COST_TEMPLATE, field, aggregations))
                    .size(fetchAggregationValue(STORAGE_CLASS_USAGE_BYTES_TEMPLATE, field, aggregations))
                    .oldVersionCost(
                        fetchAggregationValue(STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE, field, aggregations))
                    .oldVersionSize(
                        fetchAggregationValue(STORAGE_CLASS_OLD_VERSIONS_USAGE_BYTES_TEMPLATE, field, aggregations)
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

    private static long fetchAggregationValue(final String template, final String field,
                                              final Aggregations aggregations) {
        final String costAggName = String.format(template, field.toLowerCase(Locale.ROOT));
        return Optional.ofNullable(aggregations).map(agg -> agg.<ParsedSum>get(costAggName))
                .map(ParsedSum::getValue).orElse(0.0).longValue();
    }

    private static SumAggregationBuilder buildSumAggregation(final String template, final String storageClass) {
        final String agg = getAggregationField(template, storageClass);
        return buildSumAggregation(agg);
    }

    private static SumAggregationBuilder buildSumAggregation(String agg) {
        return AggregationBuilders.sum(agg).field(agg);
    }

    private static String getAggregationField(String template, String field) {
        return String.format(template, field);
    }
}

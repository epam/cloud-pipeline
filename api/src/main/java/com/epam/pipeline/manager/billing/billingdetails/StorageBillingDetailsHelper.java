package com.epam.pipeline.manager.billing.billingdetails;

import com.epam.pipeline.entity.billing.BillingChartDetails;
import com.epam.pipeline.entity.billing.StorageBillingChartDetails;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;

import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StorageBillingDetailsHelper {

    private final static List<String> S3_STORAGE_CLASSES =
            Arrays.asList("ALL_STORAGE_CLASSES", "STANDARD", "GLACIER", "GLACIER_IR", "DEEP_ARCHIVE");

    private final static String STORAGE_CLASS_COST_TEMPLATE = "%s_cost";
    private final static String STORAGE_CLASS_USAGE_BYTES_TEMPLATE = "%s_usage_bytes";
    private final static String STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE = "%s_ov_cost";
    private final static String STORAGE_CLASS_OLD_VERSIONS_USAGE_BYTES_TEMPLATE = "%s_ov_usage_bytes";
    private final static List<String> STORAGE_CLASS_AGGREGATION_TEMPLATES =
            Arrays.asList(STORAGE_CLASS_COST_TEMPLATE, STORAGE_CLASS_USAGE_BYTES_TEMPLATE,
                    STORAGE_CLASS_OLD_VERSIONS_COST_TEMPLATE, STORAGE_CLASS_OLD_VERSIONS_USAGE_BYTES_TEMPLATE);

    public final static List<String> STORAGE_CLASS_AGGREGATION_MASKS = Arrays.asList(
            "*_cost", "*_ov_cost", "*_usage_bytes", "*_ov_usage_bytes"
    );

    public static List<AggregationBuilder> buildQuery() {
        return  S3_STORAGE_CLASSES.stream()
                .map(field -> field.toLowerCase(Locale.ROOT))
                .flatMap(field -> STORAGE_CLASS_AGGREGATION_TEMPLATES.stream().map(t -> buildAggregation(t, field)))
                .collect(Collectors.toList());
    }


    public static BillingChartDetails parseResponse(final Aggregations aggregations) {
        return StorageBillingChartDetails.builder().tiers(
            S3_STORAGE_CLASSES.stream()
                .map(field ->
                StorageBillingChartDetails.StorageBillingDetails.builder()
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

    private static boolean isDetailsEntryEmpty(StorageBillingChartDetails.StorageBillingDetails details) {
        return details.getCost() == 0 && details.getSize() == 0
                && details.getOldVersionCost() == 0 && details.getOldVersionSize() == 0;
    }

    private static long fetchAggregationValue(final String template, final String field,
                                              final Aggregations aggregations) {
        final String costAggName = String.format(template, field.toLowerCase(Locale.ROOT));
        return Optional.ofNullable(aggregations.<ParsedSum>get(costAggName))
                .map(ParsedSum::getValue).orElse(0.0).longValue();
    }

    private static SumAggregationBuilder buildAggregation(final String template, final String field) {
        final String agg = getAggregationField(template, field);
        return AggregationBuilders.sum(agg).field(agg);
    }

    private static String getAggregationField(String template, String field) {
        return String.format(template, field);
    }

}

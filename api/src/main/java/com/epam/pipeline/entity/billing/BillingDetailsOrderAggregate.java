package com.epam.pipeline.entity.billing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@Getter
@RequiredArgsConstructor
public enum BillingDetailsOrderAggregate {

    DEFAULT(null, getCostSortOrder(), getCostSortOrder()),

    STORAGE(BillingGrouping.STORAGE, getCostSortOrder(), getSortOrder("usage_bytes")),
    STANDARD(BillingGrouping.STORAGE,
            getSortOrder("standard_total_cost"), getSortOrder("standard_total_usage_bytes")),
    GLACIER(BillingGrouping.STORAGE,
            getSortOrder("glacier_total_cost"), getSortOrder("glacier_total_usage_bytes")),
    GLACIER_IR(BillingGrouping.STORAGE,
            getSortOrder("glacier_ir_total_cost"), getSortOrder("glacier_ir_total_usage_bytes")),
    DEEP_ARCHIVE(BillingGrouping.STORAGE,
            getSortOrder("deep_archive_total_cost"), getSortOrder("deep_archive_total_usage_bytes"));

    private static ImmutablePair<String, String> getCostSortOrder() {
        return ImmutablePair.of("c_so", "cost");
    }

    private static ImmutablePair<String, String> getSortOrder(String field) {
        return ImmutablePair.of(field, field);
    }

    private final BillingGrouping group;
    private final Pair<String, String> costAggregate;
    private final Pair<String, String> usageAggregate;
}

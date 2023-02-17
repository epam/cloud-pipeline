package com.epam.pipeline.entity.billing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BillingDetailsAggregate {

    DEFAULT(null, "cost", "cost"),

    STORAGE_DEFAULT(BillingGrouping.STORAGE, "cost", "usage"),
    STANDARD(BillingGrouping.STORAGE, "standard_total_cost", "standard_total_usage_bytes"),
    GLACIER(BillingGrouping.STORAGE, "glacier_total_cost", "glacier_total_usage_bytes"),
    GLACIER_IR(BillingGrouping.STORAGE, "glacier_ir_total_cost", "glacier_ir_total_usage_bytes"),
    DEEP_ARCHIVE(BillingGrouping.STORAGE, "deep_archive_total_cost", "deep_archive_total_usage_bytes");

    private final BillingGrouping group;
    private final String costAggregate;
    private final String usageAggregate;
}

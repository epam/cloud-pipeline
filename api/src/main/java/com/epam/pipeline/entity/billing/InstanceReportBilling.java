package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder
public class InstanceReportBilling implements ReportBilling<InstanceReportBillingMetrics> {
    String name;
    InstanceReportBillingMetrics totalMetrics;
    Map<YearMonth, InstanceReportBillingMetrics> periodMetrics;

    public InstanceReportBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(InstanceReportBillingMetrics::empty);
    }
}

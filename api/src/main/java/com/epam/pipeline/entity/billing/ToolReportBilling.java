package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class ToolReportBilling implements ReportBilling<ToolReportBillingMetrics> {

    String name;
    String owner;
    ToolReportBillingMetrics totalMetrics;
    Map<YearMonth, ToolReportBillingMetrics> periodMetrics;

    public ToolReportBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(ToolReportBillingMetrics::empty);
    }
}

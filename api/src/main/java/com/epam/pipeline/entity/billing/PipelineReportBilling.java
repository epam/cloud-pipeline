package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class PipelineReportBilling implements ReportBilling<PipelineReportBillingMetrics> {

    Long id;
    String name;
    String owner;
    PipelineReportBillingMetrics totalMetrics;
    Map<YearMonth, PipelineReportBillingMetrics> periodMetrics;

    public PipelineReportBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(PipelineReportBillingMetrics::empty);
    }
}

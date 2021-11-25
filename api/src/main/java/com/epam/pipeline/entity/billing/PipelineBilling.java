package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class PipelineBilling implements PeriodBilling<YearMonth, PipelineBillingMetrics> {

    Long id;
    String name;
    String owner;
    PipelineBillingMetrics totalMetrics;
    Map<YearMonth, PipelineBillingMetrics> periodMetrics;

    @Override
    public PipelineBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(PipelineBillingMetrics::empty);
    }
}

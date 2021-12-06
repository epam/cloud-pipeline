package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class PipelineBilling implements PeriodBilling<PipelineBillingMetrics> {

    Long id;
    String name;
    String owner;
    PipelineBillingMetrics totalMetrics;
    Map<Temporal, PipelineBillingMetrics> periodMetrics;

    @Override
    public PipelineBillingMetrics getPeriodMetrics(final Temporal period) {
        return Optional.ofNullable(periodMetrics.get(period)).orElseGet(PipelineBillingMetrics::empty);
    }
}

package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Optional;

@Value
@Builder
public class InstanceBilling implements PeriodBilling<InstanceBillingMetrics> {

    String name;
    InstanceBillingMetrics totalMetrics;
    Map<Temporal, InstanceBillingMetrics> periodMetrics;

    @Override
    public InstanceBillingMetrics getPeriodMetrics(final Temporal period) {
        return Optional.ofNullable(periodMetrics.get(period)).orElseGet(InstanceBillingMetrics::empty);
    }
}

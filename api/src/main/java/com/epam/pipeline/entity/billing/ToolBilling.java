package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
@Jacksonized
public class ToolBilling implements PeriodBilling<ToolBillingMetrics> {

    String name;
    String owner;
    ToolBillingMetrics totalMetrics;
    Map<Temporal, ToolBillingMetrics> periodMetrics;

    @Override
    public ToolBillingMetrics getPeriodMetrics(final Temporal period) {
        return Optional.ofNullable(periodMetrics.get(period)).orElseGet(ToolBillingMetrics::empty);
    }
}

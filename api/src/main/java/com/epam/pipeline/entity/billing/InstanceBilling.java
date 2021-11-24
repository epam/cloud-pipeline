package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder
public class InstanceBilling implements ReportBilling<InstanceBillingMetrics> {
    String name;
    InstanceBillingMetrics totalMetrics;
    Map<YearMonth, InstanceBillingMetrics> periodMetrics;

    @Override
    public InstanceBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(InstanceBillingMetrics::empty);
    }
}

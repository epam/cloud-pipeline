package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class UserGeneralBilling implements PeriodBilling<GeneralBillingMetrics> {
    String name;
    String billingCenter;
    GeneralBillingMetrics totalMetrics;
    Map<Temporal, GeneralBillingMetrics> periodMetrics;

    @Override
    public GeneralBillingMetrics getPeriodMetrics(final Temporal period) {
        return Optional.ofNullable(periodMetrics.get(period)).orElseGet(GeneralBillingMetrics::empty);
    }
}

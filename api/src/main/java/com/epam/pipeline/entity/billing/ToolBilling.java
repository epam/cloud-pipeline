package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class ToolBilling implements PeriodBilling<YearMonth, ToolBillingMetrics> {

    String name;
    String owner;
    ToolBillingMetrics totalMetrics;
    Map<YearMonth, ToolBillingMetrics> periodMetrics;

    @Override
    public ToolBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(ToolBillingMetrics::empty);
    }
}

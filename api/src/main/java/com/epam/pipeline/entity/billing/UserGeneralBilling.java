package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class UserGeneralBilling implements ReportBilling<GeneralBillingMetrics> {
    String name;
    String billingCenter;
    GeneralBillingMetrics totalMetrics;
    Map<YearMonth, GeneralBillingMetrics> periodMetrics;

    @Override
    public GeneralBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(GeneralBillingMetrics::empty);
    }
}

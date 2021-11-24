package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder
public class BillingCenterGeneralBilling implements ReportBilling<GeneralBillingMetrics> {

    String name;
    GeneralBillingMetrics totalMetrics;
    Map<YearMonth, GeneralBillingMetrics> periodMetrics;

    public GeneralBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(GeneralBillingMetrics::empty);
    }
}

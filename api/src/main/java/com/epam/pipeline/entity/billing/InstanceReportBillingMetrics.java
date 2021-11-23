package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;

@Value
@Builder
public class InstanceReportBillingMetrics {
    Long runsNumber;
    Long runsDuration;
    Long runsCost;

    public static InstanceReportBillingMetrics empty() {
        return InstanceReportBillingMetrics.builder()
                .runsNumber(0L)
                .runsDuration(0L)
                .runsCost(0L)
                .build();
    }
}

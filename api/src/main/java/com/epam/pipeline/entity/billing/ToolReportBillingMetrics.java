package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ToolReportBillingMetrics {
    Long runsNumber;
    Long runsDuration;
    Long runsCost;

    public static ToolReportBillingMetrics empty() {
        return ToolReportBillingMetrics.builder()
                .runsNumber(0L)
                .runsDuration(0L)
                .runsCost(0L)
                .build();
    }
}

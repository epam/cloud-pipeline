package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ToolBillingMetrics {
    Long runsNumber;
    Long runsDuration;
    Long runsCost;

    public static ToolBillingMetrics empty() {
        return ToolBillingMetrics.builder()
                .runsNumber(0L)
                .runsDuration(0L)
                .runsCost(0L)
                .build();
    }
}

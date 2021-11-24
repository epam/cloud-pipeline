package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PipelineBillingMetrics {
    Long runsNumber;
    Long runsDuration;
    Long runsCost;

    public static PipelineBillingMetrics empty() {
        return PipelineBillingMetrics.builder()
                .runsNumber(0L)
                .runsDuration(0L)
                .runsCost(0L)
                .build();
    }
}

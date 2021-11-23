package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PipelineReportBillingMetrics {
    Long runsNumber;
    Long runsDuration;
    Long runsCost;

    public static PipelineReportBillingMetrics empty() {
        return PipelineReportBillingMetrics.builder()
                .runsNumber(0L)
                .runsDuration(0L)
                .runsCost(0L)
                .build();
    }
}

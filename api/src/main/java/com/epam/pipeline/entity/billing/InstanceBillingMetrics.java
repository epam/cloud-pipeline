package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstanceBillingMetrics {

    Long runsNumber;
    Long runsDuration;
    Long runsCost;

    public static InstanceBillingMetrics empty() {
        return InstanceBillingMetrics.builder()
                .runsNumber(0L)
                .runsDuration(0L)
                .runsCost(0L)
                .build();
    }
}

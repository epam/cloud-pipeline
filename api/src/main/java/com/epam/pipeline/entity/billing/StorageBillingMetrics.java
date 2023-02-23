package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StorageBillingMetrics {

    Long cost;
    Long averageVolume;
    Long currentVolume;
    BillingChartDetails details;

    public static StorageBillingMetrics empty() {
        return StorageBillingMetrics.builder()
                .cost(0L)
                .averageVolume(0L)
                .currentVolume(0L)
                .build();
    }
}

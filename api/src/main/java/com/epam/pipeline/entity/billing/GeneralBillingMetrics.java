package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class GeneralBillingMetrics {

    Long runsNumber;
    Long runsDuration;
    Long runsCost;
    Long storagesCost;

    public static GeneralBillingMetrics empty() {
        return GeneralBillingMetrics.builder()
                .runsNumber(0L)
                .runsDuration(0L)
                .runsCost(0L)
                .storagesCost(0L)
                .build();
    }
}

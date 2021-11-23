package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;

@Value
@Builder
public class StorageReportYearMonthBilling {
    YearMonth yearMonth;
    Long cost;
    Long averageVolume;
    Long currentVolume;

    public static StorageReportYearMonthBilling empty(final YearMonth ym) {
        return StorageReportYearMonthBilling.builder()
                .yearMonth(ym)
                .cost(0L)
                .averageVolume(0L)
                .currentVolume(0L)
                .build();
    }
}

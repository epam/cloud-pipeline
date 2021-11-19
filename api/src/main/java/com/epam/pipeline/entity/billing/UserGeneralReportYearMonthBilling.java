package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;

@Value
@Builder
public class UserGeneralReportYearMonthBilling {
    YearMonth yearMonth;
    Long runsNumber;
    Long runsDuration;
    Long runsCost;
    Long storagesCost;

    public static UserGeneralReportYearMonthBilling empty(final YearMonth ym) {
        return UserGeneralReportYearMonthBilling.builder()
                .yearMonth(ym)
                .runsNumber(0L)
                .runsDuration(0L)
                .runsCost(0L)
                .storagesCost(0L)
                .build();
    }
}

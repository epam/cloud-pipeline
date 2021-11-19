package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class UserGeneralReportBilling {
    String user;
    String billingCenter;
    Long runsNumber;
    Long runsDuration;
    Long runsCost;
    Long storagesCost;
    Map<YearMonth, UserGeneralReportYearMonthBilling> billings;

    public Optional<UserGeneralReportYearMonthBilling> getBilling(final YearMonth ym) {
        return Optional.ofNullable(billings.get(ym));
    }
}

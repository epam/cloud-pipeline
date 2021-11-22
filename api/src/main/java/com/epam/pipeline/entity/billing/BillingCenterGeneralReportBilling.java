package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder
public class BillingCenterGeneralReportBilling {

    String name;
    Long runsNumber;
    Long runsDuration;
    Long runsCost;
    Long storagesCost;
    Map<YearMonth, GeneralReportYearMonthBilling> billings;

    public Optional<GeneralReportYearMonthBilling> getBilling(final YearMonth ym) {
        return Optional.ofNullable(billings.get(ym));
    }
}

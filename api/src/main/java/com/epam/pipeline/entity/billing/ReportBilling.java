package com.epam.pipeline.entity.billing;

import java.time.YearMonth;

public interface ReportBilling<M> {

    M getTotalMetrics();
    M getPeriodMetrics(YearMonth yearMonth);
}

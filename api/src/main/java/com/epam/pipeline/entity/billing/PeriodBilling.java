package com.epam.pipeline.entity.billing;

import java.time.temporal.Temporal;

public interface PeriodBilling<P extends Temporal, M> {

    M getTotalMetrics();
    M getPeriodMetrics(P yearMonth);
}

package com.epam.pipeline.entity.billing;

import java.time.temporal.Temporal;

public interface PeriodBilling<M> {

    M getTotalMetrics();
    M getPeriodMetrics(Temporal period);
}

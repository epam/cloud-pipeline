package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.InstanceBilling;
import com.epam.pipeline.entity.billing.InstanceBillingMetrics;
import com.epam.pipeline.utils.CommonUtils;

import java.io.Writer;
import java.util.Collections;
import java.util.stream.Stream;

public class InstanceBillingWriterTest extends AbstractBillingWriterTest<InstanceBilling> {

    public InstanceBillingWriterTest() {
        super("/billings/billing.instance.csv");
    }

    @Override
    public BillingWriter<InstanceBilling> getWriter(final Writer writer) {
        return new InstanceBillingWriter(writer, START_DATE, FINISH_DATE);
    }

    @Override
    public Stream<InstanceBilling> billings() {
        return Stream.of(
                InstanceBilling.builder()
                        .name(INSTANCE_TYPE)
                        .periodMetrics(CommonUtils.mergeMaps(
                                Collections.singletonMap(START_YEAR_MONTH,
                                        InstanceBillingMetrics.builder()
                                                .runsNumber(ONE)
                                                .runsDuration(ONE_HOUR)
                                                .runsCost(ONE_DOLLAR)
                                                .build()),
                                Collections.singletonMap(FINISH_YEAR_MONTH,
                                        InstanceBillingMetrics.builder()
                                                .runsNumber(TEN)
                                                .runsDuration(TEN_HOURS)
                                                .runsCost(TEN_DOLLARS)
                                                .build())))
                        .totalMetrics(InstanceBillingMetrics.builder()
                                .runsNumber(ONE + TEN)
                                .runsDuration(ONE_HOUR + TEN_HOURS)
                                .runsCost(ONE_DOLLAR + TEN_DOLLARS)
                                .build())
                        .build());
    }
}

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.PipelineBilling;
import com.epam.pipeline.entity.billing.PipelineBillingMetrics;
import com.epam.pipeline.utils.CommonUtils;

import java.io.Writer;
import java.util.Collections;
import java.util.stream.Stream;

public class PipelineBillingWriterTest extends AbstractBillingWriterTest<PipelineBilling> {

    public PipelineBillingWriterTest() {
        super("/billings/billing.pipeline.csv");
    }

    @Override
    public BillingWriter<PipelineBilling> getWriter(final Writer writer) {
        return new PipelineBillingWriter(writer, START_DATE, FINISH_DATE);
    }

    @Override
    public Stream<PipelineBilling> billings() {
        return Stream.of(
                PipelineBilling.builder()
                        .name(PIPELINE)
                        .owner(OWNER)
                        .periodMetrics(CommonUtils.mergeMaps(
                                Collections.singletonMap(START_YEAR_MONTH,
                                        PipelineBillingMetrics.builder()
                                                .runsNumber(ONE)
                                                .runsDuration(ONE_HOUR)
                                                .runsCost(ONE_DOLLAR)
                                                .build()),
                                Collections.singletonMap(FINISH_YEAR_MONTH,
                                        PipelineBillingMetrics.builder()
                                                .runsNumber(TEN)
                                                .runsDuration(TEN_HOURS)
                                                .runsCost(TEN_DOLLARS)
                                                .build())))
                        .totalMetrics(PipelineBillingMetrics.builder()
                                .runsNumber(ONE + TEN)
                                .runsDuration(ONE_HOUR + TEN_HOURS)
                                .runsCost(ONE_DOLLAR + TEN_DOLLARS)
                                .build())
                        .build());
    }
}

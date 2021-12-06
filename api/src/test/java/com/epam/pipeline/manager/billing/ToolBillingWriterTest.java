package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.ToolBilling;
import com.epam.pipeline.entity.billing.ToolBillingMetrics;
import com.epam.pipeline.utils.CommonUtils;

import java.io.Writer;
import java.util.Collections;
import java.util.stream.Stream;

public class ToolBillingWriterTest extends AbstractBillingWriterTest<ToolBilling> {

    public ToolBillingWriterTest() {
        super("/billings/billing.tool.csv");
    }

    @Override
    public BillingWriter<ToolBilling> getWriter(final Writer writer) {
        return new ToolBillingWriter(writer, START_DATE, FINISH_DATE);
    }

    @Override
    public Stream<ToolBilling> billings() {
        return Stream.of(
                ToolBilling.builder()
                        .name(TOOL)
                        .owner(OWNER)
                        .periodMetrics(CommonUtils.mergeMaps(
                                Collections.singletonMap(START_YEAR_MONTH,
                                        ToolBillingMetrics.builder()
                                                .runsNumber(ONE)
                                                .runsDuration(ONE_HOUR)
                                                .runsCost(ONE_DOLLAR)
                                                .build()),
                                Collections.singletonMap(FINISH_YEAR_MONTH,
                                        ToolBillingMetrics.builder()
                                                .runsNumber(TEN)
                                                .runsDuration(TEN_HOURS)
                                                .runsCost(TEN_DOLLARS)
                                                .build())))
                        .totalMetrics(ToolBillingMetrics.builder()
                                .runsNumber(ONE + TEN)
                                .runsDuration(ONE_HOUR + TEN_HOURS)
                                .runsCost(ONE_DOLLAR + TEN_DOLLARS)
                                .build())
                        .build());
    }
}

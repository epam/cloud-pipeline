package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.RunBilling;

import java.io.Writer;
import java.util.stream.Stream;

public class RunBillingWriterTest extends AbstractBillingWriterTest<RunBilling> {

    public RunBillingWriterTest() {
        super("/billings/billing.run.csv");
    }

    @Override
    public BillingWriter<RunBilling> getWriter(final Writer writer) {
        return new RunBillingWriter(writer);
    }

    @Override
    public Stream<RunBilling> billings() {
        return Stream.of(
                RunBilling.builder()
                        .runId(ID)
                        .owner(OWNER)
                        .tool(TOOL)
                        .instanceType(INSTANCE_TYPE)
                        .started(START_DATE_TIME)
                        .duration(ONE_HOUR)
                        .cost(ONE_DOLLAR)
                        .build(),
                RunBilling.builder()
                        .runId(ID_2)
                        .owner(OWNER)
                        .pipeline(PIPELINE)
                        .tool(TOOL)
                        .instanceType(INSTANCE_TYPE)
                        .started(START_DATE_TIME)
                        .finished(FINISH_DATE_TIME)
                        .duration(TEN_HOURS)
                        .cost(TEN_DOLLARS)
                        .build());
    }
}

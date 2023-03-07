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
                        .billingCenter(BILLING_CENTER)
                        .tool(TOOL)
                        .computeType(COMPUTE_TYPE)
                        .instanceType(INSTANCE_TYPE)
                        .started(START_DATE_TIME)
                        .duration(ONE_HOUR)
                        .cost(ONE_DOLLAR)
                        .diskCost(ONE_DOLLAR)
                        .computeCost(ONE_DOLLAR)
                        .build(),
                RunBilling.builder()
                        .runId(ID_2)
                        .owner(OWNER)
                        .billingCenter(BILLING_CENTER)
                        .pipeline(PIPELINE)
                        .tool(TOOL)
                        .computeType(COMPUTE_TYPE)
                        .instanceType(INSTANCE_TYPE)
                        .started(START_DATE_TIME)
                        .finished(FINISH_DATE_TIME)
                        .duration(TEN_HOURS)
                        .cost(TEN_DOLLARS)
                        .diskCost(TEN_DOLLARS)
                        .computeCost(TEN_DOLLARS)
                        .build());
    }
}

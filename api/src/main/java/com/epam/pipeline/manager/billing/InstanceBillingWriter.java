package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.InstanceBilling;
import com.epam.pipeline.entity.billing.InstanceBillingMetrics;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

@RequiredArgsConstructor
public class InstanceBillingWriter implements BillingWriter<InstanceBilling> {

    private static final String TABLE_NAME = "Instances";
    private static final String INSTANCE_COLUMN = "Instance";
    private static final String RUNS_COUNT_COLUMN = "Runs (count)";
    private static final String RUNS_DURATION_COLUMN = "Duration (hours)";
    private static final String RUNS_COST_COLUMN = "Cost ($)";

    private final PeriodBillingWriter<InstanceBilling, InstanceBillingMetrics> writer;

    public InstanceBillingWriter(final Writer writer,
                                 final LocalDate from,
                                 final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Collections.singletonList(INSTANCE_COLUMN),
                Arrays.asList(RUNS_COUNT_COLUMN, RUNS_DURATION_COLUMN, RUNS_COST_COLUMN),
                Collections.singletonList(InstanceBilling::getName),
                Arrays.asList(
                        metrics -> BillingUtils.asString(metrics.getRunsNumber()),
                        metrics -> BillingUtils.asDurationString(metrics.getRunsDuration()),
                        metrics -> BillingUtils.asCostString(metrics.getRunsCost())));
    }

    @Override
    public void writeHeader() {
        writer.writeHeader();
    }

    @Override
    public void write(final InstanceBilling billing) {
        writer.write(billing);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

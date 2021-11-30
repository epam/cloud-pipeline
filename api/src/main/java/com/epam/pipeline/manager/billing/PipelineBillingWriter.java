package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.PipelineBilling;
import com.epam.pipeline.entity.billing.PipelineBillingMetrics;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;

public class PipelineBillingWriter implements BillingWriter<PipelineBilling> {

    private static final String TABLE_NAME = "Pipelines";

    private final PeriodBillingWriter<PipelineBilling, PipelineBillingMetrics> writer;

    public PipelineBillingWriter(final Writer writer,
                                 final LocalDate from,
                                 final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Arrays.asList(
                    BillingUtils.PIPELINE_COLUMN,
                    BillingUtils.OWNER_COLUMN),
                Arrays.asList(
                    BillingUtils.RUNS_COUNT_COLUMN,
                    BillingUtils.DURATION_COLUMN,
                    BillingUtils.COST_COLUMN),
                Arrays.asList(
                    PipelineBilling::getName,
                    PipelineBilling::getOwner),
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
    public void write(final PipelineBilling billing) {
        writer.write(billing);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

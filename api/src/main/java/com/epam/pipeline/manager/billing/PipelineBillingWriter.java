package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.PipelineBilling;
import com.epam.pipeline.entity.billing.PipelineBillingMetrics;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;

@RequiredArgsConstructor
public class PipelineBillingWriter implements BillingWriter<PipelineBilling> {

    private static final String TABLE_NAME = "Pipelines";
    private static final String PIPELINE_COLUMN = "Pipeline";
    private static final String OWNER_COLUMN = "Owner";
    private static final String RUNS_COUNT_COLUMN = "Runs (count)";
    private static final String RUNS_DURATION_COLUMN = "Duration (hours)";
    private static final String RUNS_COST_COLUMN = "Cost ($)";

    private final PeriodBillingWriter<PipelineBilling, PipelineBillingMetrics> writer;

    public PipelineBillingWriter(final Writer writer,
                                 final LocalDate from,
                                 final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Arrays.asList(PIPELINE_COLUMN, OWNER_COLUMN),
                Arrays.asList(RUNS_COUNT_COLUMN, RUNS_DURATION_COLUMN, RUNS_COST_COLUMN),
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

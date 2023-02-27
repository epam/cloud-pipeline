package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.RunBilling;
import com.opencsv.CSVWriter;

import java.io.IOException;
import java.io.Writer;

public class RunBillingWriter implements BillingWriter<RunBilling> {

    private final CSVWriter writer;

    public RunBillingWriter(final Writer writer) {
        this.writer = new CSVWriter(writer, BillingUtils.SEPARATOR);
    }

    @Override
    public void writeHeader() {
        writer.writeNext(new String[]{
            BillingUtils.RUN_COLUMN,
            BillingUtils.OWNER_COLUMN,
            BillingUtils.BILLING_CENTER_COLUMN,
            BillingUtils.PIPELINE_COLUMN,
            BillingUtils.TOOL_COLUMN,
            BillingUtils.TYPE_COLUMN,
            BillingUtils.INSTANCE_COLUMN,
            BillingUtils.STARTED_COLUMN,
            BillingUtils.FINISHED_COLUMN,
            BillingUtils.DURATION_COLUMN,
            BillingUtils.COST_COLUMN,
            BillingUtils.DISK_COST_COLUMN,
            BillingUtils.COMPUTE_COST_COLUMN
        });
    }

    @Override
    public void write(final RunBilling billing) {
        writer.writeNext(new String[]{
            BillingUtils.asString(billing.getRunId()),
            billing.getOwner(),
            billing.getBillingCenter(),
            billing.getPipeline(),
            billing.getTool(),
            billing.getComputeType(),
            billing.getInstanceType(),
            BillingUtils.asString(billing.getStarted()),
            BillingUtils.asString(billing.getFinished()),
            BillingUtils.asDurationString(billing.getDuration()),
            BillingUtils.asCostString(billing.getCost()),
            BillingUtils.asCostString(billing.getDiskCost()),
            BillingUtils.asCostString(billing.getComputeCost())
        });
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

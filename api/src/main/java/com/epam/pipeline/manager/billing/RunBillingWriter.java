package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.RunBilling;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;

import java.io.Closeable;
import java.io.IOException;

@RequiredArgsConstructor
public class RunBillingWriter implements Closeable {

    private static final String[] RUN_RAW_DATA_HEADER = new String[]{
            "Run", "Owner", "Pipeline", "Tool", "Instance", "Started", "Finished", "Duration", "Cost"};

    private final CSVWriter writer;
    private final BillingHelper billingHelper;

    public void writeHeader() {
        writer.writeNext(RUN_RAW_DATA_HEADER, false);
    }

    public void write(final RunBilling info) {
        writer.writeNext(new String[]{
                billingHelper.asString(info.getRunId()),
                info.getOwner(),
                info.getPipeline(),
                info.getTool(),
                info.getInstanceType(),
                billingHelper.asString(info.getStarted()),
                billingHelper.asString(info.getFinished()),
                billingHelper.asString(info.getDuration()),
                billingHelper.asString(info.getCost())});
    }

    @Override
    public void close() throws IOException {
    }
}

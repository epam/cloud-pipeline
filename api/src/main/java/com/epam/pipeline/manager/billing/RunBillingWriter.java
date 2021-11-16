package com.epam.pipeline.manager.billing;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.billing.RunBilling;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;

import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
public class RunBillingWriter implements Closeable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(Constants.FMT_ISO_LOCAL_DATE);
    private static final String[] RUN_RAW_DATA_HEADER = new String[]{
            "Run", "Owner", "Pipeline", "Tool", "Instance", "Started", "Finished", "Duration", "Cost"};

    private final CSVWriter writer;

    public void writeHeader() {
        writer.writeNext(RUN_RAW_DATA_HEADER, false);
    }

    public void write(final RunBilling info) {
        writer.writeNext(new String[]{
                asString(info.getRunId()),
                info.getOwner(),
                info.getPipeline(),
                info.getTool(),
                info.getInstanceType(),
                asString(info.getStarted()),
                asString(info.getFinished()),
                asString(info.getDuration()),
                asString(info.getCost())});
    }

    private String asString(final Long value) {
        return value != null ? value.toString() : null;
    }

    private String asString(final LocalDateTime value) {
        return value != null ? DATE_FORMATTER.format(value) : null;
    }

    @Override
    public void close() throws IOException {
    }
}

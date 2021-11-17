package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.RunBilling;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Optional;

@RequiredArgsConstructor
public class RunBillingWriter implements Closeable {

    private static final char SEPARATOR = ',';
    private static final String[] FALLBACK_HEADER = new String[]{
        "Run", "Owner", "Pipeline", "Tool", "Instance", "Started", "Finished", "Duration", "Cost"};

    private final CSVWriter writer;
    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;

    public RunBillingWriter(final Writer writer,
                            final BillingHelper billingHelper,
                            final PreferenceManager preferenceManager) {
        this.writer = new CSVWriter(writer, SEPARATOR);
        this.billingHelper = billingHelper;
        this.preferenceManager = preferenceManager;
    }

    public void writeHeader() {
        writer.writeNext(getHeader());
    }

    private String[] getHeader() {
        return Optional.of(SystemPreferences.BILLING_EXPORT_RUN_HEADER)
                .map(preferenceManager::getPreference)
                .filter(StringUtils::isNotBlank)
                .map(it -> StringUtils.split(it, SEPARATOR))
                .orElse(FALLBACK_HEADER);
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
        writer.close();
    }
}

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.RunBilling;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.Optional;

@RequiredArgsConstructor
public class RunBillingWriter implements BillingWriter<RunBilling> {

    private static final char SEPARATOR = ',';
    private static final String[] FALLBACK_HEADER = new String[]{
        "Run", "Owner", "Billing Center", "Pipeline", "Tool", "Type", "Instance", "Started", "Finished",
        "Duration (hours)", "Cost ($)"};

    private final CSVWriter writer;
    private final PreferenceManager preferenceManager;

    public RunBillingWriter(final Writer writer,
                            final PreferenceManager preferenceManager) {
        this.writer = new CSVWriter(writer, SEPARATOR);
        this.preferenceManager = preferenceManager;
    }

    @Override
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
                BillingUtils.asCostString(billing.getCost())});
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

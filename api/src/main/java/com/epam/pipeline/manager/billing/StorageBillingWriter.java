package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.StorageBilling;
import com.epam.pipeline.entity.billing.StorageBillingMetrics;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;

public class StorageBillingWriter implements BillingWriter<StorageBilling> {

    private static final String BILLING_CENTER_COLUMN = "Billing center";
    private static final String STORAGE_COLUMN = "Storage";
    private static final String OWNER_COLUMN = "Owner";
    private static final String TYPE_COLUMN = "Type";
    private static final String REGION_COLUMN = "Region";
    private static final String PROVIDER_COLUMN = "Provider";
    private static final String CREATED_COLUMN = "Created";
    private static final String COST_COLUMN = "Cost ($)";
    private static final String AVERAGE_VOLUME_COLUMN = "Average Volume (GB)";
    private static final String CURRENT_VOLUME_COLUMN = "Current Volume (GB)";
    private static final String TABLE_NAME = "Storages";

    private final PeriodBillingWriter<StorageBilling, StorageBillingMetrics> writer;

    public StorageBillingWriter(final Writer writer,
                                final LocalDate from,
                                final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Arrays.asList(STORAGE_COLUMN, OWNER_COLUMN, BILLING_CENTER_COLUMN, TYPE_COLUMN,
                        REGION_COLUMN, PROVIDER_COLUMN, CREATED_COLUMN),
                Arrays.asList(COST_COLUMN, AVERAGE_VOLUME_COLUMN, CURRENT_VOLUME_COLUMN),
                Arrays.asList(
                        StorageBilling::getName,
                        StorageBilling::getOwner,
                        StorageBilling::getBillingCenter,
                        b -> BillingUtils.asString(b.getType()),
                        StorageBilling::getRegion,
                        StorageBilling::getProvider,
                        b -> BillingUtils.asString(b.getCreated())),
                Arrays.asList(
                        metrics -> BillingUtils.asCostString(metrics.getCost()),
                        metrics -> BillingUtils.asVolumeString(metrics.getAverageVolume()),
                        metrics -> BillingUtils.asVolumeString(metrics.getCurrentVolume())));
    }

    @Override
    public void writeHeader() {
        writer.writeHeader();
    }

    @Override
    public void write(final StorageBilling billing) {
        writer.write(billing);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

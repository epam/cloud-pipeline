package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.StorageBilling;
import com.epam.pipeline.entity.billing.StorageBillingMetrics;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;

public class StorageBillingWriter implements BillingWriter<StorageBilling> {

    private static final String TABLE_NAME = "Storages";

    private final PeriodBillingWriter<StorageBilling, StorageBillingMetrics> writer;

    public StorageBillingWriter(final Writer writer,
                                final LocalDate from,
                                final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Arrays.asList(
                        BillingUtils.STORAGE_COLUMN,
                        BillingUtils.OWNER_COLUMN,
                        BillingUtils.BILLING_CENTER_COLUMN,
                        BillingUtils.TYPE_COLUMN,
                        BillingUtils.REGION_COLUMN,
                        BillingUtils.PROVIDER_COLUMN,
                        BillingUtils.CREATED_COLUMN),
                Arrays.asList(
                        BillingUtils.COST_COLUMN,
                        BillingUtils.AVERAGE_VOLUME_COLUMN,
                        BillingUtils.CURRENT_VOLUME_COLUMN),
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

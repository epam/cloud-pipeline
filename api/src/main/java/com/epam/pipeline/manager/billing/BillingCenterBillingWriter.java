package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.BillingCenterGeneralBilling;
import com.epam.pipeline.entity.billing.GeneralBillingMetrics;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

public class BillingCenterBillingWriter implements BillingWriter<BillingCenterGeneralBilling> {

    private static final String TABLE_NAME = "Billing centers";

    private final PeriodBillingWriter<BillingCenterGeneralBilling, GeneralBillingMetrics> writer;

    public BillingCenterBillingWriter(final Writer writer,
                                      final LocalDate from,
                                      final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Collections.singletonList(BillingUtils.BILLING_CENTER_COLUMN),
                Arrays.asList(
                        BillingUtils.RUNS_COUNT_COLUMN,
                        BillingUtils.RUNS_DURATIONS_COLUMN,
                        BillingUtils.RUNS_COSTS_COLUMN,
                        BillingUtils.STORAGES_COSTS_COLUMN),
                Collections.singletonList(BillingCenterGeneralBilling::getName),
                Arrays.asList(
                        metrics -> BillingUtils.asString(metrics.getRunsNumber()),
                        metrics -> BillingUtils.asDurationString(metrics.getRunsDuration()),
                        metrics -> BillingUtils.asCostString(metrics.getRunsCost()),
                        metrics -> BillingUtils.asCostString(metrics.getStoragesCost())));
    }

    @Override
    public void writeHeader() {
        writer.writeHeader();
    }

    @Override
    public void write(final BillingCenterGeneralBilling billing) {
        writer.write(billing);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

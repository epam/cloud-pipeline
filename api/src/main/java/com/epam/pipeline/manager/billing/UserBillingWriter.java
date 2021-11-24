package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.GeneralBillingMetrics;
import com.epam.pipeline.entity.billing.UserGeneralBilling;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;

public class UserBillingWriter implements BillingWriter<UserGeneralBilling> {

    private static final String TABLE_NAME = "Users";

    private final PeriodBillingWriter<UserGeneralBilling, GeneralBillingMetrics> writer;

    public UserBillingWriter(final Writer writer,
                             final LocalDate from,
                             final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Arrays.asList(
                        BillingUtils.USER_COLUMN,
                        BillingUtils.BILLING_CENTER_COLUMN),
                Arrays.asList(
                        BillingUtils.RUNS_COUNT_COLUMN,
                        BillingUtils.RUNS_DURATIONS_COLUMN,
                        BillingUtils.RUNS_COSTS_COLUMN,
                        BillingUtils.STORAGES_COSTS_COLUMN),
                Arrays.asList(
                        UserGeneralBilling::getName,
                        UserGeneralBilling::getBillingCenter),
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
    public void write(final UserGeneralBilling billing) {
        writer.write(billing);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

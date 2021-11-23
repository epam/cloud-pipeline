package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.StorageReportBilling;
import com.epam.pipeline.entity.billing.StorageReportYearMonthBilling;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public class StorageReportWriter implements BillingWriter {

    private static final char SEPARATOR = ',';
    private static final int NUMERIC_SCALE = 2;
    private static final long COST_DIVISOR = BigDecimal.ONE.setScale(4, RoundingMode.CEILING)
            .unscaledValue()
            .longValue();
    private static final long VOLUME_DIVISOR = BigDecimal.ONE.setScale(9, RoundingMode.CEILING)
            .unscaledValue()
            .longValue();
    private static final String BILLING_CENTER_COLUMN = "Billing center";
    private static final String YEAR_MONTH_FORMAT = "MMMM yyyy";
    private static final String STORAGE_COLUMN = "Storage";
    private static final String OWNER_COLUMN = "Owner";
    private static final String TYPE_COLUMN = "Type";
    private static final String REGION_COLUMN = "Region";
    private static final String PROVIDER_COLUMN = "Provider";
    private static final String CREATED_COLUMN = "Created";
    private static final String COST_COLUMN = "Cost ($)";
    private static final String AVG_VOLUME_COLUMN = "Average Volume (GB)";
    private static final String CUR_VOLUME_COLUMN = "Current Volume (GB)";

    private final CSVWriter writer;
    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;
    private final LocalDate from;
    private final LocalDate to;

    public StorageReportWriter(final Writer writer,
                               final BillingHelper billingHelper,
                               final PreferenceManager preferenceManager,
                               final LocalDate from,
                               final LocalDate to) {
        this.writer = new CSVWriter(writer, SEPARATOR);
        this.billingHelper = billingHelper;
        this.preferenceManager = preferenceManager;
        this.from = from;
        this.to = to;
    }

    public void writeHeader() {
        final List<String> datesRow = new ArrayList<>();
        datesRow.add("Storages");
        datesRow.add(StringUtils.EMPTY);
        datesRow.add(StringUtils.EMPTY);
        datesRow.add(StringUtils.EMPTY);
        datesRow.add(StringUtils.EMPTY);
        datesRow.add(StringUtils.EMPTY);
        datesRow.add(StringUtils.EMPTY);
        yms().forEach(ym -> {
            datesRow.add(DateTimeFormatter.ofPattern(YEAR_MONTH_FORMAT, Locale.US).format(ym));
            datesRow.add(StringUtils.EMPTY);
            datesRow.add(StringUtils.EMPTY);
        });
        datesRow.add(from + " - " + to);
        datesRow.add(StringUtils.EMPTY);
        datesRow.add(StringUtils.EMPTY);
        writer.writeNext(datesRow.toArray(new String[0]));
        final List<String> columnsRow = new ArrayList<>();
        columnsRow.add(STORAGE_COLUMN);
        columnsRow.add(OWNER_COLUMN);
        columnsRow.add(BILLING_CENTER_COLUMN);
        columnsRow.add(TYPE_COLUMN);
        columnsRow.add(REGION_COLUMN);
        columnsRow.add(PROVIDER_COLUMN);
        columnsRow.add(CREATED_COLUMN);
        yms().forEach(ym -> {
            columnsRow.add(COST_COLUMN);
            columnsRow.add(AVG_VOLUME_COLUMN);
            columnsRow.add(CUR_VOLUME_COLUMN);
        });
        columnsRow.add(COST_COLUMN);
        columnsRow.add(AVG_VOLUME_COLUMN);
        columnsRow.add(CUR_VOLUME_COLUMN);
        writer.writeNext(columnsRow.toArray(new String[0]));
    }

    public void write(final StorageReportBilling billing) {
        final List<String> row = new ArrayList<>();
        row.add(billing.getName());
        row.add(billing.getOwner());
        row.add(billing.getBillingCenter());
        row.add(billingHelper.asString(billing.getType()));
        row.add(billing.getRegion());
        row.add(billing.getProvider());
        row.add(billingHelper.asString(billing.getCreated()));
        yms().forEach(ym -> {
            final StorageReportYearMonthBilling ymBilling = billing.getBilling(ym)
                    .orElseGet(() -> StorageReportYearMonthBilling.empty(ym));
            row.add(divided(ymBilling.getCost(), COST_DIVISOR).toString());
            row.add(divided(ymBilling.getAverageVolume(), VOLUME_DIVISOR).toString());
            row.add(divided(ymBilling.getCurrentVolume(), VOLUME_DIVISOR).toString());
        });
        row.add(divided(billing.getCost(), COST_DIVISOR).toString());
        row.add(divided(billing.getAverageVolume(), VOLUME_DIVISOR).toString());
        row.add(divided(billing.getCurrentVolume(), VOLUME_DIVISOR).toString());
        writer.writeNext(row.toArray(new String[0]));
    }

    private Stream<YearMonth> yms() {
        return Stream.iterate(YearMonth.from(from), ym -> ym.plusMonths(1L))
                .limit(ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1L);
    }

    private BigDecimal divided(final Long divider, final Long divisor) {
        return Optional.ofNullable(divider)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(divisor), NUMERIC_SCALE, RoundingMode.CEILING);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

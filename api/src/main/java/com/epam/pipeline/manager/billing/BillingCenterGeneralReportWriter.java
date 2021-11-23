package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.BillingCenterGeneralReportBilling;
import com.epam.pipeline.entity.billing.GeneralReportYearMonthBilling;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class BillingCenterGeneralReportWriter implements BillingWriter {

    private static final char SEPARATOR = ',';
    private static final int NUMERIC_SCALE = 2;
    private static final long DURATION_DIVISOR = TimeUnit.MINUTES.convert(NumberUtils.LONG_ONE, TimeUnit.HOURS);
    private static final long COST_DIVISOR = BigDecimal.ONE.setScale(4, RoundingMode.CEILING)
            .unscaledValue()
            .longValue();
    private static final String RUNS_COUNT_COLUMN = "Runs (count)";
    private static final String RUNS_DURATIONS_COLUMN = "Runs durations (hours)";
    private static final String RUNS_COSTS_COLUMN = "Runs costs ($)";
    private static final String STORAGES_COSTS_COLUMN = "Storage costs ($)";
    private static final String YEAR_MONTH_FORMAT = "MMMM yyyy";

    private final CSVWriter writer;
    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;
    private final LocalDate from;
    private final LocalDate to;

    public BillingCenterGeneralReportWriter(final Writer writer,
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
        writer.writeNext(new String[] {});
        writer.writeNext(new String[]{"Compute discounts:", "-"});
        writer.writeNext(new String[]{"Storage discounts:", "-"});
        final List<String> datesRow = new ArrayList<>();
        datesRow.add(StringUtils.EMPTY);
        yms().forEach(ym -> {
            datesRow.add(DateTimeFormatter.ofPattern(YEAR_MONTH_FORMAT, Locale.US).format(ym));
            datesRow.add(StringUtils.EMPTY);
            datesRow.add(StringUtils.EMPTY);
            datesRow.add(StringUtils.EMPTY);
        });
        datesRow.add(from + " - " + to);
        datesRow.add(StringUtils.EMPTY);
        datesRow.add(StringUtils.EMPTY);
        datesRow.add(StringUtils.EMPTY);
        writer.writeNext(datesRow.toArray(new String[0]));
        final List<String> columnsRow = new ArrayList<>();
        columnsRow.add(StringUtils.EMPTY);
        yms().forEach(ym -> {
            columnsRow.add(RUNS_COUNT_COLUMN);
            columnsRow.add(RUNS_DURATIONS_COLUMN);
            columnsRow.add(RUNS_COSTS_COLUMN);
            columnsRow.add(STORAGES_COSTS_COLUMN);
        });
        columnsRow.add(RUNS_COUNT_COLUMN);
        columnsRow.add(RUNS_DURATIONS_COLUMN);
        columnsRow.add(RUNS_COSTS_COLUMN);
        columnsRow.add(STORAGES_COSTS_COLUMN);
        writer.writeNext(columnsRow.toArray(new String[0]));
    }

    public void write(final BillingCenterGeneralReportBilling billing) {
        final List<String> row = new ArrayList<>();
        row.add(billing.getName());
        yms().forEach(ym -> {
            final GeneralReportYearMonthBilling ymBilling = billing.getBilling(ym)
                    .orElseGet(() -> GeneralReportYearMonthBilling.empty(ym));
            row.add(billingHelper.asString(ymBilling.getRunsNumber()));
            row.add(divided(ymBilling.getRunsDuration(), DURATION_DIVISOR).toString());
            row.add(divided(ymBilling.getRunsCost(), COST_DIVISOR).toString());
            row.add(divided(ymBilling.getStoragesCost(), COST_DIVISOR).toString());
        });
        row.add(billingHelper.asString(billing.getRunsNumber()));
        row.add(divided(billing.getRunsDuration(), DURATION_DIVISOR).toString());
        row.add(divided(billing.getRunsCost(), COST_DIVISOR).toString());
        row.add(divided(billing.getStoragesCost(), COST_DIVISOR).toString());
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

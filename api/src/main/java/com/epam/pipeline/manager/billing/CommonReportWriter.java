package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.ReportBilling;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CommonReportWriter<B extends ReportBilling<M>, M> implements BillingWriter {

    private static final char SEPARATOR = ',';
    private static final String YEAR_MONTH_FORMAT = "MMMM yyyy";

    private final String name;
    private final List<String> detailColumns;
    private final List<String> periodColumns;
    private final List<Function<B, String>> detailExtractors;
    private final List<Function<M, String>> periodExtractors;
    private final CSVWriter writer;
    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;
    private final LocalDate from;
    private final LocalDate to;

    public CommonReportWriter(final Writer writer,
                              final BillingHelper billingHelper,
                              final PreferenceManager preferenceManager,
                              final LocalDate from,
                              final LocalDate to,
                              final String name,
                              final List<String> detailColumns,
                              final List<String> periodColumns,
                              final List<Function<B, String>> detailExtractors,
                              final List<Function<M, String>> periodExtractors) {
        this.writer = new CSVWriter(writer, SEPARATOR);
        this.billingHelper = billingHelper;
        this.preferenceManager = preferenceManager;
        this.from = from;
        this.to = to;
        this.name = name;
        this.detailColumns = detailColumns;
        this.periodColumns = periodColumns;
        this.detailExtractors = detailExtractors;
        this.periodExtractors = periodExtractors;
    }

    public void writeHeader() {
        final List<String> datesRow = new ArrayList<>();
        datesRow.add(name);
        IntStream.range(0, detailColumns.size() - 1).forEach(i -> datesRow.add(StringUtils.EMPTY));
        yms().forEach(ym -> {
            datesRow.add(DateTimeFormatter.ofPattern(YEAR_MONTH_FORMAT, Locale.US).format(ym));
            IntStream.range(0, periodColumns.size() - 1).forEach(i -> datesRow.add(StringUtils.EMPTY));
        });
        datesRow.add(from + " - " + to);
        IntStream.range(0, periodColumns.size() - 1).forEach(i -> datesRow.add(StringUtils.EMPTY));
        writer.writeNext(datesRow.toArray(new String[0]));
        final List<String> columnsRow = new ArrayList<>();
        columnsRow.addAll(detailColumns);
        yms().forEach(ym -> columnsRow.addAll(periodColumns));
        columnsRow.addAll(periodColumns);
        writer.writeNext(columnsRow.toArray(new String[0]));
    }

    public void write(final B billing) {
        final List<String> row = new ArrayList<>();
        detailExtractors.stream()
                .map(extractor -> extractor.apply(billing))
                .forEach(row::add);
        yms().forEach(ym -> {
            final M metrics = billing.getPeriodMetrics(ym);
            periodExtractors.stream()
                    .map(extractor -> extractor.apply(metrics))
                    .forEach(row::add);
        });
        periodExtractors.stream()
                .map(extractor -> extractor.apply(billing.getTotalMetrics()))
                .forEach(row::add);
        writer.writeNext(row.toArray(new String[0]));
    }

    private Stream<YearMonth> yms() {
        return Stream.iterate(YearMonth.from(from), ym -> ym.plusMonths(1L))
                .limit(ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1L);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

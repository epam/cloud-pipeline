package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.PeriodBilling;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class PeriodBillingWriter<B extends PeriodBilling<M>, M> implements BillingWriter<B> {

    private final CSVWriter writer;
    private final LocalDate from;
    private final LocalDate to;
    private final String name;
    private final List<String> detailColumns;
    private final List<String> periodColumns;
    private final List<Function<B, String>> detailExtractors;
    private final List<Function<M, String>> periodExtractors;
    private final int numberOfPeriods;
    private final int numberOfColumns;

    public PeriodBillingWriter(final Writer writer,
                               final LocalDate from,
                               final LocalDate to,
                               final String name,
                               final List<String> detailColumns,
                               final List<String> periodColumns,
                               final List<Function<B, String>> detailExtractors,
                               final List<Function<M, String>> periodExtractors) {
        this.writer = new CSVWriter(writer, BillingUtils.SEPARATOR);
        this.from = from;
        this.to = to;
        this.name = name;
        this.detailColumns = detailColumns;
        this.periodColumns = periodColumns;
        this.detailExtractors = detailExtractors;
        this.periodExtractors = periodExtractors;
        this.numberOfPeriods =  1 + (int) ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to));
        this.numberOfColumns = detailColumns.size() + periodColumns.size() * numberOfPeriods;
    }

    @Override
    public void writeHeader() {
        writePeriodHeader();
        writeDataHeader();
    }

    private void writePeriodHeader() {
        final List<String> row = new ArrayList<>(numberOfColumns);
        row.add(name);
        addCorrespondingEmptyColumns(row, detailColumns);
        periods().forEach(period -> {
            row.add(BillingUtils.asString(period));
            addCorrespondingEmptyColumns(row, periodColumns);
        });
        row.add(from + " - " + to);
        addCorrespondingEmptyColumns(row, periodColumns);
        writeRow(row);
    }

    private void addCorrespondingEmptyColumns(final List<String> row, final List<String> columns) {
        row.addAll(Collections.nCopies(Math.max(columns.size() - 1, 0), StringUtils.EMPTY));
    }

    private void writeDataHeader() {
        final List<String> row = new ArrayList<>(numberOfColumns);
        row.addAll(detailColumns);
        periods().forEach(period -> row.addAll(periodColumns));
        row.addAll(periodColumns);
        writeRow(row);
    }

    @Override
    public void write(final B billing) {
        final List<String> row = new ArrayList<>(numberOfColumns);
        addExtractedColumns(row, billing, detailExtractors);
        periods().forEach(period -> addExtractedColumns(row, billing.getPeriodMetrics(period), periodExtractors));
        addExtractedColumns(row, billing.getTotalMetrics(), periodExtractors);
        writeRow(row);
    }

    private <T> void addExtractedColumns(final List<String> row,
                                         final T value,
                                         final List<Function<T, String>> extractors) {
        for (Function<T, String> extractor : extractors) {
            row.add(extractor.apply(value));
        }
    }

    private Stream<Temporal> periods() {
        return Stream.iterate(YearMonth.from(from), ym -> ym.plusMonths(1L))
                .limit(numberOfPeriods)
                .map(Temporal.class::cast);
    }

    private void writeRow(final List<String> row) {
        writer.writeNext(row.toArray(new String[0]));
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

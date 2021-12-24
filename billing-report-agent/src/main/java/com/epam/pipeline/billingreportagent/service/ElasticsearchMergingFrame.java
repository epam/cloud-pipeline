package com.epam.pipeline.billingreportagent.service;

import com.epam.pipeline.billingreportagent.exception.BillingException;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;

@RequiredArgsConstructor
public enum ElasticsearchMergingFrame {

    DAY(ChronoUnit.DAYS,
            LocalDate::from,
            LocalDate::from,
            LocalDate::from,
            date -> DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date)),

    MONTH(ChronoUnit.MONTHS,
            YearMonth::from,
            ym -> YearMonth.from(ym).atDay(1),
            ym -> YearMonth.from(ym).atEndOfMonth(),
            ym -> DateTimeFormatter.ofPattern("yyyy-MM'm'").format(ym)),

    YEAR(ChronoUnit.YEARS,
            Year::from,
            y -> Year.from(y).atDay(1),
            y -> Year.from(y).atDay(Year.from(y).length()),
            y -> DateTimeFormatter.ofPattern("yyyy'y'").format(y));

    private static final Comparator<ElasticsearchMergingFrame> DURATION_COMPARATOR =
            Comparator.comparing(ElasticsearchMergingFrame::unit,
                    Comparator.comparing(ChronoUnit::getDuration));

    private final ChronoUnit unit;
    private final Function<LocalDate, Temporal> getDatePeriod;
    private final Function<Temporal, LocalDate> getPeriodStart;
    private final Function<Temporal, LocalDate> getPeriodEnd;
    private final Function<Temporal, String> getPeriodName;

    public ChronoUnit unit() {
        return unit;
    }

    public Temporal periodOf(final LocalDate date) {
        return getDatePeriod.apply(date);
    }

    public LocalDate startOf(final Temporal period) {
        return getPeriodStart.apply(period);
    }

    public LocalDate endOf(final Temporal period) {
        return getPeriodEnd.apply(period);
    }

    public String nameOf(final Temporal period) {
        return getPeriodName.apply(period);
    }

    public Stream<String> subPeriodNamesOf(final Temporal period) {
        if (ordinal() < 1) {
            throw new BillingException(String.format("Synchronization frame %s does not have child frame", name()));
        }
        final ElasticsearchMergingFrame childFrame = values()[ordinal() - 1];
        return Stream.iterate(startOf(period), date -> date.plusDays(1))
                .limit(ChronoUnit.DAYS.between(startOf(period), endOf(period)) + 1)
                .map(childFrame::periodOf)
                .distinct()
                .map(childFrame::nameOf);
    }

    public Stream<Temporal> periods(final LocalDate from, final LocalDate to) {
        return Stream.iterate(from, date -> date.plusDays(1))
                .map(this::periodOf)
                .distinct()
                .limit(unit().between(from, to) + 1);
    }

    public static Comparator<ElasticsearchMergingFrame> comparingByDuration() {
        return DURATION_COMPARATOR;
    }
}

package com.epam.pipeline.manager.billing.index;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PeriodBillingIndexHelperTest {

    private static final String PREFIX = "index-";
    private static final String RUN = "run";
    private static final String STORAGE = "storage";
    private static final int YEAR = 2022;

    private final PeriodBillingIndexHelper helper = new PeriodBillingIndexHelper(PREFIX, RUN, STORAGE);

    @Test
    public void dailyIndicesShouldGenerateProperIndicesForMonth() {
        Assert.assertArrayEquals(
                dailyIndices(Month.JANUARY, 1, 31),
                helper.dailyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 1),
                        LocalDate.of(YEAR, Month.JANUARY, 31)));
    }

    @Test
    public void dailyIndicesShouldGenerateProperIndicesForSeveralDaysInMonth() {
        Assert.assertArrayEquals(
                dailyIndices(Month.JANUARY, 5, 10),
                helper.dailyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 5),
                        LocalDate.of(YEAR, Month.JANUARY, 10)));
    }

    @Test
    public void dailyIndicesShouldGenerateProperIndicesForSeveralDaysInSeveralMonths() {
        Assert.assertArrayEquals(concat(
                        dailyIndices(Month.JANUARY, 20, 31),
                        dailyIndices(Month.FEBRUARY, 1, 10)),
                helper.dailyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 20),
                        LocalDate.of(YEAR, Month.FEBRUARY, 10)));
    }

    @Test
    public void monthlyIndicesShouldGenerateProperIndicesForMonth() {
        Assert.assertArrayEquals(
                monthlyIndices(YearMonth.of(YEAR, Month.JANUARY)),
                helper.monthlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 1),
                        LocalDate.of(YEAR, Month.JANUARY, 31)));
    }

    @Test
    public void monthlyIndicesShouldGenerateProperIndicesForSeveralDaysInMonth() {
        Assert.assertArrayEquals(
                dailyIndices(Month.JANUARY, 5, 10),
                helper.monthlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 5),
                        LocalDate.of(YEAR, Month.JANUARY, 10)));
    }

    @Test
    public void monthlyIndicesShouldGenerateProperIndicesForSeveralDaysInSeveralMonths() {
        Assert.assertArrayEquals(concat(
                        dailyIndices(Month.JANUARY, 20, 31),
                        dailyIndices(Month.FEBRUARY, 1, 10)),
                helper.monthlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 20),
                        LocalDate.of(YEAR, Month.FEBRUARY, 10)));
    }

    @Test
    public void monthlyIndicesShouldGenerateProperIndicesForSeveralMonths() {
        Assert.assertArrayEquals(concat(
                        monthlyIndices(YearMonth.of(YEAR, Month.JANUARY)),
                        monthlyIndices(YearMonth.of(YEAR, Month.FEBRUARY))),
                helper.monthlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 1),
                        LocalDate.of(YEAR, Month.FEBRUARY, 28)));
    }

    @Test
    public void monthlyIndicesShouldGenerateProperIndicesForSeveralDaysAndSeveralMonths() {
        Assert.assertArrayEquals(concat(
                        dailyIndices(Month.JANUARY, 20, 31),
                        monthlyIndices(YearMonth.of(YEAR, Month.FEBRUARY)),
                        dailyIndices(Month.MARCH, 1, 15)),
                helper.monthlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 20),
                        LocalDate.of(YEAR, Month.MARCH, 15)));
    }


    @Test
    public void yearIndicesShouldGenerateProperIndicesForMonth() {
        Assert.assertArrayEquals(
                monthlyIndices(YearMonth.of(YEAR, Month.JANUARY)),
                helper.yearlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 1),
                        LocalDate.of(YEAR, Month.JANUARY, 31)));
    }

    @Test
    public void yearlyIndicesShouldGenerateProperIndicesForSeveralDaysInMonth() {
        Assert.assertArrayEquals(
                dailyIndices(Month.JANUARY, 5, 10),
                helper.yearlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 5),
                        LocalDate.of(YEAR, Month.JANUARY, 10)));
    }

    @Test
    public void yearlyIndicesShouldGenerateProperIndicesForSeveralDaysInSeveralMonths() {
        Assert.assertArrayEquals(concat(
                        dailyIndices(Month.JANUARY, 20, 31),
                        dailyIndices(Month.FEBRUARY, 1, 10)),
                helper.yearlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 20),
                        LocalDate.of(YEAR, Month.FEBRUARY, 10)));
    }

    @Test
    public void yearlyIndicesShouldGenerateProperIndicesForSeveralMonths() {
        Assert.assertArrayEquals(concat(
                        monthlyIndices(YearMonth.of(YEAR, Month.JANUARY)),
                        monthlyIndices(YearMonth.of(YEAR, Month.FEBRUARY))),
                helper.yearlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 1),
                        LocalDate.of(YEAR, Month.FEBRUARY, 28)));
    }

    @Test
    public void yearlyIndicesShouldGenerateProperIndicesForSeveralDaysAndSeveralMonths() {
        Assert.assertArrayEquals(concat(
                        dailyIndices(Month.JANUARY, 20, 31),
                        monthlyIndices(YearMonth.of(YEAR, Month.FEBRUARY)),
                        dailyIndices(Month.MARCH, 1, 15)),
                helper.yearlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 20),
                        LocalDate.of(YEAR, Month.MARCH, 15)));
    }

    @Test
    public void yearlyIndicesShouldGenerateProperIndicesForYear() {
        Assert.assertArrayEquals(concat(
                        yearlyIndices(Year.of(YEAR))),
                helper.yearlyIndicesBetween(
                        LocalDate.of(YEAR, Month.JANUARY, 1),
                        LocalDate.of(YEAR, Month.DECEMBER, 31)));
    }

    @Test
    public void yearlyIndicesShouldGenerateProperIndicesForSeveralDaysInSeveralYears() {
        Assert.assertArrayEquals(concat(
                        dailyIndices(Year.of(YEAR - 1), Month.DECEMBER, 20, 31),
                        dailyIndices(Month.JANUARY, 1, 15)),
                helper.yearlyIndicesBetween(
                        LocalDate.of(YEAR - 1, Month.DECEMBER, 20),
                        LocalDate.of(YEAR, Month.JANUARY, 15)));
    }

    @Test
    public void yearlyIndicesShouldGenerateProperIndicesForSeveralDaysAndSeveralMonthsAndSeveralYears() {
        Assert.assertArrayEquals(concat(
                        dailyIndices(Year.of(YEAR - 2), Month.NOVEMBER, 20, 30),
                        monthlyIndices(YearMonth.of(YEAR - 2, Month.DECEMBER)),
                        yearlyIndices(Year.of(YEAR - 1)),
                        dailyIndices(Month.JANUARY, 1, 15)),
                helper.yearlyIndicesBetween(
                        LocalDate.of(YEAR - 2, Month.NOVEMBER, 20),
                        LocalDate.of(YEAR, Month.JANUARY, 15)));
    }

    private String[] concat(final String[]... items) {
        return Arrays.stream(items)
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }

    private String[] dailyIndices(final Month month,
                                  final int startDayOfMonth, final int endDayOfMonth) {
        return dailyIndices(Year.of(YEAR), month, startDayOfMonth, endDayOfMonth);
    }

    private String[] dailyIndices(final Year year, final Month month,
                                  final int startDayOfMonth, final int endDayOfMonth) {
        return IntStream.range(startDayOfMonth, endDayOfMonth + 1)
                .mapToObj(day -> Stream.of(
                        String.format("%s%s-%d-%02d-%02d", PREFIX, RUN, year.getValue(), month.getValue(), day),
                        String.format("%s%s-%d-%02d-%02d", PREFIX, STORAGE, year.getValue(), month.getValue(), day)))
                .flatMap(Function.identity())
                .toArray(String[]::new);
    }

    private String[] monthlyIndices(final YearMonth ym) {
        return new String[]{
                String.format("%s%s-%d-%02dm", PREFIX, RUN, ym.getYear(), ym.getMonth().getValue()),
                String.format("%s%s-%d-%02dm", PREFIX, STORAGE, ym.getYear(), ym.getMonth().getValue())
        };
    }

    private String[] yearlyIndices(final Year year) {
        return new String[]{
                String.format("%s%s-%dy", PREFIX, RUN, year.getValue()),
                String.format("%s%s-%dy", PREFIX, STORAGE, year.getValue())
        };
    }

}

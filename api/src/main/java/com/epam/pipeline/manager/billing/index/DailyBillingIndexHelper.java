package com.epam.pipeline.manager.billing.index;

import com.epam.pipeline.manager.billing.BillingUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

@Slf4j
public class DailyBillingIndexHelper implements BillingIndexHelper {

    private final String billingIndicesMonthlyPattern;
    private final String billingRunIndicesMonthlyPattern;
    private final String billingStorageIndicesMonthlyPattern;

    public DailyBillingIndexHelper(final String commonIndexPrefix,
                                   final String runIndexName,
                                   final String storageIndexName) {
        this.billingIndicesMonthlyPattern = String.join("-",
                commonIndexPrefix + BillingUtils.ES_WILDCARD,
                BillingUtils.ES_MONTHLY_DATE_REGEXP);
        this.billingRunIndicesMonthlyPattern = String.join("-",
                commonIndexPrefix + runIndexName,
                BillingUtils.ES_MONTHLY_DATE_REGEXP);
        this.billingStorageIndicesMonthlyPattern = String.join("-",
                commonIndexPrefix + storageIndexName,
                BillingUtils.ES_MONTHLY_DATE_REGEXP);
    }

    @Override
    public String[] dailyIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to, billingIndicesMonthlyPattern);
    }

    @Override
    public String[] dailyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to, billingRunIndicesMonthlyPattern);
    }

    @Override
    public String[] dailyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to, billingStorageIndicesMonthlyPattern);
    }

    @Override
    public String[] monthlyIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to);
    }

    @Override
    public String[] monthlyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to);
    }

    @Override
    public String[] monthlyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to);
    }

    @Override
    public String[] yearlyIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to);
    }

    @Override
    public String[] yearlyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to);
    }

    @Override
    public String[] yearlyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndicesBetween(from, to);
    }

    public String[] dailyIndicesBetween(final LocalDate from, final LocalDate to, final String indexPattern) {
        return Stream.iterate(from, d -> d.plus(1, ChronoUnit.MONTHS))
                .limit(ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1)
                .map(date -> String.format(indexPattern, date.getYear(), date.getMonthValue()))
                .toArray(String[]::new);
    }
}

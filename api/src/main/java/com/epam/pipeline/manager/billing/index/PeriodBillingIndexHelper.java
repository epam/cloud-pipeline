package com.epam.pipeline.manager.billing.index;

import com.epam.pipeline.manager.billing.ElasticsearchMergingFrame;
import com.epam.pipeline.manager.billing.ElasticsearchMergingFramePeriod;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PeriodBillingIndexHelper implements BillingIndexHelper {

    private final String runIndexPrefix;
    private final String storageIndexPrefix;

    public PeriodBillingIndexHelper(final String commonIndexPrefix,
                                    final String runIndexName,
                                    final String storageIndexName) {
        this.runIndexPrefix = commonIndexPrefix + runIndexName;
        this.storageIndexPrefix = commonIndexPrefix + storageIndexName;
    }

    @Override
    public String[] dailyIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndices(from, to, runIndexPrefix, storageIndexPrefix);
    }

    @Override
    public String[] dailyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndices(from, to, runIndexPrefix);
    }

    @Override
    public String[] dailyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return dailyIndices(from, to, storageIndexPrefix);
    }

    private String[] dailyIndices(final LocalDate from, final LocalDate to,
                                  final String... indexPrefix) {
        return indicesBetween(from, to, ElasticsearchMergingFrame.DAY, indexPrefix);
    }

    @Override
    public String[] monthlyIndicesBetween(final LocalDate from, final LocalDate to) {
        return monthlyIndices(from, to, runIndexPrefix, storageIndexPrefix);
    }

    @Override
    public String[] monthlyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return monthlyIndices(from, to, runIndexPrefix);
    }

    @Override
    public String[] monthlyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return monthlyIndices(from, to, storageIndexPrefix);
    }

    private String[] monthlyIndices(final LocalDate from, final LocalDate to,
                                    final String... indexPrefix) {
        return indicesBetween(from, to, ElasticsearchMergingFrame.MONTH, indexPrefix);
    }

    @Override
    public String[] yearlyIndicesBetween(final LocalDate from, final LocalDate to) {
        return yearlyIndicesBetween(from, to, runIndexPrefix, storageIndexPrefix);
    }

    @Override
    public String[] yearlyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return yearlyIndicesBetween(from, to, runIndexPrefix);
    }

    @Override
    public String[] yearlyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return yearlyIndicesBetween(from, to, storageIndexPrefix);
    }

    private String[] yearlyIndicesBetween(final LocalDate from, final LocalDate to,
                                          final String... indexPrefix) {
        return indicesBetween(from, to, ElasticsearchMergingFrame.YEAR, indexPrefix);
    }

    public String[] indicesBetween(final LocalDate from, final LocalDate to,
                                   final ElasticsearchMergingFrame frame,
                                   final String... indexPrefix) {
        final List<String> indices = new ArrayList<>();
        LocalDate current = from;
        while (current.isBefore(to.plusDays(1))) {
            final ElasticsearchMergingFramePeriod period = getNextPeriod(current, to, frame);
            for (final String prefix : indexPrefix) {
                indices.add(prefix + "-" + period.name());
            }
            current = period.end().plusDays(1);
        }
        return indices.toArray(new String[]{});
    }

    private ElasticsearchMergingFramePeriod getNextPeriod(final LocalDate from, final LocalDate to,
                                                          final ElasticsearchMergingFrame frame) {
        return frame.children()
                .map(childFrame -> new ElasticsearchMergingFramePeriod(childFrame, childFrame.periodOf(from)))
                .filter(period -> period.isBetween(from, to))
                .findFirst()
                .orElseGet(() -> new ElasticsearchMergingFramePeriod(ElasticsearchMergingFrame.DAY, from));
    }
}

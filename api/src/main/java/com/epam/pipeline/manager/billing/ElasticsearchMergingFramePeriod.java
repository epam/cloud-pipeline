package com.epam.pipeline.manager.billing;

import lombok.Value;

import java.time.LocalDate;
import java.time.temporal.Temporal;

@Value
public class ElasticsearchMergingFramePeriod {

    ElasticsearchMergingFrame frame;
    Temporal period;

    public String name() {
        return frame.nameOf(period);
    }

    public LocalDate start() {
        return frame.startOf(period);
    }

    public LocalDate end() {
        return frame.endOf(period);
    }

    public boolean isAfter(final LocalDate date) {
        final LocalDate start = start();
        return start.isAfter(date) || start.isEqual(date);
    }

    public boolean isBefore(final LocalDate date) {
        final LocalDate end = end();
        return end.isBefore(date) || end.isEqual(date);
    }

    public boolean isBetween(final LocalDate start, final LocalDate end) {
        return isAfter(start) && isBefore(end);
    }
}

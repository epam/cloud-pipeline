package com.epam.pipeline.utils;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.utils.DateUtils;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;

public class RunDurationUtils {

    public static Duration getOverallDuration(final PipelineRun run) {
        return durationBetween(run.getStartDate(), run.getEndDate());
    }

    public static Duration getBillableDuration(final PipelineRun run) {
        return durationBetween(run.getInstanceStartDate(), run.getEndDate());
    }

    private static Duration durationBetween(final Date from, final Date to) {
        final Date end = Optional.ofNullable(to).orElseGet(DateUtils::now);
        final Date start = Optional.ofNullable(from).orElse(end);
        return Duration.ofMillis(end.getTime() - start.getTime()).abs();
    }
}

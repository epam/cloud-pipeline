/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.utils.RunDurationUtils;
import com.opencsv.CSVWriter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.util.TextUtils;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.utils.PipelineStringUtils.formatNullable;

public class PipelineRunExporter {
    private static final List<String> HEADER = Arrays.asList("Run ID", "Run Name", "Parent Run ID", "Instance Type",
            "Tags", "Pipeline", "Docker Image", "Started Date", "Completed Date", "Owner", "Costs");
    private static final String KEY_VALUE_PATTERN = "%s:%s";
    private static final int DIVIDE_SCALE = 5;
    private static final int REPORT_SCALE = 2;
    private static final int MINUTES_IN_HOUR = 60;

    public String export(final Collection<PipelineRun> runs,
                         final String delimiter, final String fieldDelimiter) {
        final StringWriter writer = new StringWriter();
        final CSVWriter csvWriter = new CSVWriter(writer, delimiter.charAt(0));
        csvWriter.writeNext(HEADER.toArray(new String[0]), false);
        CollectionUtils.emptyIfNull(runs).stream()
                .map(run -> toLine(run, fieldDelimiter))
                .forEach(line -> csvWriter.writeNext(line, false));
        return writer.toString();
    }

    private String[] toLine(final PipelineRun run, final String fieldDelimiter) {
        final List<String> result = new ArrayList<>();
        result.add(String.valueOf(run.getId()));
        result.add(getName(run));
        result.add(formatNullable(run.getParentRunId()));
        result.add(Optional.ofNullable(run.getInstance()).map(RunInstance::getNodeType).orElse(StringUtils.EMPTY));

        final List<String> tags = new ArrayList<>();
        MapUtils.emptyIfNull(run.getTags()).forEach((k, v) -> tags.add(String.format(KEY_VALUE_PATTERN, k, v)));
        result.add(String.join(fieldDelimiter, tags));

        result.add(TextUtils.isBlank(run.getPipelineName()) || TextUtils.isBlank(run.getVersion()) ?
                StringUtils.EMPTY : String.format(KEY_VALUE_PATTERN, run.getPipelineName(), run.getVersion()));
        result.add(formatNullable(run.getDockerImage()));
        result.add(DateUtils.formatDate(run.getStartDate()));
        result.add(Optional.ofNullable(run.getEndDate())
                .map(d -> DateUtils.formatDate(run.getEndDate()))
                .orElse(StringUtils.EMPTY));
        result.add(formatNullable(run.getOwner()));
        result.add(formatNullable(getCosts(run)));
        return result.toArray(new String[0]);
    }

    private static String getName(final PipelineRun run) {
        return TextUtils.isBlank(run.getName()) ?
                TextUtils.isBlank(run.getPodId()) ? StringUtils.EMPTY : run.getPodId() :
                run.getName();
    }

    String getCosts(final PipelineRun run) {
        final BigDecimal computePricePerHour = getNullableBigDecimal(run.getComputePricePerHour());
        final BigDecimal computeCosts = getComputeCosts(run, computePricePerHour);
        final BigDecimal storageCosts = getStorageCosts(run, computePricePerHour);

        return storageCosts.add(computeCosts)
                .add(getNullableBigDecimal(run.getWorkersPrice()))
                .setScale(REPORT_SCALE, RoundingMode.HALF_EVEN)
                .toString();
    }

    private BigDecimal getComputeCosts(final PipelineRun run, final BigDecimal computePricePerHour) {
        final BigDecimal runActiveHours = toHours(getRunActiveStateMinutes(run));
        return runActiveHours.multiply(computePricePerHour);
    }

    private BigDecimal getStorageCosts(final PipelineRun run, final BigDecimal computePricePerHour) {
        final BigDecimal totalRunHours = toHours(RunDurationUtils.getBillableDuration(run).toMinutes());

        final BigDecimal runPricePerHour = getNullableBigDecimal(run.getPricePerHour());
        final BigDecimal diskPricePerHour = runPricePerHour.subtract(computePricePerHour);
        return totalRunHours.multiply(diskPricePerHour);
    }

    private BigDecimal getNullableBigDecimal(final BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO);
    }

    private BigDecimal toHours(final long minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(MINUTES_IN_HOUR), DIVIDE_SCALE, RoundingMode.HALF_EVEN);
    }

    private long getRunActiveStateMinutes(final PipelineRun run) {
        final List<RunStatus> statuses = run.getRunStatuses();
        if (CollectionUtils.isEmpty(statuses)) {
            return RunDurationUtils.durationBetween(run.getInstanceStartDate(), run.getEndDate()).toMinutes();
        }
        final LocalDateTime end = Optional.ofNullable(run.getEndDate())
                .map(DateUtils::convertDateToLocalDateTime)
                .orElse(LocalDateTime.now(Clock.systemUTC()));
        final LocalDateTime start = Optional.ofNullable(run.getInstanceStartDate())
                .map(DateUtils::convertDateToLocalDateTime)
                .orElse(end);

        final List<RunStatus> sortedStatuses = prepareStatuses(statuses, start, end);

        final List<Duration> durations = new ArrayList<>();
        for (int i = 0; i < sortedStatuses.size() - 1; i++) {
            final RunStatus previous = sortedStatuses.get(i);
            final RunStatus current = sortedStatuses.get(i + 1);
            if (isActiveStatus(previous.getStatus())) {
                durations.add(Duration.between(previous.getTimestamp(), current.getTimestamp()));
            }
        }
        return durations.stream()
                .mapToLong(Duration::toMinutes)
                .sum();
    }

    private List<RunStatus> prepareStatuses(final List<RunStatus> statuses, final LocalDateTime start,
                                            final LocalDateTime end) {
        final List<RunStatus> statusesWithArtificialBorders = new ArrayList<>();
        statusesWithArtificialBorders.add(RunStatus.builder().status(TaskStatus.RUNNING).timestamp(start).build());
        statusesWithArtificialBorders.add(RunStatus.builder().status(TaskStatus.STOPPED).timestamp(end).build());
        statusesWithArtificialBorders.addAll(statuses);

        return statusesWithArtificialBorders.stream()
                .sorted(Comparator.comparing(RunStatus::getTimestamp))
                .collect(Collectors.toList());
    }

    private boolean isActiveStatus(final TaskStatus status) {
        return !TaskStatus.PAUSED.equals(status) && !status.isFinal();
    }
}

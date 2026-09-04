/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class PipelineRunExporterTest {
    private static final double COMPUTE_PRICE = 0.5;
    private static final double TOTAL_PRICE = 0.7;

    @Test
    public void shouldCalculateCostsForCompletedRun() {
        final Date start = DateTime.now(DateTimeZone.UTC).minusDays(1).toDate();
        final Date end = DateTime.now(DateTimeZone.UTC).minusDays(1).plusHours(7).plusMinutes(7).toDate();

        final LocalDateTime initialDateTime = LocalDateTime.now(Clock.systemUTC()).minusDays(1);
        final List<RunStatus> runStatuses = Arrays.asList(
                RunStatus.builder()
                        .status(TaskStatus.RUNNING)
                        .timestamp(initialDateTime)
                        .build(),
                RunStatus.builder()
                        .status(TaskStatus.STOPPED)
                        .timestamp(initialDateTime.plusHours(7).plusMinutes(6))
                        .build()
        );

        // total duration: 7 hours 7 minutes; active time: 7 hours 7 minutes
        final PipelineRun run = run(start, end, runStatuses);

        final String actualCosts = new PipelineRunExporter().getCosts(run);
        // 0.5 * 7 hours 7 minutes + 0.2 * 7 hours 7 minutes
        final String expectedCosts = "4.97";

        assertThat(actualCosts).isEqualTo(expectedCosts);
    }

    @Test
    public void shouldCalculateCostsForRunningRun() {
        final Date start = DateTime.now(DateTimeZone.UTC).minusHours(2).toDate();

        final List<RunStatus> runStatuses = Collections.singletonList(
                RunStatus.builder()
                        .status(TaskStatus.RUNNING)
                        .timestamp(LocalDateTime.now(Clock.systemUTC()).minusHours(2))
                        .build()
        );

        final PipelineRun run = run(start, null, runStatuses);

        final String actualCosts = new PipelineRunExporter().getCosts(run);
        // (0.5 + 0.2) * 2 hours
        final String expectedCosts = "1.40";

        assertThat(actualCosts).isEqualTo(expectedCosts);
    }

    @Test
    public void shouldCalculateCostsForCompletedRunWithPauses() {
        final Date start = DateTime.now(DateTimeZone.UTC).minusDays(1).toDate();
        final Date end = DateTime.now(DateTimeZone.UTC).minusDays(1).plusHours(7).plusMinutes(7).toDate();

        final LocalDateTime initialDateTime = LocalDateTime.now(Clock.systemUTC()).minusDays(1);
        final List<RunStatus> runStatuses = generateRunActivity(initialDateTime);
        runStatuses.add(RunStatus.builder()
                .status(TaskStatus.STOPPED)
                .timestamp(initialDateTime.plusHours(7).plusMinutes(6))
                .build());

        // total duration: 7 hours 7 minutes; active time: 5 hours 6 minutes
        final PipelineRun run = run(start, end, runStatuses);

        final String actualCosts = new PipelineRunExporter().getCosts(run);
        // 0.5 * 5 hours 6 minutes + 0.2 * 7 hours 7 minutes = 0.5 * 5.1 hours + 0.2 * 7.11667 hours = 2.55 + 1.423334
        final String expectedCosts = "3.97";

        assertThat(actualCosts).isEqualTo(expectedCosts);
    }

    @Test
    public void shouldCalculateCostsForRunningRunWithPauses() {
        final Date start = DateTime.now(DateTimeZone.UTC).minusHours(6).minusMinutes(6).toDate();

        final LocalDateTime initialDateTime = LocalDateTime.now(Clock.systemUTC()).minusHours(6).minusMinutes(6);
        final List<RunStatus> runStatuses = generateRunActivity(initialDateTime);

        // total duration: 6 hours 6 minutes; active time: 4 hours 6 minutes
        final PipelineRun run = run(start, null, runStatuses);

        final String actualCosts = new PipelineRunExporter().getCosts(run);
        // 0.5 * 4 hours 6 minutes + 0.2 * 6 hours 6 minutes = 0.5 * 4.1 hours + 0.2 * 6.1 hours = 2.05 + 1.22
        final String expectedCosts = "3.27";

        assertThat(actualCosts).isEqualTo(expectedCosts);
    }

    @Test
    public void shouldCalculateCostsForRunningRunWithEmptyStates() {
        final Date start = DateTime.now(DateTimeZone.UTC).minusHours(6).minusMinutes(6).toDate();

        // total duration: 6 hours 6 minutes; active time: 6 hours 6 minutes
        final PipelineRun run = run(start, null, null);

        final String actualCosts = new PipelineRunExporter().getCosts(run);
        // 6 hours 6 minutes * (0.5 + 0.2) = 6.1 * 0.7
        final String expectedCosts = "4.27";

        assertThat(actualCosts).isEqualTo(expectedCosts);
    }

    @Test
    public void shouldCalculateCostsForClusterCosts() {
        final Date start = DateTime.now(DateTimeZone.UTC).minusDays(1).toDate();
        final Date end = DateTime.now(DateTimeZone.UTC).minusDays(1).plusHours(7).plusMinutes(7).toDate();

        // total duration: 7 hours 7 minutes; active time: 7 hours 7 minutes
        final PipelineRun run = run(start, end, null);
        run.setWorkersPrice(BigDecimal.TEN);

        final String actualCosts = new PipelineRunExporter().getCosts(run);
        // 7 hours 7 minutes * (0.5 + 0.2) + 10 = 7.11667 * 0.7 + 10
        final String expectedCosts = "14.98";

        assertThat(actualCosts).isEqualTo(expectedCosts);
    }

    @Test
    public void shouldCalculateCostsForCompletedRunWithEmptyStates() {
        final Date start = DateTime.now(DateTimeZone.UTC).minusDays(1).toDate();
        final Date end = DateTime.now(DateTimeZone.UTC).minusDays(1).plusHours(7).plusMinutes(7).toDate();

        // total duration: 7 hours 7 minutes; active time: 7 hours 7 minutes
        final PipelineRun run = run(start, end, null);

        final String actualCosts = new PipelineRunExporter().getCosts(run);
        // 7 hours 7 minutes * (0.5 + 0.2) = 7.11667 * 0.7
        final String expectedCosts = "4.98";

        assertThat(actualCosts).isEqualTo(expectedCosts);
    }

    private static List<RunStatus> generateRunActivity(final LocalDateTime initialDateTime) {
        final List<RunStatus> runStatuses = new ArrayList<>();

        LocalDateTime timestamp = initialDateTime.plusMinutes(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.RUNNING).timestamp(timestamp).build());

        // active time: 1 hour 1 minute; paused time: -
        timestamp = timestamp.plusHours(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.RUNNING).timestamp(timestamp).build());

        // active time: 2 hours 1 minute; paused time: -
        timestamp = timestamp.plusHours(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.PAUSING).timestamp(timestamp).build());

        // active time: 3 hours 1 minute; paused time: -
        timestamp = timestamp.plusHours(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.PAUSING).timestamp(timestamp).build());

        // active time: 3 hours 2 minutes; paused time: -
        timestamp = timestamp.plusMinutes(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.PAUSED).timestamp(timestamp).build());

        // active time: 3 hours 2 minutes; paused time: 1 hour
        timestamp = timestamp.plusHours(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.RESUMING).timestamp(timestamp).build());

        // active time: 3 hours 3 minutes; paused time: 1 hour
        timestamp = timestamp.plusMinutes(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.RUNNING).timestamp(timestamp).build());

        // active time: 4 hours 3 minutes; paused time: 1 hour
        timestamp = timestamp.plusHours(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.PAUSING).timestamp(timestamp).build());

        // active time: 4 hours 5 minutes; paused time: 1 hour
        timestamp = timestamp.plusMinutes(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.PAUSED).timestamp(timestamp).build());

        // active time: 4 hours 5 minutes; paused time: 2 hours
        timestamp = timestamp.plusHours(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.RESUMING).timestamp(timestamp).build());

        // active time: 4 hours 6 minutes; paused time: 2 hours
        timestamp = timestamp.plusMinutes(1);
        runStatuses.add(RunStatus.builder().status(TaskStatus.RUNNING).timestamp(timestamp).build());

        // total time: 6 hours 6 minutes
        return runStatuses;
    }

    private static PipelineRun run(final Date start, final Date end, final List<RunStatus> runStatuses) {
        final PipelineRun run = new PipelineRun();
        run.setStartDate(start);
        run.setInstanceStartDate(start);
        run.setEndDate(end);
        run.setRunStatuses(runStatuses);
        run.setPricePerHour(BigDecimal.valueOf(TOTAL_PRICE));
        run.setComputePricePerHour(BigDecimal.valueOf(COMPUTE_PRICE));
        return run;
    }
}

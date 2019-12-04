/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.scheduling;

import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineRunScheduleManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

@Slf4j
@Component
@ConditionalOnExpression("${run.scheduling}")
public class PipelineRunSchedulerPureJava extends AbstractSchedulingManager {

    private final PipelineRunManager pipelineRunManager;
    private final List<RunSchedule> schedules;
    private static final int SCHEDULER_INVOCATION_PERIOD =
        SystemPreferences.LAUNCH_TASK_STATUS_UPDATE_RATE.getDefaultValue();

    @Autowired
    PipelineRunSchedulerPureJava(final PipelineRunManager pipelineRunManager) {
        this.pipelineRunManager = pipelineRunManager;
        this.schedules = new CopyOnWriteArrayList<>();
    }

    @PostConstruct
    public void init() {
        scheduleFixedDelay(this::executeScheduledTasks, SystemPreferences.LAUNCH_TASK_STATUS_UPDATE_RATE,
                           "Pipeline run scheduling service");
    }

    public void scheduleRunSchedule(final RunSchedule task) {
        schedules.add(task);
    }

    public void unscheduleRunSchedule(final RunSchedule task) {
        schedules.removeIf(s -> s.getId().equals(task.getId()));
    }

    public void executeScheduledTasks() {
        final Date checkStart = new Date(System.currentTimeMillis());
        final Date checkEnd = new Date(checkStart.toInstant().plusMillis(SCHEDULER_INVOCATION_PERIOD).toEpochMilli());
        final Map<Long, List<RunSchedule>> runSchedulesMap = schedules.stream()
            .collect(Collectors.groupingBy(RunSchedule::getRunId));

        runSchedulesMap.values()
            .parallelStream()
            .flatMap(runSchedules -> extractScheduledActionsForNextTick(runSchedules, checkStart, checkEnd))
            .sorted(Comparator.comparing(ScheduledAction::getFireTime))
            .forEachOrdered(this::executeAction);
    }

    private boolean isNotExecutingThisTick(final RunSchedule task, final Date checkStart, final Date checkEnd) {
        return getNextExecutionDate(task, checkStart).after(checkEnd);
    }

    private Date getNextExecutionDate(final RunSchedule schedule, final Date checkStart) {
        final CronSequenceGenerator generator = new CronSequenceGenerator(schedule.getCronExpression(),
                                                                          schedule.getTimeZone());
        return generator.next(checkStart);
    }

    @Value
    private static class ScheduledAction {

        private Long runId;
        private RunScheduledAction action;
        private Date fireTime;
    }

    private Stream<ScheduledAction> extractScheduledActionsForNextTick(final List<RunSchedule> schedules,
                                                                       final Date checkStart,
                                                                       final Date checkEnd) {
        schedules.removeIf(s -> isNotExecutingThisTick(s, checkStart, checkEnd));
        final List<ScheduledAction> extractedActions = new ArrayList<>();
        for (RunSchedule schedule : schedules) {
            extractedActions.addAll(extractActionForNextTick(schedule, checkStart, checkEnd));
        }
        /* TODO Can be filtered more: if few actions are scheduled for exact one time, execute only one.
        List<ScheduledAction> unique = extractedActions.stream()
            .collect(Collectors.collectingAndThen(
                Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(ScheduledAction::getFireTime))),
                ArrayList::new));

         */
        return extractedActions.stream();
    }

    private List<ScheduledAction> extractActionForNextTick(final RunSchedule schedule, final Date checkStart,
                                                           final Date checkEnd) {
        final List<ScheduledAction> result = new ArrayList<>();
        Date nextFireTime = getNextExecutionDate(schedule, checkStart);
        do {
            result.add(new ScheduledAction(schedule.getRunId(), schedule.getAction(), nextFireTime));
            nextFireTime = getNextExecutionDate(schedule, nextFireTime);
        } while (nextFireTime.before(checkEnd));
        return result;
    }

    private void executeAction(final ScheduledAction task) {
        /* TODO decide if we really need to schedule with that high accuracy, or SCHEDULER_INVOCATION_PERIOD
            difference at worst isn't so important
        final long timeDiff = task.getFireTime().getTime() - System.currentTimeMillis();
        if (timeDiff > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(timeDiff);
            } catch (InterruptedException e) {
                log.warn("Can't wait till fireTime, execute scheduled {} action for pipeline_id: {} immediately.",
                         task.getAction(),
                         task.getRunId());
            }
        }
        */
        switch (task.getAction()) {
            case RESUME:
                pipelineRunManager.resumeRun(task.getRunId());
                break;
            case PAUSE:
                pipelineRunManager.pauseRun(task.getRunId(), true);
                break;
            default:
                break;
        }
    }
}

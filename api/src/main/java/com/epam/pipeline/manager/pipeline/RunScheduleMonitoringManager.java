package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import com.epam.pipeline.manager.scheduling.RunScheduler;
import com.nimbusds.jose.util.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class RunScheduleMonitoringManager extends AbstractSchedulingManager {

    private final RunScheduleMonitoringManager.RunScheduleMonitoringManagerCore core;
    private final RunScheduler scheduler;
    private final RunScheduleManager runScheduleManager;

    @PostConstruct
    public void init() {
        log.debug("Initiating schedules...");
        scheduler.init();
        runScheduleManager.loadAllSchedules().forEach(scheduler::scheduleRunScheduleIfPossible);
        log.debug("Initiating schedules monitoring...");
        scheduleFixedDelaySecured(core::monitor, SystemPreferences.SYSTEM_SCHEDULE_MONITORING_PERIOD,
                TimeUnit.SECONDS, "Schedule Monitoring");
    }

    @Service
    @RequiredArgsConstructor(onConstructor_ = {@Autowired})
    public static class RunScheduleMonitoringManagerCore {

        private final RunScheduleManager runScheduleManager;
        private final PipelineRunManager runManager;
        private final RunConfigurationManager configurationManager;

        @SchedulerLock(name = "RunScheduleMonitoringManager_monitor", lockAtMostForString = "PT5M")
        public void monitor() {
            log.debug("Starting schedules monitoring...");
            runScheduleManager.loadAllSchedules().stream()
                    .filter(this::isIrrelevant)
                    .map(this::toSchedulableIdAndType)
                    .distinct()
                    .forEach(this::unschedule);
            log.debug("Finished schedules monitoring.");
        }

        private boolean isIrrelevant(final RunSchedule schedule) {
            switch (schedule.getType()) {
                case PIPELINE_RUN: return isIrrelevantRun(schedule);
                case RUN_CONFIGURATION: return isIrrelevantConfiguration(schedule);
                default: {
                    log.warn("Schedule for {} #{} is not supported.", schedule.getType(), schedule.getSchedulableId());
                    return false;
                }
            }
        }

        private boolean isIrrelevantRun(final RunSchedule schedule) {
            return runManager.findRun(schedule.getSchedulableId())
                    .map(PipelineRun::getStatus)
                    .map(TaskStatus::isFinal)
                    .orElse(true);
        }

        private boolean isIrrelevantConfiguration(final RunSchedule schedule) {
            return !configurationManager.find(schedule.getSchedulableId()).isPresent();
        }

        private Pair<Long, ScheduleType> toSchedulableIdAndType(final RunSchedule schedule) {
            return Pair.of(schedule.getSchedulableId(), schedule.getType());
        }

        private void unschedule(final Pair<Long, ScheduleType> schedule) {
            final Long id = schedule.getLeft();
            final ScheduleType type = schedule.getRight();
            try {
                log.debug("Unscheduling irrelevant schedules for {} #{}.", type, id);
                runScheduleManager.deleteSchedules(id, type);
            } catch (Exception e) {
                log.error(String.format("Error while unscheduling irrelevant schedules for %s #%s.", type, id), e);
            }
        }
    }
}

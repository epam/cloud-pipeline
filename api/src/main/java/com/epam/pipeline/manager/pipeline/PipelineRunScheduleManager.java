/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.dao.pipeline.PipelineRunScheduleDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.scheduling.PipelineRunScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineRunScheduleManager {

    private final PipelineRunScheduleDao runScheduleDao;
    private final PipelineRunManager pipelineRunManager;
    private final RunConfigurationManager configurationManager;
    private final PipelineRunScheduler scheduler;
    private final MessageHelper messageHelper;

    @PostConstruct
    public void init() {
        loadAllSchedules().forEach(scheduler::scheduleRunSchedule);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<RunSchedule> createSchedules(final Long schedulableId, final ScheduleType scheduleType,
                                             final List<PipelineRunScheduleVO> runScheduleVOs) {
        Assert.isTrue(checkCronsAreUnique(runScheduleVOs),
                      messageHelper.getMessage(MessageConstants.CRON_EXPRESSION_IDENTICAL));
        final List<RunSchedule> schedules = runScheduleVOs.stream()
            .peek(vo -> {
                if (scheduleType == ScheduleType.PIPELINE_RUN) {
                    PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(schedulableId);
                    checkPipelineRunNewScheduleRequirements(schedulableId, pipelineRun, vo);
                } else {
                    //check that we can load configuration
                    configurationManager.load(schedulableId);
                    checkRunConfigurationNewScheduleRequirements(schedulableId, vo);
                }
            })
            .map(vo -> {
                final RunSchedule runSchedule = new RunSchedule();
                runSchedule.setRunId(schedulableId);
                runSchedule.setType(scheduleType);
                runSchedule.setAction(vo.getAction());
                runSchedule.setCronExpression(vo.getCronExpression());
                runSchedule.setCreatedDate(DateUtils.now());
                runSchedule.setTimeZone(TimeZone.getTimeZone(vo.getTimeZone()));
                return runSchedule;
            })
            .collect(Collectors.toList());
        runScheduleDao.createRunSchedules(schedules);

        schedules.forEach(scheduler::scheduleRunSchedule);

        return schedules;
    }

    public List<RunSchedule> loadAllSchedulesBySchedulableId(final Long schedulableId, final ScheduleType scheduleType) {
        return runScheduleDao.loadAllRunSchedulesByRunIdAndType(schedulableId, scheduleType);
    }

    public List<RunSchedule> loadAllSchedules() {
        return runScheduleDao.loadAllRunSchedules();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<RunSchedule> updateSchedules(final Long schedulableId, final ScheduleType scheduleType,
                                             final List<PipelineRunScheduleVO> runScheduleVOs) {
        checkCronsAreUnique(runScheduleVOs);
        final PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(schedulableId);
        final Map<Long, RunSchedule> loadedSchedules =
            loadAllSchedulesBySchedulableId(schedulableId, scheduleType).stream()
                .collect(Collectors.toMap(RunSchedule::getId, Function.identity()));

        final List<RunSchedule> updatedSchedules = runScheduleVOs.stream()
            .map(vo -> {
                final RunSchedule runSchedule = loadedSchedules.get(vo.getScheduleId());
                verifyRunSchedule(schedulableId, pipelineRun, vo);
                final String prevCron = runSchedule.getCronExpression();
                final String newCron = vo.getCronExpression();
                if (prevCron.equals(newCron)) {
                    return null;
                } else {
                    runSchedule.setId(vo.getScheduleId());
                    runSchedule.setAction(vo.getAction());
                    runSchedule.setCronExpression(newCron);
                    runSchedule.setCreatedDate(DateUtils.now());
                    loadedSchedules.put(vo.getScheduleId(), runSchedule);
                    return runSchedule;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Assert.isTrue(checkCronsAreUnique(loadedSchedules.values()),
                      messageHelper.getMessage(MessageConstants.CRON_EXPRESSION_IDENTICAL));

        runScheduleDao.updateRunSchedules(updatedSchedules);

        updatedSchedules.forEach(runSchedule -> {
            scheduler.unscheduleRunSchedule(runSchedule);
            scheduler.scheduleRunSchedule(runSchedule);
        });

        return updatedSchedules;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<RunSchedule> deleteSchedules(final Long schedulableId, final ScheduleType scheduleType,
                                             final List<Long> scheduleIds) {
        final List<RunSchedule> schedules = scheduleIds.stream()
            .map(this::loadSchedule)
            .peek(schedule -> Assert.isTrue(schedulableId.equals(schedule.getRunId()) && schedule.getType() == scheduleType,
                    messageHelper.getMessage(MessageConstants.ERROR_RUN_ID_NOT_CORRESPONDING)))
            .peek(scheduler::unscheduleRunSchedule)
            .collect(Collectors.toList());
        runScheduleDao.deleteRunSchedules(scheduleIds);
        return schedules;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteSchedules(final Long schedulableId, final ScheduleType scheduleType) {
        runScheduleDao.deleteRunSchedulesForRunnable(schedulableId, scheduleType);
    }

    public RunSchedule loadSchedule(final Long id) {
        return runScheduleDao.loadRunSchedule(id).orElseThrow(() -> new IllegalArgumentException(
            messageHelper.getMessage(MessageConstants.ERROR_RUN_SCHEDULE_NOT_FOUND, id)));
    }

    private void checkPipelineRunNewScheduleRequirements(final Long runId, final PipelineRun pipelineRun,
                                                         final PipelineRunScheduleVO runScheduleVO) {
        checkIdenticalCronExpressionForRun(runId, ScheduleType.PIPELINE_RUN, runScheduleVO);
        verifyRunSchedule(runId, pipelineRun, runScheduleVO);
    }

    private void checkRunConfigurationNewScheduleRequirements(final Long configurationId,
                                                              final PipelineRunScheduleVO runScheduleVO) {
        checkIdenticalCronExpressionForRun(configurationId, ScheduleType.RUN_CONFIGURATION, runScheduleVO);
        verifyCronExpression(configurationId, runScheduleVO);
        Assert.notNull(runScheduleVO.getAction(),
                messageHelper.getMessage(MessageConstants.SCHEDULE_ACTION_IS_NOT_PROVIDED, configurationId));
        Assert.isTrue(StringUtils.hasText(runScheduleVO.getTimeZone()),
                messageHelper.getMessage(MessageConstants.ERROR_TIME_ZONE_IS_NOT_PROVIDED, configurationId));
    }

    private void verifyRunSchedule(final Long runId, final PipelineRun pipelineRun,
                                   final PipelineRunScheduleVO runScheduleVO) {
        verifyCronExpression(runId, runScheduleVO);
        Assert.notNull(runScheduleVO.getAction(),
                       messageHelper.getMessage(MessageConstants.SCHEDULE_ACTION_IS_NOT_PROVIDED, runId));
        Assert.notNull(pipelineRun,
                       messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, pipelineRun.getName()));
        Assert.isTrue(!pipelineRun.getInstance().getSpot(), messageHelper.getMessage(
            MessageConstants.ERROR_ON_DEMAND_REQUIRED));
        Assert.isTrue(!pipelineRun.getStatus().isFinal(), messageHelper.getMessage(
            MessageConstants.ERROR_PIPELINE_RUN_FINISHED, runId));
        Assert.isTrue(StringUtils.hasText(runScheduleVO.getTimeZone()),
                      messageHelper.getMessage(MessageConstants.ERROR_TIME_ZONE_IS_NOT_PROVIDED, runId));
        Assert.isTrue(!(runScheduleVO.getAction().equals(RunScheduledAction.PAUSE)
                        && isNonPauseOrClusterRun(pipelineRun)),
                      messageHelper.getMessage(MessageConstants.DEBUG_RUN_IDLE_SKIP_CHECK));
    }

    private boolean isNonPauseOrClusterRun(final PipelineRun pipelineRun) {
        return pipelineRun.isClusterRun()
               || pipelineRun.isNonPause();
    }

    private void verifyCronExpression(final Long runId, final PipelineRunScheduleVO runScheduleVO) {
        Assert.notNull(runScheduleVO.getCronExpression(),
                       messageHelper.getMessage(MessageConstants.CRON_EXPRESSION_IS_NOT_PROVIDED, runId));
        Assert.isTrue(CronExpression.isValidExpression(runScheduleVO.getCronExpression()),
                      messageHelper.getMessage(MessageConstants.CRON_EXPRESSION_IS_NOT_VALID, runId));
    }

    private void checkIdenticalCronExpressionForRun(final Long runId, final ScheduleType type,
                                                    final PipelineRunScheduleVO runScheduleVO) {
        final List<RunSchedule> runSchedules = runScheduleDao.loadAllRunSchedulesByRunIdAndType(runId, type);
        final Optional<RunSchedule> identicalCronExpressionForRun = runSchedules.stream()
            .filter(runSchedule -> runSchedule.getTimeZone().equals(TimeZone.getTimeZone(runScheduleVO.getTimeZone()))
                                   && runSchedule.getCronExpression().equals(runScheduleVO.getCronExpression()))
            .findFirst();
        Assert.isTrue(!identicalCronExpressionForRun.isPresent(),
                      messageHelper.getMessage(MessageConstants.CRON_EXPRESSION_ALREADY_EXISTS,
                                               runScheduleVO.getCronExpression(),
                                               runId));
    }

    private boolean checkCronsAreUnique(final List<PipelineRunScheduleVO> runScheduleVOs) {
        final long uniqueCrons = runScheduleVOs.stream()
            .filter(distinctByKey(PipelineRunScheduleVO::getCronExpression))
            .count();
        return uniqueCrons == runScheduleVOs.size();
    }

    private boolean checkCronsAreUnique(final Collection<RunSchedule> schedules) {
        final long uniqueCrons = schedules.stream()
            .filter(distinctByKey(RunSchedule::getCronExpression))
            .count();
        return uniqueCrons == schedules.size();
    }

    private static <T> Predicate<T> distinctByKey(final Function<? super T, Object> keyExtractor) {
        final Map<Object, Boolean> map = new ConcurrentHashMap<>();
        return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}

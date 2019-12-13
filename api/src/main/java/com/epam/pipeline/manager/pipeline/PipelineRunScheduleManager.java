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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.dao.pipeline.PipelineRunScheduleDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.scheduling.PipelineRunScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import javax.annotation.PostConstruct;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineRunScheduleManager {

    private final PipelineRunScheduleDao runScheduleDao;
    private final PipelineRunManager pipelineRunManager;
    private final PipelineRunScheduler scheduler;
    private final MessageHelper messageHelper;

    @PostConstruct
    public void init() {
        loadAllRunSchedules().forEach(scheduler::scheduleRunSchedule);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public RunSchedule createRunSchedule(final Long runId, final PipelineRunScheduleVO runScheduleVO) {
        checkScheduleRequirements(runId, runScheduleVO);
        final RunSchedule runSchedule = new RunSchedule();
        runSchedule.setRunId(runId);
        runSchedule.setAction(runScheduleVO.getAction());
        runSchedule.setCronExpression(runScheduleVO.getCronExpression());
        runSchedule.setCreatedDate(DateUtils.now());
        runSchedule.setTimeZone(TimeZone.getTimeZone(runScheduleVO.getTimeZone()));
        runScheduleDao.createRunSchedule(runSchedule);

        scheduler.scheduleRunSchedule(runSchedule);

        return runSchedule;
    }

    public List<RunSchedule> loadAllRunSchedulesByRunId(final Long runId) {
        return runScheduleDao.loadAllRunSchedulesByRunId(runId);
    }

    public List<RunSchedule> loadAllRunSchedules() {
        return runScheduleDao.loadAllRunSchedules();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public RunSchedule updateRunSchedule(final Long runId, final PipelineRunScheduleVO runScheduleVO) {
        final RunSchedule runSchedule = loadRunSchedule(runScheduleVO.getScheduleId());
        Assert.isTrue(runId.equals(runSchedule.getRunId()),
                      messageHelper.getMessage(MessageConstants.ERROR_RUN_ID_NOT_CORRESPONDING));
        checkScheduleRequirements(runSchedule.getRunId(), runScheduleVO);
        runSchedule.setId(runScheduleVO.getScheduleId());
        runSchedule.setAction(runScheduleVO.getAction());
        runSchedule.setCronExpression(runScheduleVO.getCronExpression());
        runSchedule.setCreatedDate(DateUtils.now());
        runScheduleDao.updateRunSchedule(runSchedule);
        scheduler.unscheduleRunSchedule(runSchedule);
        scheduler.scheduleRunSchedule(runSchedule);

        return runSchedule;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public RunSchedule deleteRunSchedule(final Long runId, final Long scheduleId) {
        final RunSchedule runSchedule = loadRunSchedule(scheduleId);
        Assert.isTrue(runId.equals(runSchedule.getRunId()),
                      messageHelper.getMessage(MessageConstants.ERROR_RUN_ID_NOT_CORRESPONDING));
        scheduler.unscheduleRunSchedule(runSchedule);
        runScheduleDao.deleteRunSchedule(scheduleId);
        return runSchedule;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteRunSchedulesForRun(final Long runId) {
        runScheduleDao.deleteRunSchedulesForRun(runId);
    }

    public RunSchedule loadRunSchedule(final Long id) {
        return runScheduleDao.loadRunSchedule(id).orElseThrow(() -> new IllegalArgumentException(
            messageHelper.getMessage(MessageConstants.ERROR_RUN_SCHEDULE_NOT_FOUND, id)));
    }

    private void checkScheduleRequirements(final Long runId, final PipelineRunScheduleVO runScheduleVO) {
        verifyCronExpression(runId, runScheduleVO);
        checkIdenticalCronExpressionForRun(runId, runScheduleVO);
        Assert.notNull(runScheduleVO.getAction(),
                       messageHelper.getMessage(MessageConstants.SCHEDULE_ACTION_IS_NOT_PROVIDED, runId));
        final PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                       messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, pipelineRun.getName()));
        Assert.isTrue(!pipelineRun.getInstance().getSpot(), messageHelper.getMessage(
            MessageConstants.ERROR_ON_DEMAND_REQUIRED));
        Assert.isTrue(!pipelineRun.getStatus().isFinal(), messageHelper.getMessage(
            MessageConstants.ERROR_PIPELINE_RUN_FINISHED, runId));
        Assert.isTrue(StringUtils.hasText(runScheduleVO.getTimeZone()),
                      messageHelper.getMessage(MessageConstants.ERROR_TIME_ZONE_IS_NOT_PROVIDED, runId));
        Assert.isTrue(runScheduleVO.getAction().equals(RunScheduledAction.PAUSE)
                      && !isNonPauseOrClusterRun(pipelineRun),
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

    private void checkIdenticalCronExpressionForRun(final Long runId, final PipelineRunScheduleVO runScheduleVO) {
        final List<RunSchedule> runSchedules = runScheduleDao.loadAllRunSchedulesByRunId(runId);
        final Optional<RunSchedule> identicalCronExpressionForRun = runSchedules.stream()
            .filter(runSchedule -> runSchedule.getTimeZone().equals(TimeZone.getTimeZone(runScheduleVO.getTimeZone()))
                                   && runSchedule.getCronExpression().equals(runScheduleVO.getCronExpression()))
            .findFirst();
        Assert.isTrue(!identicalCronExpressionForRun.isPresent(),
                      messageHelper.getMessage(MessageConstants.CRON_EXPRESSION_ALREADY_EXISTS, runId));
    }
}

/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dao.pipeline.EngineRunTaskDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.EngineRunTask;
import com.epam.pipeline.entity.pipeline.run.EngineRunTaskFilter;
import com.epam.pipeline.entity.pipeline.run.EngineType;
import com.epam.pipeline.entity.pipeline.run.PipelineRunWithEngineTasks;
import com.epam.pipeline.entity.run.EngineRunTaskGroupStatsEntity;
import com.epam.pipeline.entity.run.EngineRunTaskStatsEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EngineRunTaskService {

    private final PipelineRunCRUDService runCRUDService;
    private final EngineRunTaskDao engineRunTaskDao;
    private final MessageHelper messageHelper;

    @Transactional(propagation = Propagation.REQUIRED)
    public int upsertTasks(final Long runId, final List<EngineRunTask> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return 0;
        }
        runCRUDService.loadRunById(runId);
        return engineRunTaskDao.batchUpsert(tasks.stream()
                        .peek(task -> task.setRunId(runId))
                        .map(this::validateEvent)
                        .collect(Collectors.toList()))
                .size();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void resetTasks(final Long runId) {
        runCRUDService.loadRunById(runId);
        engineRunTaskDao.deleteByRunIdIn(Collections.singletonList(runId), false);
    }

    public Map<String, EngineRunTaskGroupStatsEntity> loadTasksStats(final Long runId, final EngineType engineType) {
        runCRUDService.loadRunById(runId);
        return calculateTaskGroupStatistic(engineRunTaskDao.loadStats(runId, engineType));
    }

    public PagedResult<List<EngineRunTask>> loadTasks(final Long runId, final EngineType engineType,
                                                      final EngineRunTaskFilter filter) {
        Assert.isTrue(filter.getPage() > 0, messageHelper.getMessage(MessageConstants.ERROR_PAGE_INDEX));
        Assert.isTrue(filter.getPageSize() > 0, messageHelper.getMessage(MessageConstants.ERROR_PAGE_SIZE));

        runCRUDService.loadRunById(runId);
        return new PagedResult<>(engineRunTaskDao.filterTasksByRunIdAndTypeAndFilter(runId, engineType, filter),
                engineRunTaskDao.countTasksByRunIdAndTypeAndFilter(runId, engineType, filter));
    }

    public List<EngineRunTask> loadTasks(final EngineType engineType, final List<String> taskKeys) {
        Assert.notEmpty(taskKeys, "List of task keys should be provided!");
        return engineRunTaskDao.loadEngineTasksByTaskKeys(engineType, taskKeys);
    }

    public List<PipelineRunWithEngineTasks> loadRunInfoByTasks(final EngineType engineType,
                                                               final List<String> taskKeys) {
        final List<EngineRunTask> engineRunTasks = loadTasks(engineType, taskKeys);
        final Map<Long, List<EngineRunTask>> tasksGroupedByRun =
                engineRunTasks.stream().collect(Collectors.groupingBy(EngineRunTask::getRunId));
        final List<PipelineRun> pipelineRuns = runCRUDService.loadRunsByIds(
                new ArrayList<>(tasksGroupedByRun.keySet())
        );
        return pipelineRuns.stream()
                .map(pipelineRun ->
                    Pair.create(
                        pipelineRun,
                        tasksGroupedByRun.getOrDefault(pipelineRun.getId(), Collections.emptyList())
                    )
                ).map(p ->
                    PipelineRunWithEngineTasks.builder()
                        .run(p.getKey())
                        .engineTaskKeys(
                                p.getValue().stream().map(EngineRunTask::getTaskKey).collect(Collectors.toList())
                        ).build()
                ).collect(Collectors.toList());
    }

    protected static Map<String, EngineRunTaskGroupStatsEntity> calculateTaskGroupStatistic(
            final List<EngineRunTaskStatsEntity> taskGroupStatistics) {
        final Map<String, EngineRunTaskGroupStatsEntity> result = new HashMap<>();
        for (EngineRunTaskStatsEntity taskGroupStatisticForStatus : taskGroupStatistics) {
            final Date taskGroupStartDateForStatus = taskGroupStatisticForStatus.getStartDateTime();
            final EngineRunTaskGroupStatsEntity currentTaskGroupStatistic = result.computeIfAbsent(
                taskGroupStatisticForStatus.getTaskGroup(),
                (taskGroup) -> new EngineRunTaskGroupStatsEntity(
                        taskGroup,
                        taskGroupStartDateForStatus,
                        new HashMap<>()
                )
            );
            currentTaskGroupStatistic.getStatusCounts()
                    .put(taskGroupStatisticForStatus.getStatus(), taskGroupStatisticForStatus.getTasksCount());
            final boolean startDateShouldBeUpdated = taskGroupStartDateForStatus != null &&
                    (currentTaskGroupStatistic.getStartDateTime() == null
                            || taskGroupStartDateForStatus.compareTo(currentTaskGroupStatistic.getStartDateTime()) < 0
                    );
            if (startDateShouldBeUpdated) {
                currentTaskGroupStatistic.setStartDateTime(taskGroupStartDateForStatus);
            }
        }
        return result;
    }

    private EngineRunTask validateEvent(final EngineRunTask task) {
        Assert.notNull(task.getTaskId(), messageHelper.getMessage(
                MessageConstants.ERROR_ENGINE_RUN_TASK_SETTING_NOT_FOUND, "taskId"));
        Assert.notNull(task.getEngineType(), messageHelper.getMessage(
                MessageConstants.ERROR_ENGINE_RUN_TASK_SETTING_NOT_FOUND, "engineType"));
        Assert.notNull(task.getStatus(), messageHelper.getMessage(
                MessageConstants.ERROR_ENGINE_RUN_TASK_SETTING_NOT_FOUND, "status"));
        return task;
    }
}

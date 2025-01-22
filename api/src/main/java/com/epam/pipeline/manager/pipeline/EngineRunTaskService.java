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
import com.epam.pipeline.dao.pipeline.EngineRunTaskDao;
import com.epam.pipeline.entity.pipeline.run.EngineRunTask;
import com.epam.pipeline.entity.pipeline.run.EngineRunTaskStatsEntity;
import com.epam.pipeline.entity.pipeline.run.EngineTaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                        .map(this::validate)
                        .collect(Collectors.toList()))
                .size();
    }

    public Map<EngineType, Map<String, Map<EngineTaskStatus, Long>>> loadTasksStats(final Long runId) {
        runCRUDService.loadRunById(runId);
        return ListUtils.emptyIfNull(engineRunTaskDao.loadStats(runId)).stream()
                .filter(stats -> Objects.nonNull(stats.getTaskGroup()))
                .collect(Collectors.groupingBy(EngineRunTaskStatsEntity::getEngineType,
                        Collectors.groupingBy(EngineRunTaskStatsEntity::getTaskGroup,
                                Collectors.toMap(EngineRunTaskStatsEntity::getStatus,
                                        EngineRunTaskStatsEntity::getTasksCount))));
    }

    private EngineRunTask validate(final EngineRunTask task) {
        Assert.notNull(task.getTaskId(), messageHelper.getMessage(
                MessageConstants.ERROR_ENGINE_RUN_TASK_SETTING_NOT_FOUND, "taskId"));
        Assert.notNull(task.getEngineType(), messageHelper.getMessage(
                MessageConstants.ERROR_ENGINE_RUN_TASK_SETTING_NOT_FOUND, "engineType"));
        Assert.notNull(task.getStatus(), messageHelper.getMessage(
                MessageConstants.ERROR_ENGINE_RUN_TASK_SETTING_NOT_FOUND, "status"));
        return task;
    }
}

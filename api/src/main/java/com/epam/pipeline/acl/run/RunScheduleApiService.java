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

package com.epam.pipeline.acl.run;

import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_EXECUTE;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_READ;

import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.manager.pipeline.PipelineRunScheduleManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RunScheduleApiService {

    private final PipelineRunScheduleManager runScheduleManager;

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public List<RunSchedule> createRunSchedules(final Long runId, final List<PipelineRunScheduleVO> runScheduleVOs) {
        return runScheduleManager.createRunSchedules(runId, runScheduleVOs);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public List<RunSchedule> updateRunSchedules(final Long runId, final List<PipelineRunScheduleVO> schedules) {
        return runScheduleManager.updateRunSchedules(runId, schedules);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public List<RunSchedule> deleteRunSchedule(final Long runId, final List<PipelineRunScheduleVO> schedules) {
        final List<Long> scheduleIds =
            schedules.stream().map(PipelineRunScheduleVO::getScheduleId).collect(Collectors.toList());
        return runScheduleManager.deleteRunSchedules(runId, scheduleIds);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public void deleteAllRunSchedules(final Long runId) {
        runScheduleManager.deleteRunSchedulesForRun(runId);
    }

    @PreAuthorize(RUN_ID_READ)
    public List<RunSchedule> loadAllRunSchedulesByRunId(final Long runId) {
        return runScheduleManager.loadAllRunSchedulesByRunId(runId);
    }
}

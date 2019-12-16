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

@Service
@RequiredArgsConstructor
public class RunScheduleApiService {

    private final PipelineRunScheduleManager runScheduleManager;

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public RunSchedule createRunSchedule(final Long runId, final PipelineRunScheduleVO runScheduleVO) {
        return runScheduleManager.createRunSchedule(runId, runScheduleVO);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public RunSchedule updateRunSchedule(final Long runId, final PipelineRunScheduleVO schedule) {
        return runScheduleManager.updateRunSchedule(runId, schedule);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public RunSchedule deleteRunSchedule(final Long runId, final Long scheduleId) {
        return runScheduleManager.deleteRunSchedule(runId, scheduleId);
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

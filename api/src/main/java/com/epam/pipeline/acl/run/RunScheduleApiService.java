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

package com.epam.pipeline.acl.run;

import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_EXECUTE;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_READ;

import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.manager.pipeline.PipelineRunScheduleManager;
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
    public List<RunSchedule> createRunSchedules(final Long runId, final List<PipelineRunScheduleVO> runScheduleVOs) {
        return runScheduleManager.createSchedules(runId, ScheduleType.PIPELINE_RUN, runScheduleVOs);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'EXECUTE')")
    public List<RunSchedule> createRunConfigurationSchedules(final Long configurationId,
                                                             final List<PipelineRunScheduleVO> runScheduleVOs) {
        return runScheduleManager.createSchedules(configurationId, ScheduleType.RUN_CONFIGURATION, runScheduleVOs);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    public List<RunSchedule> updateRunSchedules(final Long runId, final List<PipelineRunScheduleVO> schedules) {
        return runScheduleManager.updateSchedules(runId, ScheduleType.PIPELINE_RUN, schedules);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'EXECUTE')")
    public List<RunSchedule> updateRunConfigurationSchedules(final Long configurationId,
                                                             final List<PipelineRunScheduleVO> schedules) {
        return runScheduleManager.updateSchedules(configurationId, ScheduleType.RUN_CONFIGURATION, schedules);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    public List<RunSchedule> deleteRunSchedule(final Long runId, final List<PipelineRunScheduleVO> schedules) {
        final List<Long> scheduleIds =
            schedules.stream().map(PipelineRunScheduleVO::getScheduleId).collect(Collectors.toList());
        return runScheduleManager.deleteSchedules(runId, ScheduleType.PIPELINE_RUN, scheduleIds);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'EXECUTE')")
    public List<RunSchedule> deleteRunConfigurationSchedule(final Long configurationId,
                                                            final List<PipelineRunScheduleVO> schedules) {
        final List<Long> scheduleIds =
                schedules.stream().map(PipelineRunScheduleVO::getScheduleId).collect(Collectors.toList());
        return runScheduleManager.deleteSchedules(configurationId, ScheduleType.RUN_CONFIGURATION, scheduleIds);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    public void deleteAllRunSchedules(final Long runId) {
        runScheduleManager.deleteSchedules(runId, ScheduleType.PIPELINE_RUN);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'EXECUTE')")
    public void deleteAllRunConfigurationSchedules(final Long configurationId) {
        runScheduleManager.deleteSchedules(configurationId, ScheduleType.RUN_CONFIGURATION);
    }

    @PreAuthorize(RUN_ID_READ)
    public List<RunSchedule> loadAllRunSchedulesByRunId(final Long runId) {
        return runScheduleManager.loadAllSchedulesBySchedulableId(runId, ScheduleType.PIPELINE_RUN);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'READ')")
    public List<RunSchedule> loadAllRunConfigurationSchedulesByConfigurationId(final Long runId) {
        return runScheduleManager.loadAllSchedulesBySchedulableId(runId, ScheduleType.RUN_CONFIGURATION);
    }
}

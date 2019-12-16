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

package com.epam.pipeline.controller.pipeline;

import com.epam.pipeline.acl.run.RunScheduleApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Api(value = "Pipeline run scheduling")
@RequestMapping(value = "/schedule/run")
@RequiredArgsConstructor
public class PipelineRunScheduleController extends AbstractRestController {

    private final RunScheduleApiService runScheduleApiService;

    private static final String RUN_ID = "runId";

    @PostMapping(value = "/{runId}/create")
    @ResponseBody
    @ApiOperation(
        value = "Creates pipeline run schedule.",
        notes = "Creates pipeline run schedule.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<RunSchedule> createRunSchedule(@PathVariable(value = RUN_ID) final Long runId,
                                                 @RequestBody final PipelineRunScheduleVO schedule) {
        return Result.success(runScheduleApiService.createRunSchedule(runId, schedule));
    }

    @PutMapping(value = "/{runId}/update")
    @ResponseBody
    @ApiOperation(
        value = "Updates pipeline run schedule.",
        notes = "Updates pipeline run schedule.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<RunSchedule> updateRunSchedule(@PathVariable(value = RUN_ID) final Long runId,
                                                 @RequestBody final PipelineRunScheduleVO schedule) {
        return Result.success(runScheduleApiService.updateRunSchedule(runId, schedule));
    }

    @GetMapping(value = "/{runId}/loadAll")
    @ResponseBody
    @ApiOperation(
        value = "Loads all schedules for a given pipeline run.",
        notes = "Loads all schedules for a given pipeline run.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> loadAllRunSchedules(@PathVariable(value = RUN_ID) final Long runId) {
        return Result.success(runScheduleApiService.loadAllRunSchedulesByRunId(runId));
    }

    @DeleteMapping(value = "/{runId}/delete")
    @ResponseBody
    @ApiOperation(
        value = "Deletes given pipeline run schedule.",
        notes = "Deletes given pipeline run schedule.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<RunSchedule> deleteRunSchedule(@PathVariable(value = RUN_ID) final Long runId,
                                                 @RequestBody final PipelineRunScheduleVO schedule) {
        return Result.success(runScheduleApiService.deleteRunSchedule(runId, schedule.getScheduleId()));
    }

    @DeleteMapping(value = "/{runId}/deleteAll")
    @ResponseBody
    @ApiOperation(
        value = "Deletes all pipeline run's schedules.",
        notes = "Deletes all pipeline run's schedules.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public void deleteAllRunSchedules(@PathVariable(value = RUN_ID) final Long runId) {
        runScheduleApiService.deleteAllRunSchedules(runId);
    }
}

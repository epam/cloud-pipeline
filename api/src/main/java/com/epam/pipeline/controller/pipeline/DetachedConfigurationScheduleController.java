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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@Api(value = "Detached configuration scheduling")
@RequestMapping(value = "/schedule/configuration")
@RequiredArgsConstructor
public class DetachedConfigurationScheduleController extends AbstractRestController {

    private static final String CONFIGURATION_ID_PATH = "/{id}";
    private static final String ID = "id";

    private final RunScheduleApiService runScheduleApiService;

    @PostMapping(value = CONFIGURATION_ID_PATH)
    @ResponseBody
    @ApiOperation(
        value = "Creates detached configuration schedules.",
        notes = "Creates detached configuration schedules.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> createRunSchedule(@PathVariable(value = ID) final Long configurationId,
                                                       @RequestBody final List<PipelineRunScheduleVO> schedules) {
        return Result.success(runScheduleApiService.createRunConfigurationSchedules(configurationId, schedules));
    }

    @PutMapping(value = CONFIGURATION_ID_PATH)
    @ResponseBody
    @ApiOperation(
        value = "Updates detached configuration schedules.",
        notes = "Updates detached configuration schedules.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> updateRunSchedule(@PathVariable(value = ID) final Long configurationId,
                                                       @RequestBody final List<PipelineRunScheduleVO> schedules) {
        return Result.success(runScheduleApiService.updateRunConfigurationSchedules(configurationId, schedules));
    }

    @GetMapping(value = CONFIGURATION_ID_PATH)
    @ResponseBody
    @ApiOperation(
        value = "Loads all schedules for a given detached configuration.",
        notes = "Loads all schedules for a given detached configuration.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> loadAllRunSchedules(@PathVariable(value = ID) final Long configurationId) {
        return Result.success(runScheduleApiService.loadAllRunConfigurationSchedulesByConfigurationId(configurationId));
    }

    @DeleteMapping(value = CONFIGURATION_ID_PATH)
    @ResponseBody
    @ApiOperation(
        value = "Deletes given detached configuration.",
        notes = "Deletes given detached configuration.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> deleteRunSchedule(@PathVariable(value = ID) final Long configurationId,
                                                       @RequestBody final List<PipelineRunScheduleVO> schedules) {
        return Result.success(runScheduleApiService.deleteRunConfigurationSchedule(configurationId, schedules));
    }

    @DeleteMapping(value = CONFIGURATION_ID_PATH + "/all")
    @ResponseBody
    @ApiOperation(
        value = "Deletes all pipeline run's schedules.",
        notes = "Deletes all pipeline run's schedules.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public void deleteAllRunSchedules(@PathVariable(value = ID) final Long configurationId) {
        runScheduleApiService.deleteAllRunConfigurationSchedules(configurationId);
    }
}

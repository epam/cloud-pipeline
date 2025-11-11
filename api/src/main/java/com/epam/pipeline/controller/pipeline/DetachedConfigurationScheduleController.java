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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

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
@Tag(name = "Detached configuration scheduling")
@RequestMapping(value = "/schedule/configuration")
@RequiredArgsConstructor
public class DetachedConfigurationScheduleController extends AbstractRestController {

    private static final String CONFIGURATION_ID_PATH = "/{id}";
    private static final String ID = "id";

    private final RunScheduleApiService runScheduleApiService;

    @PostMapping(value = CONFIGURATION_ID_PATH)
    @ResponseBody
    @Operation(
        summary = "Creates detached configuration schedules.",
        description = "Creates detached configuration schedules.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> createRunSchedule(@PathVariable(value = ID) final Long configurationId,
                                                       @RequestBody final List<PipelineRunScheduleVO> schedules) {
        return Result.success(runScheduleApiService.createRunConfigurationSchedules(configurationId, schedules));
    }

    @PutMapping(value = CONFIGURATION_ID_PATH)
    @ResponseBody
    @Operation(
        summary = "Updates detached configuration schedules.",
        description = "Updates detached configuration schedules.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> updateRunSchedule(@PathVariable(value = ID) final Long configurationId,
                                                       @RequestBody final List<PipelineRunScheduleVO> schedules) {
        return Result.success(runScheduleApiService.updateRunConfigurationSchedules(configurationId, schedules));
    }

    @GetMapping(value = CONFIGURATION_ID_PATH)
    @ResponseBody
    @Operation(
        summary = "Loads all schedules for a given detached configuration.",
        description = "Loads all schedules for a given detached configuration.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> loadAllRunSchedules(@PathVariable(value = ID) final Long configurationId) {
        return Result.success(runScheduleApiService.loadAllRunConfigurationSchedulesByConfigurationId(configurationId));
    }

    @DeleteMapping(value = CONFIGURATION_ID_PATH)
    @ResponseBody
    @Operation(
        summary = "Deletes given schedules of detached configuration.",
        description = "Deletes given schedules of detached configuration.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<RunSchedule>> deleteRunSchedule(@PathVariable(value = ID) final Long configurationId,
                                                       @RequestBody final List<PipelineRunScheduleVO> schedules) {
        return Result.success(runScheduleApiService.deleteRunConfigurationSchedule(configurationId, schedules));
    }

    @DeleteMapping(value = CONFIGURATION_ID_PATH + "/all")
    @ResponseBody
    @Operation(
        summary = "Deletes all pipeline configuration's schedules.",
        description = "Deletes all pipeline configuration's schedules.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public void deleteAllRunSchedules(@PathVariable(value = ID) final Long configurationId) {
        runScheduleApiService.deleteAllRunConfigurationSchedules(configurationId);
    }
}

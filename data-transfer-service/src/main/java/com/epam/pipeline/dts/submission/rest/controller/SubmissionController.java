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

package com.epam.pipeline.dts.submission.rest.controller;

import com.epam.pipeline.dts.common.rest.Result;
import com.epam.pipeline.dts.common.rest.controller.AbstractRestController;
import com.epam.pipeline.dts.submission.model.execution.Submission;
import com.epam.pipeline.dts.submission.rest.dto.SubmissionDTO;
import com.epam.pipeline.dts.submission.rest.mapper.SubmissionMapper;
import com.epam.pipeline.dts.submission.service.execution.SubmissionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Collection;
import java.util.stream.Collectors;

import static com.epam.pipeline.dts.common.rest.controller.AbstractRestController.API_STATUS_DESCRIPTION;
import static com.epam.pipeline.dts.common.rest.controller.AbstractRestController.HTTP_STATUS_OK;

@RequestMapping("submission")
@Api(value = "SGE jobs management")
@ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
@RestController
@RequiredArgsConstructor
public class SubmissionController extends AbstractRestController {

    private final SubmissionService submissionService;
    private final SubmissionMapper submissionMapper;

    @PostMapping
    @ApiOperation(
            value = "Schedules new SGE job.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<SubmissionDTO> create(@RequestBody final SubmissionDTO submissionDTO) {
        Submission submission = submissionService.create(submissionMapper.dtoToModel(submissionDTO));
        return Result.success(submissionMapper.modelToDTO(submission));
    }

    @GetMapping(path = "/{submissionId}")
    @ApiOperation(
            value = "Loads existing submission specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<SubmissionDTO> load(@PathVariable final Long submissionId) {
        Submission submission = submissionService.load(submissionId);
        return Result.success(submissionMapper.modelToDTO(submission));
    }

    @PutMapping(path = "/stop", params = {"runId"})
    @ApiOperation(
            value = "Stops execution of submission specified by runID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<SubmissionDTO> stop(@RequestParam final Long runId) {
        Submission submission = submissionService.stop(runId);
        return Result.success(submissionMapper.modelToDTO(submission));
    }

    @GetMapping(params = {"runId"})
    @ApiOperation(
            value = "Loads existing submission specified by external runID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<SubmissionDTO> loadByRunId(@RequestParam final Long runId) {
        Submission submission = submissionService.loadByRunId(runId);
        return Result.success(submissionMapper.modelToDTO(submission));
    }

    @GetMapping
    @ApiOperation(
            value = "Loads all active submissions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<Collection<SubmissionDTO>> loadAllActive() {
        return Result.success(submissionService.loadActive().stream()
                .map(submissionMapper::modelToDTO)
                .collect(Collectors.toList()));
    }

}

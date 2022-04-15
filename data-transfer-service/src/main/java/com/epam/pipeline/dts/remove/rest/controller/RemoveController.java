/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.remove.rest.controller;

import com.epam.pipeline.dts.common.rest.Result;
import com.epam.pipeline.dts.common.rest.controller.AbstractRestController;
import com.epam.pipeline.dts.remove.model.RemoveTask;
import com.epam.pipeline.dts.remove.rest.dto.RemoveDTO;
import com.epam.pipeline.dts.remove.rest.dto.RemoveTaskCreationDTO;
import com.epam.pipeline.dts.remove.rest.mapper.RemoveTaskMapper;
import com.epam.pipeline.dts.remove.service.RemoveService;
import com.epam.pipeline.dts.remove.service.RemoveTaskService;
import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.rest.mapper.StorageItemMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

import static com.epam.pipeline.dts.common.rest.controller.AbstractRestController.API_STATUS_DESCRIPTION;
import static com.epam.pipeline.dts.common.rest.controller.AbstractRestController.HTTP_STATUS_OK;

@RequestMapping("remove")
@Api(value = "Remove tasks management")
@ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
@RestController
@RequiredArgsConstructor
public class RemoveController extends AbstractRestController {

    private final RemoveTaskService taskService;
    private final RemoveService service;
    private final StorageItemMapper storageItemMapper;
    private final RemoveTaskMapper taskMapper;

    @PostMapping
    @ApiOperation(
            value = "Creates and schedules a new remove task.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<RemoveDTO> createTask(@RequestBody RemoveTaskCreationDTO taskCreationDTO) {
        RemoveTask task = service.schedule(
                storageItemMapper.dtoToModel(taskCreationDTO.getTarget()),
                taskCreationDTO.getIncluded());
        return Result.success(taskMapper.modelToDto(task));
    }

    @PutMapping(path = "/{taskId}")
    @ApiOperation(
            value = "Updates status of existing task.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<RemoveDTO> updateStatus(@PathVariable Long taskId,
                                            @RequestParam TaskStatus status,
                                            @RequestParam(required = false) String reason) {
        RemoveTask task = taskService.updateStatus(taskId, status, reason);
        return Result.success(taskMapper.modelToDto(task));
    }

    @GetMapping(path = "/{taskId}")
    @ApiOperation(
            value = "Returns an existing task by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<RemoveDTO> getTask(@PathVariable Long taskId) {
        return Result.success(taskMapper.modelToDto(taskService.load(taskId)));
    }

    @DeleteMapping(path = "/{taskId}")
    @ApiOperation(
            value = "Deletes an existing task by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result deleteTask(@PathVariable Long taskId) {
        taskService.delete(taskId);
        return Result.success(null);
    }

    @GetMapping
    @ApiOperation(
            value = "Returns all tasks.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<List<RemoveDTO>> getAll() {
        return Result.success(taskService
                .loadAll()
                .stream()
                .map(taskMapper::modelToDto)
                .collect(Collectors.toList()));
    }
}

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

package com.epam.pipeline.dts.deletion.rest.controller;

import com.epam.pipeline.dts.common.rest.Result;
import com.epam.pipeline.dts.common.rest.controller.AbstractRestController;
import com.epam.pipeline.dts.deletion.rest.dto.DeletionTaskCreationDTO;
import com.epam.pipeline.dts.deletion.rest.dto.DeletionDTO;
import com.epam.pipeline.dts.deletion.rest.mapper.DeletionTaskMapper;
import com.epam.pipeline.dts.deletion.service.DeletionService;
import com.epam.pipeline.dts.deletion.service.DeletionTaskService;
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

@RequestMapping("delete")
@Api(value = "Deletion tasks management")
@ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
@RestController
@RequiredArgsConstructor
public class DeletionController extends AbstractRestController {

    private final DeletionTaskService taskService;
    private final DeletionService service;
    private final StorageItemMapper storageItemMapper;
    private final DeletionTaskMapper taskMapper;

    @PostMapping
    @ApiOperation(value = "Creates and schedules new deletion task.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<DeletionDTO> create(@RequestBody DeletionTaskCreationDTO taskCreationDTO) {
        return Result.success(taskMapper.modelToDto(service.schedule(
                storageItemMapper.dtoToModel(taskCreationDTO.getTarget()),
                taskCreationDTO.getScheduled(),
                taskCreationDTO.getIncluded())));
    }

    @GetMapping(path = "/{taskId}")
    @ApiOperation(value = "Returns an existing deletion task by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<DeletionDTO> get(@PathVariable Long taskId) {
        return Result.success(taskMapper.modelToDto(taskService.load(taskId)));
    }

    @GetMapping
    @ApiOperation(value = "Returns all deletion tasks.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<List<DeletionDTO>> getAll() {
        return Result.success(taskService
                .loadAll()
                .stream()
                .map(taskMapper::modelToDto)
                .collect(Collectors.toList()));
    }

    @PutMapping(path = "/{taskId}")
    @ApiOperation(value = "Updates status of an existing deletion task.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<DeletionDTO> updateStatus(@PathVariable Long taskId,
                                            @RequestParam TaskStatus status,
                                            @RequestParam(required = false) String reason) {
        return Result.success(taskMapper.modelToDto(taskService.updateStatus(taskId, status, reason)));
    }

    @DeleteMapping(path = "/{taskId}")
    @ApiOperation(value = "Deletes an existing deletion task by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result delete(@PathVariable Long taskId) {
        taskService.delete(taskId);
        return Result.success(null);
    }
}

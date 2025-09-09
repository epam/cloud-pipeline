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

package com.epam.pipeline.dts.transfer.rest.controller;

import com.epam.pipeline.dts.transfer.model.TaskStatus;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.common.rest.controller.AbstractRestController;
import com.epam.pipeline.dts.common.rest.Result;
import com.epam.pipeline.dts.transfer.rest.dto.TaskCreationDTO;
import com.epam.pipeline.dts.transfer.rest.dto.TransferDTO;
import com.epam.pipeline.dts.transfer.rest.mapper.StorageItemMapper;
import com.epam.pipeline.dts.transfer.rest.mapper.TransferTaskMapper;
import com.epam.pipeline.dts.transfer.service.TaskService;
import com.epam.pipeline.dts.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
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

@RequestMapping("transfer")
@Tag(name = "Transfer tasks management")
@ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
@RestController
@AllArgsConstructor
public class TransferController extends AbstractRestController {

    private TaskService taskService;
    private TransferService transferService;
    private StorageItemMapper storageItemMapper;
    private TransferTaskMapper taskMapper;

    @PostMapping
    @Operation(
            summary = "Creates and schedules a new transfer task.")
    public Result<TransferDTO> createTask(@RequestBody TaskCreationDTO taskCreationDTO) {
        TransferTask task = transferService.runTransferTask(
                storageItemMapper.dtoToModel(taskCreationDTO.getSource()),
                storageItemMapper.dtoToModel(taskCreationDTO.getDestination()),
                taskCreationDTO.getIncluded(),
                taskCreationDTO.isDeleteSource(),
                taskCreationDTO.isLogEnabled(),
                taskCreationDTO.getPipeCmd(),
                taskCreationDTO.getPipeCmdSuffix());
        return Result.success(taskMapper.modelToDto(task));
    }

    @PutMapping(path = "/{taskId}")
    @Operation(
            summary = "Updates status of existing task.")
    public Result<TransferDTO> updateStatus(@PathVariable Long taskId,
                                            @RequestParam TaskStatus status,
                                            @RequestParam(required = false) String reason) {
        TransferTask task = taskService.updateStatus(taskId, status, reason);
        return Result.success(taskMapper.modelToDto(task));
    }

    @GetMapping(path = "/{taskId}")
    @Operation(
            summary = "Returns an existing task by id.")
    public Result<TransferDTO> getTask(@PathVariable Long taskId) {
        return Result.success(taskMapper.modelToDto(taskService.loadTask(taskId)));
    }

    @DeleteMapping(path = "/{taskId}")
    @Operation(
            summary = "Deletes an existing task by id.")
    public Result deleteTask(@PathVariable Long taskId) {
        taskService.deleteTask(taskId);
        return Result.success(null);
    }

    @GetMapping
    @Operation(
            summary = "Returns all tasks.")
    public Result<List<TransferDTO>> getAll() {
        return Result.success(taskService
                .loadAll()
                .stream()
                .map(taskMapper::modelToDto)
                .collect(Collectors.toList()));
    }
}

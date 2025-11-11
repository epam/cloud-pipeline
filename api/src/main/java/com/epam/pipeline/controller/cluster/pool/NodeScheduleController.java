/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.controller.cluster.pool;

import com.epam.pipeline.acl.cluster.pool.NodeScheduleApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.cluster.pool.NodeScheduleVO;
import com.epam.pipeline.entity.cluster.pool.NodeSchedule;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@Tag(name = "Node Schedule Management")
@RequiredArgsConstructor
@RequestMapping("/cluster/nodeSchedule")
@ResponseBody
public class NodeScheduleController extends AbstractRestController {

    private final NodeScheduleApiService apiService;

    @GetMapping
    @Operation(summary = "Returns all registered node schedules")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<NodeSchedule>> loadAll() {
        return Result.success(apiService.loadAll());
    }

    @GetMapping("{id}")
    @Operation(summary = "Returns a node schedule by id")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<NodeSchedule> load(final @PathVariable long id) {
        return Result.success(apiService.load(id));
    }

    @PostMapping
    @Operation(summary = "Creates or updates a node schedule")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<NodeSchedule> createOrUpdate(final @RequestBody NodeScheduleVO vo) {
        return Result.success(apiService.createOrUpdate(vo));
    }

    @DeleteMapping("{id}")
    @Operation(summary = "Deletes a node schedule by id")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<NodeSchedule> delete(final @PathVariable long id) {
        return Result.success(apiService.delete(id));
    }

}

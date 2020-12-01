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
@Api(value = "Node Schedule Management")
@RequiredArgsConstructor
@RequestMapping("/cluster/nodeSchedule")
@ResponseBody
public class NodeScheduleController extends AbstractRestController {

    private final NodeScheduleApiService apiService;

    @GetMapping
    @ApiOperation(value = "Returns all registered node schedules", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<NodeSchedule>> loadAll() {
        return Result.success(apiService.loadAll());
    }

    @GetMapping("{id}")
    @ApiOperation(value = "Returns a node schedule by id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<NodeSchedule> load(final @PathVariable long id) {
        return Result.success(apiService.load(id));
    }

    @PostMapping
    @ApiOperation(value = "Creates or updates a node schedule", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<NodeSchedule> createOrUpdate(final @RequestBody NodeScheduleVO vo) {
        return Result.success(apiService.createOrUpdate(vo));
    }

    @DeleteMapping("{id}")
    @ApiOperation(value = "Deletes a node schedule by id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<NodeSchedule> delete(final @PathVariable long id) {
        return Result.success(apiService.delete(id));
    }

}

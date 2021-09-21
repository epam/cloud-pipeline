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

import com.epam.pipeline.acl.cluster.pool.NodePoolApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.pool.NodePool;
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
@Api(value = "Node Pool Management")
@RequiredArgsConstructor
@RequestMapping("/cluster/pool")
@ResponseBody
public class NodePoolController extends AbstractRestController {

    private final NodePoolApiService apiService;

    @GetMapping
    @ApiOperation(value = "Returns all registered node pools", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<NodePool>> loadAll() {
        return Result.success(apiService.loadAll());
    }

    @GetMapping("{id}")
    @ApiOperation(value = "Returns a node pool by id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<NodePool> load(final @PathVariable Long id) {
        return Result.success(apiService.load(id));
    }

    @PostMapping
    @ApiOperation(value = "Creates or updates a node pool", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<NodePool> createOrUpdate(final @RequestBody NodePoolVO vo) {
        return Result.success(apiService.createOrUpdate(vo));
    }

    @DeleteMapping("{id}")
    @ApiOperation(value = "Deletes a node pool by id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<NodePool> delete(final @PathVariable Long id) {
        return Result.success(apiService.delete(id));
    }

}

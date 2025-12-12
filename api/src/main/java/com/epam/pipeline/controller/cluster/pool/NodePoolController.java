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
import com.epam.pipeline.entity.cluster.pool.NodePoolInfo;
import com.epam.pipeline.entity.cluster.pool.NodePoolUsage;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;

@Controller
@Tag(name = "Node Pool Management")
@RequiredArgsConstructor
@RequestMapping("/cluster/pool")
@ResponseBody
public class NodePoolController extends AbstractRestController {
    private final NodePoolApiService apiService;

    @GetMapping
    @Operation(summary = "Returns all registered node pools")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<? extends NodePoolInfo>> loadAll(
            final @RequestParam(defaultValue = "false") boolean loadStatus) {
        return Result.success(apiService.loadAll(loadStatus));
    }

    @GetMapping("{id}")
    @Operation(summary = "Returns a node pool by id")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<NodePool> load(final @PathVariable Long id) {
        return Result.success(apiService.load(id));
    }

    @PostMapping
    @Operation(summary = "Creates or updates a node pool")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<NodePool> createOrUpdate(final @RequestBody NodePoolVO vo) {
        return Result.success(apiService.createOrUpdate(vo));
    }

    @DeleteMapping("{id}")
    @Operation(summary = "Deletes a node pool by id")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<NodePool> delete(final @PathVariable Long id) {
        return Result.success(apiService.delete(id));
    }

    @PostMapping("/usage")
    @Operation(summary = "Persists node pool usage")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<NodePoolUsage>> saveUsage(final @RequestBody List<NodePoolUsage> records) {
        return Result.success(apiService.saveUsage(records));
    }

    @DeleteMapping("/usage")
    @Operation(summary = "Deletes node pool usage records older than specified date")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Boolean> deleteUsage(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam
                                           final LocalDate date) {
        return Result.success(apiService.deleteUsage(date));
    }
}

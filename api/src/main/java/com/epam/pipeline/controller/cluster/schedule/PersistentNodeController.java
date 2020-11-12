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

package com.epam.pipeline.controller.cluster.schedule;

import com.epam.pipeline.acl.cluster.schedule.PersistentNodeApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.cluster.schedule.PersistentNodeVO;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
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
@Api(value = "Persistent Node Management")
@RequiredArgsConstructor
@RequestMapping("/cluster/persistentNode")
@ResponseBody
public class PersistentNodeController extends AbstractRestController {

    private final PersistentNodeApiService apiService;

    @GetMapping
    @ApiOperation(value = "Returns all registered persistent nodes", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<PersistentNode>> loadAll() {
        return Result.success(apiService.loadAll());
    }

    @GetMapping("{id}")
    @ApiOperation(value = "Returns a persistent nodes by id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PersistentNode> load(final @PathVariable long id) {
        return Result.success(apiService.load(id));
    }

    @PostMapping
    @ApiOperation(value = "Creates or updates a persistent node", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PersistentNode> createOrUpdate(final @RequestBody PersistentNodeVO vo) {
        return Result.success(apiService.createOrUpdate(vo));
    }

    @DeleteMapping("{id}")
    @ApiOperation(value = "Deletes a persistent nodes by id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PersistentNode> delete(final @PathVariable long id) {
        return Result.success(apiService.delete(id));
    }

}

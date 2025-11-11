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

package com.epam.pipeline.controller.docker;

import java.util.List;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;
import com.epam.pipeline.acl.docker.ToolGroupApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
public class ToolGroupController extends AbstractRestController {
    private static final String TOOL_GROUP_URL = "/toolGroup";

    @Autowired
    private ToolGroupApiService toolGroupApiService;

    @RequestMapping(value = "/toolGroup/list", method = RequestMethod.GET)
    public Result<List<ToolGroup>> listToolGroups(@RequestParam String registry) {
        return Result.success(toolGroupApiService.loadByRegistryNameOrId(registry));
    }

    @RequestMapping(value = "/toolGroup/private", method = RequestMethod.POST)
    public Result<ToolGroup> createPrivate(@RequestParam Long registryId) {
        return Result.success(toolGroupApiService.createPrivate(registryId));
    }

    @RequestMapping(value = TOOL_GROUP_URL, method = RequestMethod.GET)
    public Result<ToolGroup> loadToolGroup(@RequestParam String id) {
        return Result.success(toolGroupApiService.loadByNameOrId(id));
    }

    @GetMapping("/toolGroup/{id}/issuesCount")
    @Operation(
            summary = "Loads tool group with number of issues for each tool in group.",
            description = "Loads tool group with number of issues for each tool in group.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ToolGroupWithIssues> loadToolGroupWithIssuesCount(@PathVariable Long id) {
        return Result.success(toolGroupApiService.loadToolsWithIssuesCount(id));
    }

    @RequestMapping(value = TOOL_GROUP_URL, method = RequestMethod.POST)
    public Result<ToolGroup> create(@RequestBody ToolGroup group) {
        return Result.success(toolGroupApiService.create(group));
    }

    @RequestMapping(value = TOOL_GROUP_URL, method = RequestMethod.PUT)
    @Operation(
        summary = "Updates a tool group.",
        description = "Updates a tool group. The only field, that is allowed for update is description")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<ToolGroup> update(@RequestBody ToolGroup group) {
        return Result.success(toolGroupApiService.update(group));
    }

    @RequestMapping(value = TOOL_GROUP_URL, method = RequestMethod.DELETE)
    public Result<ToolGroup> delete(@RequestParam String id, 
                                    @RequestParam(defaultValue = "false") boolean force) {
        return Result.success(force ? toolGroupApiService.deleteForce(id) : toolGroupApiService.delete(id));
    }
}

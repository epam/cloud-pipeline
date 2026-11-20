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

package com.epam.pipeline.controller.dts;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.entity.dts.DtsRegistry;
<<<<<<< ours
import com.epam.pipeline.manager.dts.DtsRegistryApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
||||||| base
import com.epam.pipeline.acl.dts.DtsRegistryApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
=======
import com.epam.pipeline.acl.dts.DtsRegistryApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
>>>>>>> theirs
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Tag(name = "dts-registry-controller", description = "Data Transfer Service registry")
@RequestMapping(value = "/dts")
@AllArgsConstructor
public class DtsRegistryController extends AbstractRestController {
    private static final String REGISTRY_ID = "registryId";

    private DtsRegistryApiService dtsRegistryApiService;

    @GetMapping
    @ResponseBody
    @Operation(summary = "Lists Data Transfer Service registry.",
            description = "Lists Data Transfer Service registry.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<List<DtsRegistry>> loadAll() {
        return Result.success(dtsRegistryApiService.loadAll());
    }

    @GetMapping(value = "/{registryId}")
    @ResponseBody
<<<<<<< ours
    @ApiOperation(
            value = "Lists Data Transfer Service registry specified by ID.",
            notes = "Lists Data Transfer Service registry specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> load(@PathVariable(value = REGISTRY_ID) Long registryId) {
        return Result.success(dtsRegistryApiService.load(registryId));
||||||| base
    @ApiOperation(
            value = "Lists Data Transfer Service registry specified by ID or name.",
            notes = "Lists Data Transfer Service registry specified by ID or name.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> load(@PathVariable(value = REGISTRY_ID) String registryId) {
        return Result.success(dtsRegistryApiService.loadByNameOrId(registryId));
=======
    @Operation(summary = "Lists Data Transfer Service registry specified by ID or name.",
            description = "Lists Data Transfer Service registry specified by ID or name.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<DtsRegistry> load(@PathVariable(value = REGISTRY_ID) String registryId) {
        return Result.success(dtsRegistryApiService.loadByNameOrId(registryId));
>>>>>>> theirs
    }

    @PostMapping
    @ResponseBody
    @Operation(summary = "Creates a new Data Transfer Service registry.",
            description = "Creates a new Data Transfer Service registry.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<DtsRegistry> createDtsRegistry(@RequestBody DtsRegistryVO dtsRegistryVO) {
        return Result.success(dtsRegistryApiService.create(dtsRegistryVO));
    }

    @PutMapping(value = "/{registryId}")
    @ResponseBody
<<<<<<< ours
    @ApiOperation(
            value = "Updates Data Transfer Service registry.",
            notes = "Updates Data Transfer Service registry.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> updateDtsRegistry(@PathVariable(value = REGISTRY_ID) Long registryId,
||||||| base
    @ApiOperation(
            value = "Updates Data Transfer Service registry.",
            notes = "Updates Data Transfer Service registry.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> updateDtsRegistry(@PathVariable(value = REGISTRY_ID) String registryId,
=======
    @Operation(summary = "Updates Data Transfer Service registry.",
            description = "Updates Data Transfer Service registry.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<DtsRegistry> updateDtsRegistry(@PathVariable(value = REGISTRY_ID) String registryId,
>>>>>>> theirs
                                                 @RequestBody DtsRegistryVO dtsRegistryVO) {
        return Result.success(dtsRegistryApiService.update(registryId, dtsRegistryVO));
    }

<<<<<<< ours
||||||| base
    @PutMapping(value = "/{registryId}/heartbeat")
    @ResponseBody
    @ApiOperation(
            value = "Updates Data Transfer Service registry heartbeat.",
            notes = "Updates Data Transfer Service registry heartbeat.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> updateDtsRegistryHeartbeat(final @PathVariable(value = REGISTRY_ID) String registryId) {
        return Result.success(dtsRegistryApiService.updateHeartbeat(registryId));
    }

=======
    @PutMapping(value = "/{registryId}/heartbeat")
    @ResponseBody
    @Operation(summary = "Updates Data Transfer Service registry heartbeat.",
            description = "Updates Data Transfer Service registry heartbeat.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<DtsRegistry> updateDtsRegistryHeartbeat(final @PathVariable(value = REGISTRY_ID) String registryId) {
        return Result.success(dtsRegistryApiService.updateHeartbeat(registryId));
    }

>>>>>>> theirs
    @DeleteMapping(value = "/{registryId}")
    @ResponseBody
<<<<<<< ours
    @ApiOperation(
            value = "Deletes Data Transfer Service registry.",
            notes = "Deletes Data Transfer Service registry.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> updateDtsRegistry(@PathVariable(value = REGISTRY_ID) Long registryId) {
||||||| base
    @ApiOperation(
            value = "Deletes Data Transfer Service registry.",
            notes = "Deletes Data Transfer Service registry.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> updateDtsRegistry(@PathVariable(value = REGISTRY_ID) String registryId) {
=======
    @Operation(summary = "Deletes Data Transfer Service registry.",
            description = "Deletes Data Transfer Service registry.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<DtsRegistry> updateDtsRegistry(@PathVariable(value = REGISTRY_ID) String registryId) {
>>>>>>> theirs
        return Result.success(dtsRegistryApiService.delete(registryId));
    }
<<<<<<< ours
||||||| base

    @PutMapping(value = "/{registryId}/preferences")
    @ResponseBody
    @ApiOperation(
        value = "Upserts preferences for a Data Transfer Service registry specified.",
        notes = "Upserts certain preferences for a Data Transfer Service registry, which is specified by id.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<DtsRegistry> upsertDtsRegistryPreferences(final @PathVariable(value = REGISTRY_ID) String registryId,
                                                            final @RequestBody
                                                                DtsRegistryPreferencesUpdateVO preferencesVO) {
        return Result.success(dtsRegistryApiService.upsertPreferences(registryId, preferencesVO));
    }

    @DeleteMapping(value = "/{registryId}/preferences")
    @ResponseBody
    @ApiOperation(
            value = "Deletes preferences for a Data Transfer Service registry specified.",
            notes = "Deletes certain preferences for a Data Transfer Service registry, which is specified by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> deleteDtsRegistryPreferences(final @PathVariable(value = REGISTRY_ID) String registryId,
                                                            final @RequestBody
                                                                DtsRegistryPreferencesRemovalVO preferencesVO) {
        return Result.success(dtsRegistryApiService.deletePreferences(registryId, preferencesVO));
    }
=======

    @PutMapping(value = "/{registryId}/preferences")
    @ResponseBody
    @Operation(
        summary = "Upserts preferences for a Data Transfer Service registry specified.",
        description = "Upserts certain preferences for a Data Transfer Service registry, which is specified by id.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<DtsRegistry> upsertDtsRegistryPreferences(final @PathVariable(value = REGISTRY_ID) String registryId,
                                                            final @RequestBody
                                                                DtsRegistryPreferencesUpdateVO preferencesVO) {
        return Result.success(dtsRegistryApiService.upsertPreferences(registryId, preferencesVO));
    }

    @DeleteMapping(value = "/{registryId}/preferences")
    @ResponseBody
    @Operation(summary = "Deletes preferences for a Data Transfer Service registry specified.",
            description = "Deletes certain preferences for a Data Transfer Service registry, which is specified by id.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<DtsRegistry> deleteDtsRegistryPreferences(final @PathVariable(value = REGISTRY_ID) String registryId,
                                                            final @RequestBody
                                                                DtsRegistryPreferencesRemovalVO preferencesVO) {
        return Result.success(dtsRegistryApiService.deletePreferences(registryId, preferencesVO));
    }
>>>>>>> theirs
}

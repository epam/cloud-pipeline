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
import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesRemovalVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesUpdateVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.acl.dts.DtsRegistryApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
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
@Api(value = "Data Transfer Service registry")
@RequestMapping(value = "/dts")
@AllArgsConstructor
public class DtsRegistryController extends AbstractRestController {
    private static final String REGISTRY_ID = "registryId";

    private DtsRegistryApiService dtsRegistryApiService;

    @GetMapping
    @ResponseBody
    @ApiOperation(
            value = "Lists Data Transfer Service registry.",
            notes = "Lists Data Transfer Service registry.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<DtsRegistry>> loadAll() {
        return Result.success(dtsRegistryApiService.loadAll());
    }

    @GetMapping(value = "/{registryId}")
    @ResponseBody
    @ApiOperation(
            value = "Lists Data Transfer Service registry specified by ID or name.",
            notes = "Lists Data Transfer Service registry specified by ID or name.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> load(@PathVariable(value = REGISTRY_ID) String registryId) {
        return Result.success(dtsRegistryApiService.loadByNameOrId(registryId));
    }

    @PostMapping
    @ResponseBody
    @ApiOperation(
            value = "Creates a new Data Transfer Service registry.",
            notes = "Creates a new Data Transfer Service registry.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> createDtsRegistry(@RequestBody DtsRegistryVO dtsRegistryVO) {
        return Result.success(dtsRegistryApiService.create(dtsRegistryVO));
    }

    @PutMapping(value = "/{registryId}")
    @ResponseBody
    @ApiOperation(
            value = "Updates Data Transfer Service registry.",
            notes = "Updates Data Transfer Service registry.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> updateDtsRegistry(@PathVariable(value = REGISTRY_ID) String registryId,
                                                 @RequestBody DtsRegistryVO dtsRegistryVO) {
        return Result.success(dtsRegistryApiService.update(registryId, dtsRegistryVO));
    }

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

    @DeleteMapping(value = "/{registryId}")
    @ResponseBody
    @ApiOperation(
            value = "Deletes Data Transfer Service registry.",
            notes = "Deletes Data Transfer Service registry.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsRegistry> updateDtsRegistry(@PathVariable(value = REGISTRY_ID) String registryId) {
        return Result.success(dtsRegistryApiService.delete(registryId));
    }

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
}

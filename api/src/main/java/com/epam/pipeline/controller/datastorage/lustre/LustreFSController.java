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

package com.epam.pipeline.controller.datastorage.lustre;

import com.epam.pipeline.acl.datastorage.lustre.LustreFSApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.datastorage.LustreFS;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Lustre FS management methods")
@RequestMapping(value = "/lustre")
@RequiredArgsConstructor
public class LustreFSController extends AbstractRestController {

    private static final String RUN_ID = "run_id";
    private static final String RUN_ID_PATH = "/{run_id}";

    private final LustreFSApiService lustreFSApiService;

    @PostMapping(value = RUN_ID_PATH)
    @Operation(
            summary = "Creates a new lustre FS for a run or returns an existing one.",
            description = "Creates a new lustre FS for a run or returns an existing one.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<LustreFS> getOrCreateLustreFS(@PathVariable(value = RUN_ID) final Long runId,
                                                @RequestParam(required = false) final Integer size,
                                                @RequestParam(required = false) final String type,
                                                @RequestParam(required = false) final Integer throughput,
                                                @RequestParam(required = false) final Integer iops) {
        return Result.success(lustreFSApiService.getOrCreateLustreFS(runId, size, type, throughput, iops));
    }

    @PutMapping(value = RUN_ID_PATH)
    @Operation(
            summary = "Changes size of lustre FS.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<LustreFS> updateLustreFsSize(@PathVariable(value = RUN_ID) final Long runId,
                                               @RequestParam(required = false) final Integer size) {
        return Result.success(lustreFSApiService.updateLustreFsSize(runId, size));
    }

    @GetMapping(value = RUN_ID_PATH)
    @Operation(
            summary = "Returns an existing lustre FS for a run.",
            description = "Returns an existing lustre FS for a run.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<LustreFS> getLustreFS(@PathVariable(value = RUN_ID) final Long runId) {
        return Result.success(lustreFSApiService.getLustreFS(runId));
    }

    @GetMapping
    @Operation(
            summary = "Returns lustreFs by id.",
            description = "Returns lustreFs by id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<LustreFS> getLustreFS(@RequestParam final String mountName,
                                        @RequestParam final Long regionId) {
        return Result.success(lustreFSApiService.getLustreFS(mountName, regionId));
    }

    @DeleteMapping(value = RUN_ID_PATH)
    @Operation(
            summary = "Deletes lustre FS for a run.",
            description = "Deletes lustre FS for a run.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<LustreFS> deleteLustreFS(@PathVariable(value = RUN_ID) final Long runId) {
        return Result.success(lustreFSApiService.deleteLustreFS(runId));
    }
}

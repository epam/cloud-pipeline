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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(value = "Lustre FS management methods")
@RequestMapping(value = "/lustre")
@RequiredArgsConstructor
public class LustreFSController extends AbstractRestController {

    private static final String RUN_ID = "run_id";
    private static final String RUN_ID_PATH = "/{run_id}";

    private final LustreFSApiService lustreFSApiService;

    @PostMapping(value = RUN_ID_PATH)
    @ApiOperation(
            value = "Creates a new lustre FS for a run or returns an existing one.",
            notes = "Creates a new lustre FS for a run or returns an existing one.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<LustreFS> getOrCreateLustreFS(@PathVariable(value = RUN_ID) final Long runId,
                                                @RequestParam(required = false) final Integer size) {
        return Result.success(lustreFSApiService.getOrCreateLustreFS(runId, size));
    }

    @GetMapping(value = RUN_ID_PATH)
    @ApiOperation(
            value = "Returns an existing lustre FS for a run.",
            notes = "Returns an existing lustre FS for a run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<LustreFS> getLustreFS(@PathVariable(value = RUN_ID) final Long runId) {
        return Result.success(lustreFSApiService.getLustreFS(runId));
    }

    @DeleteMapping(value = RUN_ID_PATH)
    @ApiOperation(
            value = "Deletes lustre FS for a run.",
            notes = "Deletes lustre FS for a run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<LustreFS> deleteLustreFS(@PathVariable(value = RUN_ID) final Long runId) {
        return Result.success(lustreFSApiService.deleteLustreFS(runId));
    }
}

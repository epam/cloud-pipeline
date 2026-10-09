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
import com.epam.pipeline.entity.dts.DtsClusterConfiguration;
import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsSubmission;
import com.epam.pipeline.acl.dts.DtsOperationsApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "dts-operations-controller", description = "Listing Data Transfer Service items management")
@RequestMapping(value = "/dts")
@RequiredArgsConstructor
public class DtsOperationsController extends AbstractRestController {

    private final DtsOperationsApiService dtsOperationsApiService;

    @GetMapping(value = "/list/{dtsId}")
    @Operation(
            summary = "Returns storage content specified by path and DTS registry ID.",
            description = "Returns storage content specified by path and DTS registry ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DtsDataStorageListing> list(@PathVariable Long dtsId,
                                              @RequestParam String path,
                                              @RequestParam Integer pageSize,
                                              @RequestParam(required = false) String marker) {
        return Result.success(dtsOperationsApiService.list(path, dtsId, pageSize, marker));
    }


    @GetMapping(value = "/{dtsId}/submission")
    @Operation(
            summary = "Returns DTS submission by run id and DTS registry ID.",
            description = "Returns DTS submission by run id and DTS registry ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DtsSubmission> findSubmission(@PathVariable Long dtsId,
                                                @RequestParam Long runId) {
        return Result.success(dtsOperationsApiService.findSubmission(dtsId, runId));
    }

    @GetMapping(value = "/{dtsId}/cluster")
    @Operation(
            summary = "Returns DTS cluster configuration.",
            description = "Returns DTS cluster configuration.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DtsClusterConfiguration> getClusterConfiguration(@PathVariable Long dtsId) {
        return Result.success(dtsOperationsApiService.getClusterConfiguration(dtsId));
    }
}

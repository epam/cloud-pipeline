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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(value = "Listing Data Transfer Service items management")
@RequestMapping(value = "/dts")
@RequiredArgsConstructor
public class DtsOperationsController extends AbstractRestController {

    private final DtsOperationsApiService dtsOperationsApiService;

    @GetMapping(value = "/list/{dtsId}")
    @ApiOperation(
            value = "Returns storage content specified by path and DTS registry ID.",
            notes = "Returns storage content specified by path and DTS registry ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsDataStorageListing> list(@PathVariable Long dtsId,
                                              @RequestParam String path,
                                              @RequestParam Integer pageSize,
                                              @RequestParam(required = false) String marker) {
        return Result.success(dtsOperationsApiService.list(path, dtsId, pageSize, marker));
    }


    @GetMapping(value = "/{dtsId}/submission")
    @ApiOperation(
            value = "Returns DTS submission by run id and DTS registry ID.",
            notes = "Returns DTS submission by run id and DTS registry ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsSubmission> findSubmission(@PathVariable Long dtsId,
                                                @RequestParam Long runId) {
        return Result.success(dtsOperationsApiService.findSubmission(dtsId, runId));
    }

    @GetMapping(value = "/{dtsId}/cluster")
    @ApiOperation(
            value = "Returns DTS cluster configuration.",
            notes = "Returns DTS cluster configuration.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DtsClusterConfiguration> getClusterConfiguration(@PathVariable Long dtsId) {
        return Result.success(dtsOperationsApiService.getClusterConfiguration(dtsId));
    }
}

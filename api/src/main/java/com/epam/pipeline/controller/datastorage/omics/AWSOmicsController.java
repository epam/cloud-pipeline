/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.datastorage.omics;

import com.epam.pipeline.acl.datastorage.omics.AWSOmicsStoreApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.datastorage.omics.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@Tag(name = "AWS Omics store methods")
@RequestMapping(value = "/omicsstore")
public class AWSOmicsController extends AbstractRestController {

    @Autowired
    private AWSOmicsStoreApiService awsOmicsStoreApiService;

    @PostMapping("/{id}/import")
    @ResponseBody
    @Operation(
            summary = "Imports new files in AWS Omics storage.",
            description = "Imports new files in AWS Omics storage.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AWSOmicsFileImportJob> importOmicsFiles(@PathVariable final Long id,
                                                          @RequestBody final AWSOmicsFileImportRequest importRequest) {
        return Result.success(awsOmicsStoreApiService.importOmicsFiles(id, importRequest));
    }

    @PostMapping("/{id}/import/list")
    @ResponseBody
    @Operation(
            summary = "List AWS Omics storage import jobs according to filter.",
            description = "List AWS Omics storage import jobs according to filter.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AWSOmicsFileImportJobListing> listImportJobs(
            @PathVariable final Long id,
            @RequestParam(required = false) final String nextToken,
            @RequestParam(required = false) final Integer pageSize,
            @RequestBody(required = false) final AWSOmicsFileImportJobFilter filter) {
        return Result.success(awsOmicsStoreApiService.listImportJobs(id, nextToken, pageSize, filter));
    }

    @PostMapping("/{id}/activate")
    @ResponseBody
    @Operation(
            summary = "Activate AWS Omics storage files.",
            description = "Activate AWS Omics storage files.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AWSOmicsFilesActivationJob> activateOmicsFiles(
            @PathVariable final Long id,
            @RequestBody final AWSOmicsFilesActivationRequest request) {
        return Result.success(awsOmicsStoreApiService.activateOmicsFiles(id, request));
    }
}

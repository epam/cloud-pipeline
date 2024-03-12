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
import com.epam.pipeline.entity.datastorage.omics.AWSOmicsFileImportJob;
import com.epam.pipeline.entity.datastorage.omics.AWSOmicsFileImportJobFilter;
import com.epam.pipeline.entity.datastorage.omics.AWSOmicsFileImportJobListing;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@Api(value = "AWS Omics store methods")
@RequestMapping(value = "/omicsstore")
public class AWSOmicsController extends AbstractRestController {

    @Autowired
    private AWSOmicsStoreApiService awsOmicsStoreApiService;

    @PostMapping("/{id}/import")
    @ResponseBody
    @ApiOperation(
            value = "Imports new files in AWS Omics storage.",
            notes = "Imports new files in AWS Omics storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AWSOmicsFileImportJob> importOmicsFiles(@PathVariable final Long id,
                                                          @RequestBody final AWSOmicsFileImportJob importJob) {
        return Result.success(awsOmicsStoreApiService.importOmicsFiles(id, importJob));
    }

    @PostMapping("/{id}/import/list")
    @ResponseBody
    @ApiOperation(
            value = "List AWS Omics storage import jobs according to filter.",
            notes = "List AWS Omics storage import jobs according to filter.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AWSOmicsFileImportJobListing> listImportJobs(
            @PathVariable final Long id,
            @RequestParam(required = false) final String nextToken,
            @RequestParam(required = false) final Integer pageSize,
            @RequestBody(required = false) final AWSOmicsFileImportJobFilter filter) {
        return Result.success(awsOmicsStoreApiService.listImportJobs(id, nextToken, pageSize, filter));
    }
}

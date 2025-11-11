/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.datastorage;

import com.epam.pipeline.acl.datastorage.DataStorageApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.datastorage.StorageUsage;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "Datastorage usage methods")
@RequiredArgsConstructor
public class StorageUsageController extends AbstractRestController {

    private final DataStorageApiService dataStorageApiService;

    @GetMapping(value = "/datastorage/path/usage")
    @ResponseBody
    @Operation(
            summary = "Returns storage usage statistics.",
            description = "Returns storage usage statistics.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<StorageUsage> getStorageUsage(@RequestParam final String id,
                                                @RequestParam(required = false) final String path) {
        return Result.success(dataStorageApiService.getStorageUsage(id, path));
    }

    @PutMapping(value = "/datastorage/path/usage")
    @ResponseBody
    @Operation(
            summary = "Request update of storage usage statistics.",
            description = "Request update of storage usage statistics.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result updateStorageUsage(@RequestParam final String id) {
        dataStorageApiService.updateStorageUsage(id);
        return Result.success();
    }
}

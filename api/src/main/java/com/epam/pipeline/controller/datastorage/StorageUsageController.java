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

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.manager.datastorage.DataStorageApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Api(value = "Datastorage usage methods")
@RequiredArgsConstructor
public class StorageUsageController extends AbstractRestController {

    private final DataStorageApiService dataStorageApiService;

    @GetMapping(value = "/datastorage/path/usage")
    @ResponseBody
    @ApiOperation(
            value = "Returns storage usage statistics.",
            notes = "Returns storage usage statistics.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageUsage> getStorageUsage(@RequestParam final String id,
                                                @RequestParam(required = false) final String path) {
        return Result.success(dataStorageApiService.getStorageUsage(id, path));
    }

    @PutMapping(value = "/datastorage/path/usage")
    @ResponseBody
    @ApiOperation(
            value = "Request update of storage usage statistics.",
            notes = "Request update of storage usage statistics.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result updateStorageUsage(@RequestParam final String id) {
        dataStorageApiService.updateStorageUsage(id);
        return Result.success();
    }
}

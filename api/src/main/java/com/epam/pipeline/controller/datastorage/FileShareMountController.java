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

package com.epam.pipeline.controller.datastorage;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.acl.datastorage.FileShareMountApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@Api(value = "FileShareMount methods")
@RequestMapping(value = "/filesharemount")
public class FileShareMountController extends AbstractRestController {

    @Autowired
    private FileShareMountApiService fileShareMountApiService;

    @PostMapping
    @ResponseBody
    @ApiOperation(
            value = "Create or update file share mount.",
            notes = "Create or update file share mount.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<FileShareMount> save(@RequestBody FileShareMount fileShareMount) {
        return Result.success(fileShareMountApiService.save(fileShareMount));
    }

    @DeleteMapping(value = "/{id}")
    @ResponseBody
    @ApiOperation(
            value = "Delete file share mount.",
            notes = "Delete file share mount.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public void delete(@PathVariable Long id) {
        fileShareMountApiService.delete(id);
    }

}

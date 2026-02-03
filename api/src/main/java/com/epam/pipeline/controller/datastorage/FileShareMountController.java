/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "FileShareMount methods")
@RequestMapping(value = "/filesharemount")
public class FileShareMountController extends AbstractRestController {

    @Autowired
    private FileShareMountApiService fileShareMountApiService;

    @PostMapping
    @ResponseBody
    @Operation(
            summary = "Create or update file share mount.",
            description = "Create or update file share mount.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<FileShareMount> save(@RequestBody FileShareMount fileShareMount) {
        return Result.success(fileShareMountApiService.save(fileShareMount));
    }

    @DeleteMapping(value = "/{id}")
    @ResponseBody
    @Operation(
            summary = "Delete file share mount.",
            description = "Delete file share mount.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public void delete(@PathVariable Long id) {
        fileShareMountApiService.delete(id);
    }

    @GetMapping(value = "/{id}")
    @ResponseBody
    @Operation(
            summary = "Load file share mount.",
            description = "Load file share mount details.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<FileShareMount> load(final @PathVariable Long id) {
        return Result.success(fileShareMountApiService.load(id));
    }
}

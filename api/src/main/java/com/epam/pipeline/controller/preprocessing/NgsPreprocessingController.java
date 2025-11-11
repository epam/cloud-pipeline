/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.preprocessing;

import com.epam.pipeline.acl.preprocessing.NgsPreprocessingApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.preprocessing.SampleSheetRegistrationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/preprocessing")
@Tag(name = "NGS Project Preprocessing")
public class NgsPreprocessingController extends AbstractRestController {

    private final NgsPreprocessingApiService preprocessingApiService;

    @PostMapping(value = "/samplesheet")
    @Operation(
            summary = "Registers a new or update an existing samplesheet.",
            description = "Registers a new or update an existing samplesheet.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result registerSampleSheet(@RequestBody final SampleSheetRegistrationVO sheetRegistrationVO) {
        preprocessingApiService.registerSampleSheet(sheetRegistrationVO);
        return Result.success();
    }

    @DeleteMapping(value = "/samplesheet")
    @Operation(summary = "Deletes an existing samplesheet.", description = "Deletes an existing samplesheet.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result deleteSampleSheet(@RequestParam final Long folderId, @RequestParam final Long machineRunId,
                                    @RequestParam(defaultValue = "false") final Boolean deleteFile) {
        preprocessingApiService.unregisterSampleSheet(folderId, machineRunId, deleteFile);
        return Result.success();
    }

}

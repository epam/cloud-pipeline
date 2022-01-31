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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Api(value = "NGS Project Preprocessing")
public class NgsPreprocessingController extends AbstractRestController {

    @Autowired
    private NgsPreprocessingApiService preprocessingApiService;

    @RequestMapping(value = "/preprocessing/samplesheet", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Registers a new or update an existing samplesheet.",
            notes = "Registers a new or update an existing samplesheet.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result registerSampleSheet(@RequestBody final SampleSheetRegistrationVO sheetRegistrationVO) {
        preprocessingApiService.registerSampleSheet(sheetRegistrationVO);
        return Result.success();
    }

    @RequestMapping(value = "/preprocessing/samplesheet", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes an existing samplesheet.",
            notes = "Deletes an existing samplesheet.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result deleteSampleSheet(@RequestParam final Long folderId, @RequestParam final Long machineRunId) {
        preprocessingApiService.deleteSampleSheet(folderId, machineRunId);
        return Result.success();
    }

}

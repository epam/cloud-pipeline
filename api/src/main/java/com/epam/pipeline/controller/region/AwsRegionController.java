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

package com.epam.pipeline.controller.region;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.AwsRegionVO;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.region.AwsRegionApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(value = "Aws region")
@RequestMapping(value = "/aws/region")
@RequiredArgsConstructor
public class AwsRegionController extends AbstractRestController {

    private static final String REGION_ID_URL = "/{regionId}";
    private static final String REGION_ID = "regionId";

    private final AwsRegionApiService awsRegionApiService;

    @GetMapping
    @ApiOperation(
            value = "Lists all regions.",
            notes = "Lists all regions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<AwsRegion>> loadAll() {
        return Result.success(awsRegionApiService.loadAll());
    }

    @GetMapping(REGION_ID_URL)
    @ApiOperation(
            value = "Lists single region by the specified id.",
            notes = "Lists single region by the specified id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AwsRegion> load(@PathVariable(REGION_ID) final Long id) {
        return Result.success(awsRegionApiService.load(id));
    }

    @GetMapping("/available")
    @ApiOperation(
            value = "Returns all available AWS regions.",
            notes = "Returns all available AWS regions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<String>> loadAllAvailable() {
        return Result.success(awsRegionApiService.loadAllAvailable());
    }

    @PostMapping
    @ApiOperation(
            value = "Creates region",
            notes = "Creates region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AwsRegion> create(@RequestBody final AwsRegionVO region) {
        return Result.success(awsRegionApiService.create(region));
    }

    @PutMapping(REGION_ID_URL)
    @ApiOperation(
            value = "Updates region",
            notes = "Updates region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AwsRegion> update(@PathVariable(REGION_ID) final Long id,
                                    @RequestBody final AwsRegionVO region) {
        return Result.success(awsRegionApiService.update(id, region));
    }

    @DeleteMapping(REGION_ID_URL)
    @ApiOperation(
            value = "Deletes region",
            notes = "Deletes region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AwsRegion> delete(@PathVariable(REGION_ID) final Long id) {
        return Result.success(awsRegionApiService.delete(id));
    }

}

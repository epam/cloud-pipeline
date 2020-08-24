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
import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.region.CloudRegionApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Cloud regions management")
@RequestMapping(value = "/cloud/region")
@RequiredArgsConstructor
public class CloudRegionController extends AbstractRestController {

    private static final String REGION_ID_URL = "/{regionId}";
    private static final String REGION_ID = "regionId";

    private final CloudRegionApiService cloudRegionApiService;

    @GetMapping("/provider")
    @ApiOperation(
            value = "Lists all supported cloud providers.",
            notes = "Lists all supported cloud providers.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<CloudProvider>> loadProviders() {
        return Result.success(cloudRegionApiService.loadProviders());
    }

    @GetMapping
    @ApiOperation(
            value = "Lists all regions.",
            notes = "Lists all regions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<? extends AbstractCloudRegion>> loadAll() {
        return Result.success(cloudRegionApiService.loadAll());
    }

    @GetMapping("/billing")
    @ApiOperation(
        value = "Lists all regions for billing.",
        notes = "Lists all regions for billing. Some fields, which are not used for billing purposes are "
                + "restored to default values.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<List<? extends AbstractCloudRegion>> loadAllForBilling() {
        return Result.success(cloudRegionApiService.loadAllForBilling());
    }

    @GetMapping(REGION_ID_URL)
    @ApiOperation(
            value = "Lists single region by the specified id.",
            notes = "Lists single region by the specified id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractCloudRegion> load(@PathVariable(REGION_ID) final Long id) {
        return Result.success(cloudRegionApiService.load(id));
    }

    @GetMapping("/available")
    @ApiOperation(
            value = "Returns all available cloud regions.",
            notes = "Returns all available cloud regions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<String>> loadAllAvailable(@RequestParam(required = false) CloudProvider provider) {
        return Result.success(cloudRegionApiService.loadAllAvailable(provider));
    }

    @PostMapping
    @ApiOperation(
            value = "Creates region",
            notes = "Creates region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractCloudRegion> create(@RequestBody final AbstractCloudRegionDTO region) {
        return Result.success(cloudRegionApiService.create(region));
    }

    @PutMapping(REGION_ID_URL)
    @ApiOperation(
            value = "Updates region",
            notes = "Updates region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractCloudRegion> update(@PathVariable(REGION_ID) final Long id,
                                    @RequestBody final AbstractCloudRegionDTO region) {
        return Result.success(cloudRegionApiService.update(id, region));
    }

    @DeleteMapping(REGION_ID_URL)
    @ApiOperation(
            value = "Deletes region",
            notes = "Deletes region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractCloudRegion> delete(@PathVariable(REGION_ID) final Long id) {
        return Result.success(cloudRegionApiService.delete(id));
    }

}

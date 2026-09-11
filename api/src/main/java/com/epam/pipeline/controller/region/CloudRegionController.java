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

import com.epam.pipeline.acl.cluster.InstanceOfferApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.entity.info.CloudRegionInfo;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.acl.region.CloudRegionApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "cloud-region-controller", description = "Cloud regions management")
@RequestMapping(value = "/cloud/region")
@RequiredArgsConstructor
public class CloudRegionController extends AbstractRestController {

    private static final String REGION_ID_URL = "/{regionId}";
    private static final String REGION_ID = "regionId";

    private final CloudRegionApiService cloudRegionApiService;
    private final InstanceOfferApiService instanceOfferApiService;

    @GetMapping("/provider")
    @Operation(
            summary = "Lists all supported cloud providers.",
            description = "Lists all supported cloud providers.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<CloudProvider>> loadProviders() {
        return Result.success(cloudRegionApiService.loadProviders());
    }

    @GetMapping
    @Operation(
            summary = "Lists all regions.",
            description = "Lists all regions.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<? extends AbstractCloudRegion>> loadAll() {
        return Result.success(cloudRegionApiService.loadAll());
    }

    @GetMapping("/info")
    @Operation(
        summary = "Lists all regions' brief information.",
        description = "Lists all regions, but instead of providing detailed description only the general information "
                + "is returned.")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<List<CloudRegionInfo>> loadAllRegionsInfo() {
        return Result.success(cloudRegionApiService.loadAllRegionsInfo());
    }

    @GetMapping(REGION_ID_URL)
    @Operation(
            summary = "Lists single region by the specified id.",
            description = "Lists single region by the specified id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractCloudRegion> load(@PathVariable(REGION_ID) final Long id) {
        return Result.success(cloudRegionApiService.load(id));
    }

    @GetMapping("/available")
    @Operation(
            summary = "Returns all available cloud regions.",
            description = "Returns all available cloud regions.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<String>> loadAllAvailable(@RequestParam(required = false) CloudProvider provider) {
        return Result.success(cloudRegionApiService.loadAllAvailable(provider));
    }

    @PostMapping
    @Operation(
            summary = "Creates region",
            description = "Creates region")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractCloudRegion> create(@RequestBody final AbstractCloudRegionDTO region) {
        return Result.success(cloudRegionApiService.create(region));
    }

    @PutMapping(REGION_ID_URL)
    @Operation(
            summary = "Updates region",
            description = "Updates region")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractCloudRegion> update(@PathVariable(REGION_ID) final Long id,
                                              @RequestBody final AbstractCloudRegionDTO region) {
        return Result.success(cloudRegionApiService.update(id, region));
    }

    @DeleteMapping(REGION_ID_URL)
    @Operation(
            summary = "Deletes region",
            description = "Deletes region")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractCloudRegion> delete(@PathVariable(REGION_ID) final Long id) {
        return Result.success(cloudRegionApiService.delete(id));
    }

    @PostMapping(REGION_ID_URL + "/price")
    @Operation(
            summary = "Refreshes price list asynchronously",
            description = "Refreshes price list asynchronously")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Object> updatePriceList(@PathVariable(REGION_ID) final Long id) {
        instanceOfferApiService.updatePriceList(id);
        return Result.success();
    }
}

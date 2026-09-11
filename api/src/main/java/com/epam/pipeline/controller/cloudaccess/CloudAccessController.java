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

package com.epam.pipeline.controller.cloudaccess;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessProfile;
import com.epam.pipeline.entity.cloudaccess.key.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import com.epam.pipeline.manager.cloudaccess.CloudAccessApiService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Tag(name = "cloud-access-controller", description = "Cloud access management methods")
@RequestMapping(value = "/cloud/access")
@RequiredArgsConstructor
public class CloudAccessController extends AbstractRestController {

    private final CloudAccessApiService cloudAccessApiService;

    @GetMapping("/profile")
    @Operation(summary = "Gets cloud access profile for specified user across all regions")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CloudUserAccessProfile> getCloudPermissions(@RequestParam final String username) {
        return Result.success(cloudAccessApiService.getCloudUserProfile(username));
    }

    @GetMapping
    @Operation(summary = "Gets cloud access policy for specified user and cloud region")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CloudAccessPolicy> getCloudPermissions(@RequestParam final String username,
                                                         @RequestParam final Long regionId) {
        return Result.success(cloudAccessApiService.getCloudUserAccessPermissions(regionId, username));
    }

    @PutMapping
    @Operation(summary = "Updates cloud access policy for specified user and cloud region")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CloudAccessPolicy> updateCloudPermissions(
            @RequestParam final String username, @RequestParam final Long regionId,
            @RequestBody final CloudAccessPolicy policy) {
        return Result.success(cloudAccessApiService.updateCloudUserAccessPermissions(regionId, username, policy));
    }

    @DeleteMapping
    @Operation(summary = "Deletes cloud access policy for specified user and cloud region")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result deleteCloudPermissions(@RequestParam final String username, @RequestParam final Long regionId) {
        cloudAccessApiService.revokeCloudUserAccessPermissions(regionId, username);
        return Result.success();
    }

    @GetMapping("/keys")
    @Operation(summary = "Gets cloud access keys for specified user and cloud region")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CloudUserAccessKeys> getCloudAccessKeys(@RequestParam final String username,
                                                          @RequestParam final Long regionId) {
        return Result.success(cloudAccessApiService.getKeys(regionId, username));
    }
    @PostMapping("/keys")
    @Operation(summary = "Generates cloud access keys for specified user and cloud region")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CloudUserAccessKeys> createCloudAccessKeys(
            @RequestParam final String username,
            @RequestParam final Long regionId,
            @RequestParam(defaultValue = "false") final boolean force) {
        return Result.success(cloudAccessApiService.generateKeys(regionId, username, force));
    }

    @DeleteMapping("/keys")
    @Operation(summary = "Revoke cloud access keys for specified user and cloud region")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result revokeAccessKeys(@RequestParam final String username, @RequestParam final Long regionId) {
        cloudAccessApiService.revokeKeys(regionId, username);
        return Result.success();
    }
}

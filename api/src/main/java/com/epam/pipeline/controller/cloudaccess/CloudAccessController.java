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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Api(value = "Cloud access management methods")
@RequestMapping(value = "/cloud/access")
@RequiredArgsConstructor
public class CloudAccessController extends AbstractRestController {

    private final CloudAccessApiService cloudAccessApiService;

    @GetMapping("/profile")
    @ApiOperation(value = "Gets cloud access profile for specified user across all regions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudUserAccessProfile> getCloudPermissions(@RequestParam final String username) {
        return Result.success(cloudAccessApiService.getCloudUserProfile(username));
    }

    @GetMapping
    @ApiOperation(value = "Gets cloud access policy for specified user and cloud region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudAccessPolicy> getCloudPermissions(@RequestParam final String username,
                                                         @RequestParam final Long regionId) {
        return Result.success(cloudAccessApiService.getCloudUserAccessPermissions(regionId, username));
    }

    @PutMapping
    @ApiOperation(value = "Updates cloud access policy for specified user and cloud region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudAccessPolicy> updateCloudPermissions(
            @RequestParam final String username, @RequestParam final Long regionId,
            @RequestBody final CloudAccessPolicy policy) {
        return Result.success(cloudAccessApiService.updateCloudUserAccessPermissions(regionId, username, policy));
    }

    @DeleteMapping
    @ApiOperation(value = "Deletes cloud access policy for specified user and cloud region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result deleteCloudPermissions(@RequestParam final String username, @RequestParam final Long regionId) {
        cloudAccessApiService.revokeCloudUserAccessPermissions(regionId, username);
        return Result.success();
    }

    @GetMapping("/keys")
    @ApiOperation(value = "Gets cloud access keys for specified user and cloud region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudUserAccessKeys> getCloudAccessKeys(@RequestParam final String username,
                                                          @RequestParam final Long regionId) {
        return Result.success(cloudAccessApiService.getKeys(regionId, username));
    }
    @PostMapping("/keys")
    @ApiOperation(value = "Generates cloud access keys for specified user and cloud region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudUserAccessKeys> createCloudAccessKeys(
            @RequestParam final String username,
            @RequestParam final Long regionId,
            @RequestParam(defaultValue = "false") final boolean force) {
        return Result.success(cloudAccessApiService.generateKeys(regionId, username, force));
    }

    @DeleteMapping("/keys")
    @ApiOperation(value = "Revoke cloud access keys for specified user and cloud region",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result revokeAccessKeys(@RequestParam final String username, @RequestParam final Long regionId) {
        cloudAccessApiService.revokeKeys(regionId, username);
        return Result.success();
    }
}

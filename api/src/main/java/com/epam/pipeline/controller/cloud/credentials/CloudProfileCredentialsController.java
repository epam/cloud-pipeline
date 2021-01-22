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

package com.epam.pipeline.controller.cloud.credentials;

import com.epam.pipeline.acl.cloud.credentials.CloudProfileCredentialsApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentials;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Api(value = "Cloud profile credentials management methods")
@RequestMapping(value = "/cloud/credentials")
@RequiredArgsConstructor
public class CloudProfileCredentialsController extends AbstractRestController {
    private final CloudProfileCredentialsApiService cloudProfileCredentialsApiService;

    @PostMapping
    @ApiOperation(value = "Creates cloud profile credentials object", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudProfileCredentials> create(@RequestBody final CloudProfileCredentials credentials) {
        return Result.success(cloudProfileCredentialsApiService.create(credentials));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "Gets the cloud profile credentials object by identifier",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudProfileCredentials> get(@PathVariable final Long id) {
        return Result.success(cloudProfileCredentialsApiService.get(id));
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "Updates cloud profile credentials object by id",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudProfileCredentials> update(@PathVariable final Long id,
                                                  @RequestBody final CloudProfileCredentials credentials) {
        return Result.success(cloudProfileCredentialsApiService.update(id, credentials));
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "Deletes cloud profile credentials object", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudProfileCredentials> delete(@PathVariable final Long id) {
        return Result.success(cloudProfileCredentialsApiService.delete(id));
    }

    @GetMapping
    @ApiOperation(value = "Loads all cloud profile credentials", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<? extends CloudProfileCredentials>> findAll() {
        return Result.success(cloudProfileCredentialsApiService.findAll());
    }

    @GetMapping("/profiles")
    @ApiOperation(value = "Loads all cloud profile credentials associated with a user or a role",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<? extends CloudProfileCredentials>> getProfilesByUserOrRole(
            @RequestParam final Long id, @RequestParam final boolean principal) {
        return Result.success(cloudProfileCredentialsApiService.getProfilesByUserOrRole(id, principal));
    }

    @PostMapping("/profiles/{profileId}")
    @ApiOperation(value = "",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<CloudProfileCredentials> attachProfileToUserOrRole(@PathVariable final Long profileId,
            @RequestParam final Long id, @RequestParam final boolean principal) {
        return Result.success(cloudProfileCredentialsApiService.attachProfileToUserOrRole(profileId, id, principal));
    }
}

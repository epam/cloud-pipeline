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

package com.epam.pipeline.controller.firecloud;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.google.RefreshToken;
import com.epam.pipeline.entity.firecloud.FirecloudMethod;
import com.epam.pipeline.entity.firecloud.FirecloudMethodConfiguration;
import com.epam.pipeline.entity.firecloud.FirecloudMethodParameters;
import com.epam.pipeline.exception.GoogleAccessException;
import com.epam.pipeline.manager.firecloud.FirecloudApiService;
import com.epam.pipeline.manager.google.CredentialsManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Firecloud methods")
@RequestMapping(value = "/firecloud")
@Slf4j
@AllArgsConstructor
public class FirecloudController extends AbstractRestController {

    private FirecloudApiService firecloudApiService;
    private CredentialsManager credentialsManager;
    private MessageHelper messageHelper;

    @GetMapping(value = "/auth")
    @ApiOperation(
            value = "Exchanges Google auth code to user refresh token.",
            notes = "Exchanges Google auth code to user refresh token.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<RefreshToken> processAuthorizationCode(@RequestParam String authorizationCode) {
        if (!StringUtils.hasText(authorizationCode)) {
            throw new GoogleAccessException(
                    messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_AUTH_CODE_MISSING));
        }
        return Result.success(credentialsManager.issueTokenFromAuthCode(authorizationCode));
    }

    @GetMapping(value = "/methods")
    @ApiOperation(
            value = "Lists Method Repository methods.",
            notes = "Lists Method Repository methods.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<FirecloudMethod>> loadAllMethods(
            @RequestHeader(value = Constants.FIRECLOUD_TOKEN_HEADER, required = false) String refreshToken) {
        return Result.success(firecloudApiService.loadAll(refreshToken));
    }

    @GetMapping(value = "/methods/{namespace}/{method}/{snapshot}/configurations")
    @ApiOperation(
            value = "Returns configurations of a method specified by workspace, method name, and snapshotId.",
            notes = "Returns configurations of a method specified by workspace, method name, and snapshotId.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<FirecloudMethodConfiguration>> loadMethodConfigurations(
            @RequestHeader(value = Constants.FIRECLOUD_TOKEN_HEADER, required = false) String refreshToken,
            @PathVariable final String namespace,
            @PathVariable final String method,
            @PathVariable final Long snapshot) {
        return Result.success(firecloudApiService.loadMethodConfigurations(refreshToken, namespace, method, snapshot));
    }

    @GetMapping(value = "/methods/{namespace}/{method}/{snapshot}/parameters")
    @ApiOperation(
            value = "Returns inputs/outputs and wdl of a method specified by workspace, method name, and snapshotId.",
            notes = "Returns inputs/outputs and wdl of a method specified by workspace, method name, and snapshotId.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<FirecloudMethodParameters> loadMethodParameters(
            @RequestHeader(value = Constants.FIRECLOUD_TOKEN_HEADER, required = false) String refreshToken,
            @PathVariable final String namespace,
            @PathVariable final String method,
            @PathVariable final Long snapshot) {
        return Result.success(firecloudApiService.loadMethodParameters(refreshToken, namespace, method, snapshot));
    }
}

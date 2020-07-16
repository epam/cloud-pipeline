/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.configuration;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.manager.configuration.ServerlessConfigurationApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@Controller
@Api(value = "Serverless configuration methods")
@RequiredArgsConstructor
public class ServerlessConfigurationController extends AbstractRestController {

    private final ServerlessConfigurationApiService serverlessConfigurationApiService;

    @GetMapping(value = "/serverless/url/{id}")
    @ResponseBody
    @ApiOperation(
            value = "Generates serverless url.",
            notes = "Generates serverless url.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<String> generateUrl(@PathVariable final Long id,
                                      @RequestParam(required = false) final String config) {
        return Result.success(serverlessConfigurationApiService.generateUrl(id, config));
    }

    @RequestMapping(value = "/serverless/{id}/{config}/**", method = {
            RequestMethod.POST,
            RequestMethod.GET,
            RequestMethod.PUT,
            RequestMethod.DELETE})
    @ResponseBody
    @ApiOperation(
            value = "Launches serverless configuration request",
            notes = "Launches serverless configuration request",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public String run(@PathVariable("id") final Long id, @PathVariable("config") final String config,
                      final HttpServletRequest request) {
        return serverlessConfigurationApiService.run(id, config, request);
    }
}

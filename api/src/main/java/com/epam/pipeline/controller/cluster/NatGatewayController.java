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

package com.epam.pipeline.controller.cluster;

import com.epam.pipeline.acl.cluster.NatGatewayApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.cluster.nat.NatRoute;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRulesRequest;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController("/nat")
@RequiredArgsConstructor
public class NatGatewayController extends AbstractRestController {

    private final NatGatewayApiService natGatewayApiService;

    @PostMapping(value = "/resolve")
    @ResponseBody
    @ApiOperation(
        value = "Resolve IP for the given hostname.",
        notes = "Resolve IP for the given hostname.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<Set<String>> resolveAddress(@RequestParam final String hostname) {
        return Result.success(natGatewayApiService.resolveAddress(hostname));
    }

    @PostMapping("/rules")
    @ResponseBody
    @ApiOperation(
        value = "Schedule new routing rules registration.",
        notes = "Schedule new routing rules registration.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<List<NatRoute>> registerRoutingRulesCreation(@RequestBody final NatRoutingRulesRequest request) {
        return Result.success(natGatewayApiService.registerRoutingRulesCreation(request));
    }

    @GetMapping("/rules")
    @ResponseBody
    @ApiOperation(
        value = "Loads all routing rules.",
        notes = "Loads both queued and active routing rules.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<List<NatRoute>> loadAllRoutes() {
        return Result.success(natGatewayApiService.loadAllRoutes());
    }

    @DeleteMapping("/rules")
    @ResponseBody
    @ApiOperation(
        value = "Mark a routing rule as the one to be removed.",
        notes = "Mark a routing rule as the one to be removed.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<List<NatRoute>> registerRoutingRulesRemoval(@RequestBody final NatRoutingRulesRequest request) {
        return Result.success(natGatewayApiService.registerRoutingRulesRemoval(request));
    }
}

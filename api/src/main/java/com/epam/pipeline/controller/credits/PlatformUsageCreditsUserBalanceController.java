/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.credits;

import com.epam.pipeline.acl.credits.PlatformUsageCreditsUserBalanceApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsResetRequest;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Platform usage credits user balances management")
@RequestMapping("/usage/credits/users")
@RequiredArgsConstructor
public class PlatformUsageCreditsUserBalanceController extends AbstractRestController {

    private final PlatformUsageCreditsUserBalanceApiService apiService;

    @PostMapping
    @ApiOperation(
            value = "Loads current user balances with optional filters. Admin only.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PagedResult<List<PlatformUsageCreditsUserBalance>>> filter(
            @RequestBody final PlatformUsageCreditsUserBalanceFilterVO filter) {
        return Result.success(apiService.filter(filter));
    }

    @PostMapping("/reset")
    @ApiOperation(
            value = "Resets the credits balance for specific users, or for all users if userIds is omitted. "
                    + "Admin only.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Void> reset(@RequestBody final PlatformUsageCreditsResetRequest resetRequest) {
        apiService.reset(resetRequest);
        return Result.success(null);
    }

    @GetMapping("/balance")
    @ApiOperation(
            value = "Returns the credits balance for a specific user including currently allocated credits. "
                    + "Admins can query any user; non-admins can only query their own balance.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PlatformUsageCreditsUserBalance> getBalanceWithAllocated(
            @RequestParam final Long userId) {
        return Result.success(apiService.getBalanceWithAllocated(userId));
    }
}

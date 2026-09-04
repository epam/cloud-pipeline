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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "platform-usage-credits-user-balance-controller",
        description = "Platform usage credits user balances management")
@RequestMapping("/usage/credits/users")
@RequiredArgsConstructor
public class PlatformUsageCreditsUserBalanceController extends AbstractRestController {

    private final PlatformUsageCreditsUserBalanceApiService apiService;

    @PostMapping
    @Operation(
            summary = "Loads current user balances with optional filters. Admin only.",
            description = "Loads current user balances with optional filters. Admin only.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PagedResult<List<PlatformUsageCreditsUserBalance>>> filter(
            @RequestBody final PlatformUsageCreditsUserBalanceFilterVO filter) {
        return Result.success(apiService.filter(filter));
    }

    @PostMapping("/reset")
    @Operation(
            summary = "Resets the credits balance for specific users, or for all users if userIds is omitted. "
                    + "Admin only.",
            description = "Resets the credits balance for specific users, or for all users if userIds is omitted. "
                    + "Admin only.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Void> reset(@RequestBody final PlatformUsageCreditsResetRequest resetRequest) {
        apiService.reset(resetRequest);
        return Result.success(null);
    }

    @GetMapping("/balance")
    @Operation(
            summary = "Returns the credits balance for a specific user including currently allocated credits. "
                    + "Admins can query any user; non-admins can only query their own balance.",
            description = "Returns the credits balance for a specific user including currently allocated credits. "
                    + "Admins can query any user; non-admins can only query their own balance.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PlatformUsageCreditsUserBalance> getBalanceWithAllocated(
            @RequestParam final Long userId) {
        return Result.success(apiService.getBalanceWithAllocated(userId));
    }
}

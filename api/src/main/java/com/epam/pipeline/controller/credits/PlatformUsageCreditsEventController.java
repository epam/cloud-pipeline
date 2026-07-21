/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.controller.credits;

import com.epam.pipeline.acl.credits.PlatformUsageCreditsEventApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsEventFilterVO;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsResetRequest;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRequest;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Platform usage credits events management")
@RequestMapping("/usage/credits/events")
@RequiredArgsConstructor
public class PlatformUsageCreditsEventController extends AbstractRestController {

    private final PlatformUsageCreditsEventApiService apiService;

    @PostMapping
    @ApiOperation(
            value = "Saves platform usage credits update events. Admin only.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<PlatformUsageCreditsUpdateEvent>> process(
            @RequestBody final List<PlatformUsageCreditsUpdateRequest> requests) {
        return Result.success(apiService.process(requests));
    }

    @PostMapping("/filter")
    @ApiOperation(
            value = "Filters platform usage credits update events with pagination. "
                    + "Non-admin users are restricted to their own events.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PagedResult<List<PlatformUsageCreditsUpdateEvent>>> filter(
            @RequestBody final PlatformUsageCreditsEventFilterVO filter) {
        return Result.success(apiService.filter(filter));
    }

    @PostMapping("/reset")
    @ApiOperation(
            value = "Resets platform usage credits to the given value for the specified users. "
                    + "If userIds is omitted, resets for all users. Admin only.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<PlatformUsageCreditsUpdateEvent>> reset(
            @RequestBody final PlatformUsageCreditsResetRequest resetRequest) {
        return Result.success(apiService.reset(resetRequest));
    }
}

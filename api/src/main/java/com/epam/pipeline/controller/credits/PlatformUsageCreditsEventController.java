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
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@Tag(name = "platform-usage-credits-event-controller", description = "Platform usage credits events management")
@RequestMapping("/usage/credits/events")
@RequiredArgsConstructor
public class PlatformUsageCreditsEventController extends AbstractRestController {

    private final PlatformUsageCreditsEventApiService apiService;

    @PostMapping
    @Operation(
            summary = "Send platform usage credits update events. Admin only.",
            description = "Send platform usage credits update events. Admin only.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<PlatformUsageCreditsUpdateEvent>> process(
            @RequestBody final List<PlatformUsageCreditsUpdateEvent> events) {
        return Result.success(apiService.process(events));
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filters platform usage credits update events with pagination. "
                    + "Non-admin users are restricted to their own events.",
            description = "Filters platform usage credits update events with pagination. "
                    + "Non-admin users are restricted to their own events.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PagedResult<List<PlatformUsageCreditsUpdateEvent>>> filter(
            @RequestBody final PlatformUsageCreditsEventFilterVO filter) {
        return Result.success(apiService.filter(filter));
    }

    @PostMapping("/export")
    @Operation(
            summary = "Exports all matching credits events as CSV. "
                    + "Non-admin users are restricted to their own events. "
                    + "Pagination fields in the request body are ignored.",
            description = "Exports all matching credits events as CSV. "
                    + "Non-admin users are restricted to their own events. "
                    + "Pagination fields in the request body are ignored.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public void export(@RequestBody final PlatformUsageCreditsEventFilterVO filter,
                       final HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment;filename=credits_events.csv");
        apiService.export(filter, response.getOutputStream());
    }
}

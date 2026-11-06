/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.log;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.acl.log.LogApiService;
import com.epam.pipeline.entity.log.LogRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "log-controller", description = "Log Controller")
public class LogController extends AbstractRestController {

    private final LogApiService logApiService;


    @PostMapping(value = "/log/filter")
    @Operation(summary = "Filter logs.", description = "Filter logs.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<LogPagination> filter(@RequestBody LogFilter logFilter) {
        return Result.success(logApiService.filter(logFilter));
    }

    @GetMapping(value = "/log/filter")
    @Operation(summary = "Get possible values for filters.", description = "Get possible values for filters.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<LogFilter> filter() {
        return Result.success(logApiService.getFilters());
    }

    @PostMapping(value = "/log/group")
    @Operation(summary = "Filter and group logs by a field.", description = "Filter and group logs by a field.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Map<String, Long>> group(@RequestBody final LogRequest logRequest) {
        return Result.success(logApiService.group(logRequest));
    }

    @PostMapping(value = "/log")
    @Operation(summary = "Save logs.", description = "Save logs.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Boolean> save(@RequestBody final List<LogEntry> logEntries) {
        logApiService.save(logEntries);
        return Result.success();
    }
}

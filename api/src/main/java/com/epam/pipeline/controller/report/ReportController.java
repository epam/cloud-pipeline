/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.report;

import com.epam.pipeline.acl.report.ReportApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.report.ReportFilter;
import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.dto.report.NodePoolReportType;
import com.epam.pipeline.dto.report.UsersUsageInfo;
import com.epam.pipeline.dto.report.UsersUsageReportFilterVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@Api(value = "Statistics reporting")
@RequestMapping(value = "/report")
@RequiredArgsConstructor
public class ReportController extends AbstractRestController {
    private static final String REPORT_NAME_TEMPLATE = "node_pool_report_%s-%s-%s.%s";
    private static final char TIME_SEPARATION_CHAR = ':';
    private static final char UNDERSCORE = '_';

    private final ReportApiService reportApiService;

    @PostMapping("/users")
    @ApiOperation(value = "Reports users statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<UsersUsageInfo>> loadUsersUsage(@RequestBody final UsersUsageReportFilterVO filter) {
        return Result.success(reportApiService.loadUsersUsage(filter));
    }

    @PostMapping("/pools")
    @ApiOperation(value = "Reports node pools usage statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<NodePoolUsageReport>> loadNodePoolUsage(@RequestBody final ReportFilter filter) {
        return Result.success(reportApiService.loadNodePoolReport(filter));
    }

    @PostMapping("/pools/export")
    @ResponseBody
    @ApiOperation(value = "Downloads node pools usage report", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public void downloadUsageReport(@RequestBody final ReportFilter filter,
                                    @RequestParam final Long poolId,
                                    @RequestParam(defaultValue = "CSV") final NodePoolReportType type,
                                    final HttpServletResponse response) throws IOException {
        final InputStream inputStream = reportApiService.loadNodePoolUsageReportFile(filter, poolId, type);
        final String reportName = String.format(REPORT_NAME_TEMPLATE, filter.getFrom(), filter.getTo(),
                filter.getInterval(), type.name().toLowerCase())
                .replace(TIME_SEPARATION_CHAR, UNDERSCORE);
        writeStreamToResponse(response, inputStream, reportName);
    }
}

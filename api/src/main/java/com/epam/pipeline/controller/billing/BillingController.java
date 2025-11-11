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

package com.epam.pipeline.controller.billing;

import com.epam.pipeline.acl.billing.BillingApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.billing.BillingChartRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingChartInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BillingController extends AbstractRestController {

    private final BillingApiService billingApi;

    @RequestMapping(value = "/billing/charts", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
        summary = "Get info for building expenses charts.",
        description = "Get info for building expenses charts.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<BillingChartInfo>> getBillingChartInfo(@RequestBody final BillingChartRequest request) {
        return Result.success(billingApi.getBillingChartInfo(request));
    }

    @RequestMapping(value = "/billing/charts/pagination", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
        summary = "Get paginated info about billing expenses.",
        description = "Get paginated info about billing expenses.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<BillingChartInfo>> getBillingChartInfoPaginated(@RequestBody final BillingChartRequest request) {
        return Result.success(billingApi.getBillingChartInfoPaginated(request));
    }

    @RequestMapping(value = "/billing/export", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Export raw data for billing expenses.",
            description = "Export raw data for billing expenses.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public void export(@RequestBody final BillingExportRequest request,
                       final HttpServletResponse response) throws IOException {
        writeToResponse(response, billingApi.export(request));
    }

    @GetMapping(value = "/billing/centers")
    @ResponseBody
    @Operation(
        summary = "Get list containing all billing centers.",
        description = "Get list containing all billing centers.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<String>> getAllBillingCenters() {
        return Result.success(billingApi.getAllBillingCenters());
    }
}

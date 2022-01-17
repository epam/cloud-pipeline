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
import com.epam.pipeline.entity.billing.BillingReportTemplate;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BillingController extends AbstractRestController {

    private final BillingApiService billingApi;

    @RequestMapping(value = "/billing/charts", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
        value = "Get info for building expenses charts.",
        notes = "Get info for building expenses charts.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<BillingChartInfo>> getBillingChartInfo(@RequestBody final BillingChartRequest request) {
        return Result.success(billingApi.getBillingChartInfo(request));
    }

    @RequestMapping(value = "/billing/charts/pagination", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
        value = "Get paginated info about billing expenses.",
        notes = "Get paginated info about billing expenses.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<BillingChartInfo>> getBillingChartInfoPaginated(@RequestBody final BillingChartRequest request) {
        return Result.success(billingApi.getBillingChartInfoPaginated(request));
    }

    @RequestMapping(value = "/billing/export", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Export raw data for billing expenses.",
            notes = "Export raw data for billing expenses.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public void export(@RequestBody final BillingExportRequest request,
                       final HttpServletResponse response) throws IOException {
        writeToResponse(response, billingApi.export(request));
    }

    @GetMapping(value = "/billing/centers")
    @ResponseBody
    @ApiOperation(
        value = "Get list containing all billing centers.",
        notes = "Get list containing all billing centers.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<String>> getAllBillingCenters() {
        return Result.success(billingApi.getAllBillingCenters());
    }

    @RequestMapping(value = "/billing/facets", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Get info for available fields to filter on.",
            notes = "Get info for available fields to filter on.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<FacetedSearchResult> getAvailableFacets(@RequestBody final BillingChartRequest request) {
        return Result.success(billingApi.getAvailableFields(request));
    }

    @RequestMapping(value = "/billing/templates", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "List available custom billing report templates.",
            notes = "List available custom billing report templates.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<BillingReportTemplate>> getAvailableCustomBillingTemplates() {
        return Result.success(billingApi.getAvailableCustomBillingTemplates());
    }

    @RequestMapping(value = "/billing/templates/{id}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Get custom billing report template by id.",
            notes = "Get custom billing report template by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<BillingReportTemplate> getCustomBillingTemplates(@PathVariable final Long id) {
        return Result.success(billingApi.getCustomBillingTemplate(id));
    }

    @RequestMapping(value = "/billing/templates/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Delete custom billing report template by id.",
            notes = "Delete custom billing report template by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result deleteCustomBillingTemplates(@PathVariable final Long id) {
        billingApi.deleteCustomBillingTemplate(id);
        return Result.success();
    }

    @RequestMapping(value = "/billing/templates", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Create custom billing report template.",
            notes = "Create custom billing report template.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<BillingReportTemplate> createCustomBillingTemplates(
            @RequestBody final BillingReportTemplate template) {
        return Result.success(billingApi.createCustomBillingTemplates(template));
    }

    @RequestMapping(value = "/billing/templates", method = RequestMethod.PUT)
    @ResponseBody
    @ApiOperation(
            value = "Create custom billing report template.",
            notes = "Create custom billing report template.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<BillingReportTemplate> updateCustomBillingTemplates(
            @RequestBody final BillingReportTemplate template) {
        return Result.success(billingApi.updateCustomBillingTemplates(template));
    }
}

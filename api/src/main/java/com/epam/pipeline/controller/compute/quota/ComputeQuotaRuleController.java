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

package com.epam.pipeline.controller.compute.quota;

import com.epam.pipeline.acl.compute.quota.ComputeQuotaRuleApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaRule;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Compute quota rules management")
@RequestMapping("/compute/quotas/rules")
@RequiredArgsConstructor
public class ComputeQuotaRuleController extends AbstractRestController {

    private final ComputeQuotaRuleApiService apiService;

    @GetMapping
    @ApiOperation(
            value = "Loads all registered compute quota rules.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<ComputeQuotaRule>> loadAll() {
        return Result.success(apiService.loadAll());
    }

    @PostMapping
    @ApiOperation(
            value = "Registers a new compute quota rule. Admin only.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<ComputeQuotaRule> create(@RequestBody final ComputeQuotaRule rule) {
        return Result.success(apiService.create(rule));
    }

    @PutMapping("/{id}")
    @ApiOperation(
            value = "Updates compute quota rule by ID. Admin only.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<ComputeQuotaRule> update(@PathVariable final Long id,
                                           @RequestBody final ComputeQuotaRule rule) {
        return Result.success(apiService.update(id, rule));
    }

    @DeleteMapping("/{id}")
    @ApiOperation(
            value = "Deletes compute quota rule by ID. Admin only.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Void> delete(@PathVariable final Long id) {
        apiService.delete(id);
        return Result.success(null);
    }

    @GetMapping("/keywords")
    @ApiOperation(
            value = "Returns allowed filter fields for compute quota rule expressions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<FilterFieldVO>> getKeywords() {
        return Result.success(apiService.getKeywords());
    }
}

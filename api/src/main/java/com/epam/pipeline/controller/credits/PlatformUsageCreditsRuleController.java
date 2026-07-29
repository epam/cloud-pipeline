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

import com.epam.pipeline.acl.credits.PlatformUsageCreditsRuleApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "platform-usage-credits-rule-controller", description = "Platform usage credits rules management")
@RequestMapping("/usage/credits/rules")
@RequiredArgsConstructor
public class PlatformUsageCreditsRuleController extends AbstractRestController {

    private final PlatformUsageCreditsRuleApiService apiService;

    @GetMapping
    @Operation(
            summary = "Loads all registered platform usage credits update rules.",
            description = "Loads all registered platform usage credits update rules.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<PlatformUsageCreditsUpdateRule>> loadAll() {
        return Result.success(apiService.loadAll());
    }

    @PostMapping
    @Operation(
            summary = "Registers a new platform usage credits update rule. Admin only.",
            description = "Registers a new platform usage credits update rule. Admin only.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PlatformUsageCreditsUpdateRule> create(@RequestBody final PlatformUsageCreditsUpdateRule rule) {
        return Result.success(apiService.create(rule));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Updates platform usage credits update rule by ID. Admin only.",
            description = "Updates platform usage credits update rule by ID. Admin only.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PlatformUsageCreditsUpdateRule> update(@PathVariable final Long id,
                                                         @RequestBody final PlatformUsageCreditsUpdateRule rule) {
        return Result.success(apiService.update(id, rule));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Deletes platform usage credits update rule by ID. Admin only.",
            description = "Deletes platform usage credits update rule by ID. Admin only.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Void> delete(@PathVariable final Long id) {
        apiService.delete(id);
        return Result.success(null);
    }

    @GetMapping("/keywords")
    @Operation(
            summary = "Returns allowed filter fields for platform usage credits update rule expressions.",
            description = "Returns allowed filter fields for platform usage credits update rule expressions.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Map<String, List<FilterFieldVO>>> getKeywords() {
        return Result.success(apiService.getKeywords());
    }
}

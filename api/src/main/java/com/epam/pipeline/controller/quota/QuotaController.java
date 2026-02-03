/*
 * Copyright 2021-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.quota;

import com.epam.pipeline.acl.quota.QuotaApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.quota.Quota;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Quotas management")
@RequestMapping("/quotas")
@RequiredArgsConstructor
public class QuotaController extends AbstractRestController {
    private final QuotaApiService quotaApiService;

    @PostMapping
    @Operation(summary = "Creates quota")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Quota> create(@RequestBody final Quota quota) {
        return Result.success(quotaApiService.create(quota));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Gets the quota by ID")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Quota> get(@PathVariable final Long id) {
        return Result.success(quotaApiService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Updates quota by ID")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Quota> update(@PathVariable final Long id, @RequestBody final Quota quota) {
        return Result.success(quotaApiService.update(id, quota));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletes quota")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public void delete(@PathVariable final Long id) {
        quotaApiService.delete(id);
    }

    @GetMapping
    @Operation(summary = "Gets all quotas")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<Quota>> getAll(
            @RequestParam(required = false, defaultValue = "false") final boolean loadActive) {
        return Result.success(quotaApiService.getAll(loadActive));
    }
}

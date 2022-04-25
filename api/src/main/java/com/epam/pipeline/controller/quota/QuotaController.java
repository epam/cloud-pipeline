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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Quotas management")
@RequestMapping("/quotas")
@RequiredArgsConstructor
public class QuotaController extends AbstractRestController {
    private final QuotaApiService quotaApiService;

    @PostMapping
    @ApiOperation(value = "Creates quota", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Quota> create(@RequestBody final Quota quota) {
        return Result.success(quotaApiService.create(quota));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "Gets the quota by ID", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Quota> get(@PathVariable final Long id) {
        return Result.success(quotaApiService.get(id));
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "Updates quota by ID", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Quota> update(@PathVariable final Long id, @RequestBody final Quota quota) {
        return Result.success(quotaApiService.update(id, quota));
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "Deletes quota", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public void delete(@PathVariable final Long id) {
        quotaApiService.delete(id);
    }

    @GetMapping
    @ApiOperation(value = "Gets all quotas", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<Quota>> getAll(
            @RequestParam(required = false, defaultValue = "false") final boolean loadActive) {
        return Result.success(quotaApiService.getAll(loadActive));
    }
}

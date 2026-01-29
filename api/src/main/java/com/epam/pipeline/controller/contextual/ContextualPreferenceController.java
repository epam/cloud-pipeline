/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.contextual;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.contextual.ContextualPreferenceSearchRequest;
import com.epam.pipeline.acl.contextual.ContextualPreferenceApiService;




import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Contextual preference")
@RequestMapping(value = "/contextual/preference")
@RequiredArgsConstructor
public class ContextualPreferenceController extends AbstractRestController {

    private final ContextualPreferenceApiService contextualPreferenceApiService;

    @GetMapping("/load/all")
    @Operation(
            summary = "Lists all contextual preferences.",
            description = "Lists all contextual preferences.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<ContextualPreference>> loadAll() {
        return Result.success(contextualPreferenceApiService.loadAll());
    }

    @GetMapping("/load")
    @Operation(
            summary = "Loads contextual preference by its name, level and resourceId.",
            description = "Loads contextual preference by its name, level and resourceId.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ContextualPreference> load(@RequestParam final String name,
                                             @RequestParam final ContextualPreferenceLevel level,
                                             @RequestParam final String resourceId) {
        return Result.success(contextualPreferenceApiService.load(name,
                new ContextualPreferenceExternalResource(level, resourceId)));
    }

    @PostMapping
    @Operation(
            summary = "Searches for a contextual preference by the given search request.",
            description = "Searches for a contextual preference by the given search request.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ContextualPreference> search(@RequestBody final ContextualPreferenceSearchRequest searchRequest) {
        return Result.success(contextualPreferenceApiService.search(searchRequest.preferences(),
                searchRequest.resource()));
    }

    @PutMapping
    @Operation(
            summary = "Updates or creates contextual preference",
            description = "Updates or creates contextual preference")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ContextualPreference> update(@RequestBody final ContextualPreferenceVO preference) {
        return Result.success(contextualPreferenceApiService.upsert(preference));
    }

    @DeleteMapping
    @Operation(
            summary = "Deletes contextual preference",
            description = "Deletes contextual preference")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ContextualPreference> delete(@RequestParam final String name,
                                               @RequestParam final ContextualPreferenceLevel level,
                                               @RequestParam final String resourceId) {
        return Result.success(contextualPreferenceApiService.delete(name,
                new ContextualPreferenceExternalResource(level, resourceId)));
    }
}

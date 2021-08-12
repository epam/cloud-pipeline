/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.metadata;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.acl.metadata.CategoricalAttributeApiService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "CategoricalAttributes")
@RequiredArgsConstructor
@RequestMapping(value = "/categoricalAttribute")
public class CategoricalAttributeController extends AbstractRestController {

    private final CategoricalAttributeApiService categoricalAttributeApiService;

    @PostMapping
    @ApiOperation(
        value = "Create new categorical attribute.",
        notes = "Create new categorical attribute.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<CategoricalAttribute> updateCategoricalAttribute(@RequestBody final CategoricalAttribute attribute) {
        return Result.success(categoricalAttributeApiService.updateCategoricalAttribute(attribute));
    }

    @GetMapping
    @ApiOperation(
        value = "Load all categorical attributes with values.",
        notes = "Load all categorical attributes with values.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<List<CategoricalAttribute>> loadAllCategoricalAttributes() {
        return Result.success(categoricalAttributeApiService.loadAll());
    }

    @GetMapping(value = "{attributeKey}")
    @ApiOperation(
        value = "Load requested attribute with all values.",
        notes = "Load requested attribute with all values.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<CategoricalAttribute> loadCategoricalAttribute(@PathVariable final String attributeKey) {
        return Result.success(categoricalAttributeApiService.loadAllValuesForKey(attributeKey));
    }

    @DeleteMapping(value = "{attributeKey}")
    @ApiOperation(
        value = "Delete values for a requested attribute.",
        notes = "Delete one specific value for a requested attribute if `value` parameter is specified, or all of the "
                + "values otherwise.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<Boolean> deleteAttributeValue(@PathVariable final String attributeKey,
                                                @RequestParam(required = false) final String value) {
        return Result.success(value != null
                              ? categoricalAttributeApiService.deleteAttributeValue(attributeKey, value)
                              : categoricalAttributeApiService.deleteAttributeValues(attributeKey));
    }
}

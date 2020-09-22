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

package com.epam.pipeline.controller.metadata;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.manager.metadata.CategoricalAttributeApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Api(value = "CategoricalAttributes")
@RequiredArgsConstructor
@RequestMapping(value = "/categoricalAttribute")
public class CategoricalAttributeController extends AbstractRestController {

    private final CategoricalAttributeApiService categoricalAttributeApiService;

    @PostMapping
    @ApiOperation(
        value = "Add categorical attributes values.",
        notes = "Add categorical attributes values",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<Boolean> insertCategoricalAttributes(@RequestBody final Map<String, List<String>> dict) {
        return Result.success(categoricalAttributeApiService.insertAttributesValues(dict));
    }

    @GetMapping("/loadAll")
    @ApiOperation(
        value = "Load all categorical attributes with values.",
        notes = "Load all categorical attributes with values.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<Map<String, List<String>>> loadAllCategoricalAttributes() {
        return Result.success(categoricalAttributeApiService.loadAll());
    }

    @GetMapping("/load")
    @ApiOperation(
        value = "Load all requested attributes with values.",
        notes = "Load all requested attributes with values.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<Map<String, List<String>>> loadCategoricalAttributes(@RequestParam final String attributeKey) {
        return Result.success(categoricalAttributeApiService.loadAllValuesForKey(attributeKey));
    }

    @DeleteMapping("/delete")
    @ApiOperation(
        value = "Delete all values for a requested attribute.",
        notes = "Delete all values for a requested attribute.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<Boolean> deleteAllAttributeValues(@RequestParam final String attributeKey) {
        return Result.success(categoricalAttributeApiService.deleteAttributeValues(attributeKey));
    }

    @DeleteMapping("/deleteValue")
    @ApiOperation(
        value = "Delete one specific value for a requested attribute.",
        notes = "Delete one specific value for a requested attribute.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<Boolean> deleteAttributeValue(@RequestParam final String attributeKey,
                                                @RequestParam final String value) {
        return Result.success(categoricalAttributeApiService.deleteAttributeValue(attributeKey, value));
    }

    @PostMapping("/sync")
    @ApiOperation(
        value = "Fill in categorical attributes table based on a current metadata.",
        notes = "Fill in categorical attributes table based on a current metadata.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public void syncCategoricalAttributesWithMetadata() {
        categoricalAttributeApiService.syncWithMetadata();
    }
}

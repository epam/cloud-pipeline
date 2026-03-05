/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.datastorage.tag;

import com.epam.pipeline.acl.datastorage.tag.DataStorageTagBatchApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteAllBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagUpsertBatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Tag(name = "data-storage-tag-batch-controller", description = "Data storage object tag batch methods. " +
        "NOTE: all these methods assume that client provide absolute path for a storage object " +
        "(path from datastorage root object and not from storage itself) within BatchRequest")
public class DataStorageTagBatchController extends AbstractRestController {

    private static final String ID = "id";

    @Autowired
    private DataStorageTagBatchApiService dataStorageTagBatchApiService;

    @RequestMapping(value = "/datastorage/{id}/tags/batch/insert", method = RequestMethod.PUT)
    @ResponseBody
    @Operation(
            summary = "Inserts data storage object tags replacing already existing ones.",
            description = "Inserts data storage object tags replacing already existing ones.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result insert(@PathVariable(value = ID) final Long id,
                         @RequestBody final DataStorageTagInsertBatchRequest request) {
        dataStorageTagBatchApiService.insert(id, request);
        return Result.success();
    }

    @RequestMapping(value = "/datastorage/{id}/tags/batch/upsert", method = RequestMethod.PUT)
    @ResponseBody
    @Operation(
            summary = "Upserts data storage object tags overriding already existing ones.",
            description = "Upserts data storage object tags overriding already existing ones.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result upsert(@PathVariable(value = ID) final Long id,
                         @RequestBody final DataStorageTagUpsertBatchRequest request) {
        dataStorageTagBatchApiService.upsert(id, request);
        return Result.success();
    }

    @RequestMapping(value = "/datastorage/{id}/tags/batch/copy", method = RequestMethod.PUT)
    @ResponseBody
    @Operation(
            summary = "Copies data storage object tags from from one object to another.",
            description = "Copies data storage object tags from from one object to another.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result copy(@PathVariable(value = ID) final Long id,
                       @RequestBody final DataStorageTagCopyBatchRequest request) {
        dataStorageTagBatchApiService.copy(id, request);
        return Result.success();
    }

    @RequestMapping(value = "/datastorage/{id}/tags/batch/load", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Loads data storage object latest version tags.",
            description = "Loads data storage object latest version tags.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<DataStorageTag>> load(@PathVariable(value = ID) final Long id,
                                             @RequestBody final DataStorageTagLoadBatchRequest request) {
        return Result.success(dataStorageTagBatchApiService.load(id, request));
    }

    @DeleteMapping(value = "/datastorage/{id}/tags/batch/delete")
    @ResponseBody
    @Operation(
            summary = "Deletes data storage object single version tags.",
            description = "Deletes data storage object single version tags.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result delete(@PathVariable(value = ID) final Long id,
                         @RequestBody final DataStorageTagDeleteBatchRequest request) {
        dataStorageTagBatchApiService.delete(id, request);
        return Result.success();
    }

    @DeleteMapping(value = "/datastorage/{id}/tags/batch/deleteAll")
    @ResponseBody
    @Operation(
            summary = "Deletes data storage object all version tags.",
            description = "Deletes data storage object all version tags.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result deleteAll(@PathVariable(value = ID) final Long id,
                            @RequestBody final DataStorageTagDeleteAllBatchRequest request) {
        dataStorageTagBatchApiService.deleteAll(id, request);
        return Result.success();
    }
}

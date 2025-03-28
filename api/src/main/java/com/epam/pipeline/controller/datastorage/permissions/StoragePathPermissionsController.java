/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.datastorage.permissions;

import com.epam.pipeline.acl.datastorage.permissions.StoragePathPermissionsApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.PermissionVO;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissionsVO;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissions;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.user.SidImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Api(value = "Data storage paths permissions management methods")
@RequiredArgsConstructor
public class StoragePathPermissionsController extends AbstractRestController {

    private static final String ID = "id";
    private static final String URL = "/datastorage/{id}/paths/permissions";

    private final StoragePathPermissionsApiService storagePathPermissionsApiService;

    @PostMapping(URL)
    @ResponseBody
    @ApiOperation(
            value = "Rewrites all storage path permissions for storage and user/group.",
            notes = "Rewrites all storage path permissions for storage and user/group.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result updatePathPermissions(
            @PathVariable(value = ID) final Long id,
            @RequestParam final String sidName, @RequestParam final boolean isPrincipal,
            @RequestBody final List<StoragePathPermissions> permissions) {
        storagePathPermissionsApiService.updateStoragePathPermissions(id, sidName, isPrincipal, permissions);
        return Result.success();
    }

    @GetMapping(URL)
    @ResponseBody
    @ApiOperation(
            value = "Loads storage path permissions for specified storage and current user.",
            notes = "Loads storage path permissions for specified storage and current user.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<StoragePathPermissions>> loadStoragePathPermissions(@PathVariable(value = ID) final Long id) {
        return Result.success(storagePathPermissionsApiService.loadStoragePathPermissions(id));
    }

    @DeleteMapping(URL)
    @ResponseBody
    @ApiOperation(
            value = "Deletes storage path permissions for specified storage for specified users and groups.",
            notes = "Deletes storage path permissions for specified storage for specified users and groups. " +
                    "If no users/groups provided all storage path permissions for specified storage will be deleted.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result deleteStoragePathPermissions(@PathVariable(value = ID) final Long id,
                                               @RequestBody final List<SidImpl> sids) {
        storagePathPermissionsApiService.deleteStoragePathPermissions(id, sids);
        return Result.success();
    }

    @GetMapping(URL + "/sids")
    @ResponseBody
    @ApiOperation(
            value = "Loads users and groups that have storage path permissions for specified storage.",
            notes = "Loads users and groups that have storage path permissions for specified storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<PermissionVO>> loadStoragePathPermissionsSids(
            @PathVariable(value = ID) final Long id,
            @RequestParam(required = false) final String path,
            @RequestParam(required = false) final DataStorageItemType type) {
        return Result.success(storagePathPermissionsApiService.loadStoragePathPermissionsSids(id, path, type));
    }

    @PutMapping(URL)
    @ResponseBody
    @ApiOperation(
            value = "Updates storage path permissions for specified storage objects.",
            notes = "Updates storage path permissions for specified storage objects.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result updateStoragePathPermissionsForItems(
            @PathVariable(value = ID) final Long id, @RequestBody final List<StoragePathPermissionsVO> permissions) {
        storagePathPermissionsApiService.updateStoragePathPermissionsForItems(id, permissions);
        return Result.success();
    }
}

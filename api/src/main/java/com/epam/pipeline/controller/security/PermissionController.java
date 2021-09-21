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

package com.epam.pipeline.controller.security;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.EntityPermissionVO;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.acl.security.AclPermissionApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Api(value = "Permissions")
public class PermissionController extends AbstractRestController {

    @Autowired
    private AclPermissionApiService permissionApiService;

    @RequestMapping(value = "/grant", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Sets user's  permissions for an object.",
            notes = "Sets user's permissions for an object.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AclSecuredEntry> grantPermissions(@RequestBody PermissionGrantVO grantVO) {
        return Result.success(permissionApiService.setPermissions(grantVO));
    }

    @RequestMapping(value = "/grant", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes user's permissions for an object.",
            notes = "Deletes user's permissions for an object.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AclSecuredEntry> deletePermissionsForUser(@RequestParam Long id,
            @RequestParam AclClass aclClass, @RequestParam String user,
            @RequestParam(required = false, defaultValue = "true") Boolean isPrincipal) {
        return Result.success(permissionApiService.deletePermissions(id, aclClass, user, isPrincipal));
    }

    @RequestMapping(value = "/grant/all", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes all permissions for an object.",
            notes = "Deletes all permissions for an object.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AclSecuredEntry> deleteAllPermissions(@RequestParam Long id,
            @RequestParam AclClass aclClass) {
        return Result.success(permissionApiService.deleteAllPermissions(id, aclClass));
    }

    @RequestMapping(value = "/grant", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads all permissions for an object.",
            notes = "Loads all permissions for an object.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AclSecuredEntry> getPipelinePermissions(@RequestParam Long id,
            @RequestParam AclClass aclClass) {
        return Result.success(permissionApiService.getPermissions(id, aclClass));
    }

    @RequestMapping(value = "grant/owner", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Change the owner of the particular acl object.",
            notes = "Change the owner of the particular acl object.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AclSecuredEntry> changeOwner(@RequestParam Long id,
            @RequestParam AclClass aclClass, @RequestParam String userName) {
        return Result.success(permissionApiService.changeOwner(id, aclClass, userName));
    }

    @GetMapping(value = "permissions")
    @ResponseBody
    @ApiOperation(
            value = "Loads all permissions for entity specified by ID.",
            notes = "Loads all permissions for entity specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<EntityPermissionVO> loadEntityPermissions(@RequestParam Long id, @RequestParam AclClass aclClass) {
        return Result.success(permissionApiService.loadEntityPermission(id, aclClass));
    }
}

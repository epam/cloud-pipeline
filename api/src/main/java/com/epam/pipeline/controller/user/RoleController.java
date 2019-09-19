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

package com.epam.pipeline.controller.user;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.entity.RoleWithGroupBlockedStatus;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.RoleApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collection;
import java.util.List;

@Controller
@Api(value = "Users")
@RequestMapping("/role")
public class RoleController extends AbstractRestController {

    @Autowired
    private RoleApiService roleApiService;

    @RequestMapping(value = "/loadAll", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads all available roles.",
            notes = "Loads all available roles. Parameter <b>loadUsers</b> specifies whether"
                    + "list of associated users should be returned with roles.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Collection<RoleWithGroupBlockedStatus>> loadRoles(
            @RequestParam(required = false, defaultValue = "false") boolean loadUsers) {
        return Result.success(loadUsers ? roleApiService.loadRolesWithUsers() : roleApiService.loadRoles());
    }

    @RequestMapping(value = "/{id}/assign", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Assigns a list of users to role.",
            notes = "Assigns a list of users to role",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<ExtendedRole> assignRole(@PathVariable Long id,
                                           @RequestParam List<Long> userIds) {
        return Result.success(roleApiService.assignRole(id, userIds));
    }

    @RequestMapping(value = "/{id}/remove", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Removes a role from a list of users",
            notes = "Removes a role from a list of users",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<ExtendedRole> removeRole(@PathVariable Long id,
                                           @RequestParam List<Long> userIds) {
        return Result.success(roleApiService.removeRole(id, userIds));
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates a new role.",
            notes = "Creates a new role with specified name. Name should not be empty. All roles"
                    + "are supposed to start with 'ROLE_' prefix, if it is not provided, prefix will"
                    + "be added automatically.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Role> createRole(@RequestParam String roleName,
                                   @RequestParam(required = false, defaultValue = "false") boolean userDefault,
                                   @RequestParam(required = false) Long defaultStorageId) {
        return Result.success(roleApiService.createRole(roleName, userDefault, defaultStorageId));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    @ResponseBody
    @ApiOperation(
            value = "Updates a role specified by ID.",
            notes = "Updates a role specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody RoleVO roleVO) {
        return Result.success(roleApiService.updateRole(id, roleVO));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets a role specified by ID.",
            notes = "Gets a role specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Role> getRole(@PathVariable Long id) {
        return Result.success(roleApiService.loadRole(id));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a role specified by ID.",
            notes = "Deletes a role specified by ID along with all permissions set",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Role> deleteRole(@PathVariable Long id) {
        return Result.success(roleApiService.deleteRole(id));
    }


}

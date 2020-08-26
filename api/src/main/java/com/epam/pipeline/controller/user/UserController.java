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

package com.epam.pipeline.controller.user;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.controller.vo.RouteType;
import com.epam.pipeline.entity.info.UserInfo;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Controller
@Api(value = "Users")
public class UserController extends AbstractRestController {

    @Autowired
    private AuthManager authManager;

    @Autowired
    private UserApiService userApiService;

    @RequestMapping(value = "/user/token", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a new valid token.",
            notes = "Returns a new valid token. " +
                    "If user is name not specified a new token will be generated for currently authenticated user.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<JwtRawToken> getSettings(@RequestParam(required = false) Long expiration,
                                           @RequestParam(required = false) String name) {
        return Result.success(StringUtils.isNotBlank(name)
                ? userApiService.issueToken(name, expiration)
                : authManager.issueTokenForCurrentUser(expiration));
    }

    @RequestMapping(value = "/whoami", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a description of currently authenticated user.",
            notes = "Returns a description of currently authenticated user.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> getCurrentUser() {
        return Result.success(userApiService.getCurrentUser());
    }


    @RequestMapping(value = "/route", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns html and sets auth cookies.",
            notes = "Returns html and sets auth cookies.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public String redirect(@RequestParam String url, @RequestParam RouteType type, HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        String token = authManager.issueTokenForCurrentUser(null).getToken();
        if (type == RouteType.COOKIE) {
            response.addCookie(new Cookie("Bearer", token));
            return String.format("<html><body><script>window.location.href = \"%s\"</script></body></html>",
                    url);
        } else if (type == RouteType.FORM) {
            return String.format(
                            "<html>\n"
                            +  "<body>\n"
                            +    "<form id=\"form\" method=\"post\" action=\"%s\">\n"
                            +      " <input type=\"hidden\" name=\"bearer\" value=\"%s\" />\n"
                            +    "</form>\n"
                            +    "<script>\n" + "document.getElementById('form').submit()\n"
                            +    "</script>\n"
                            +  "</body>\n"
                            +"</html>",
                    url, token);
        } else {
            throw new IllegalArgumentException("Unsupported route type + " + type);
        }
    }

    @RequestMapping(value = "/user/find", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Finds user by a prefix (case insensitive).",
            notes = "Finds user by a prefix (case insensitive). Search is performed in user name "
                    + "and all user's additional attribute values.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineUser>> findUsers(@RequestParam String prefix) {
        return Result.success(userApiService.findUsers(prefix));
    }

    @RequestMapping(value = "/user", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates a new user.",
            notes = "Creates a new user with specified username and roles.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> createUser(@RequestBody PipelineUserVO userVO) {
        return Result.success(userApiService.createUser(userVO));
    }


    @RequestMapping(value = "/user/{id}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads a user by a ID.",
            notes = "Loads a user by a ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> loadUser(@PathVariable Long id) {
        return Result.success(userApiService.loadUser(id));
    }

    @GetMapping(value = "/user")
    @ResponseBody
    @ApiOperation(
            value = "Loads registered user by user name.",
            notes = "Loads registered user by user name.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> loadUserByName(@RequestParam String name) {
        return Result.success(userApiService.loadUserByName(name));
    }

    @RequestMapping(value = "/user/{id}", method = RequestMethod.PUT)
    @ResponseBody
    @ApiOperation(
            value = "Updates a user by a ID.",
            notes = "Updates a user by a ID. Currently only defaultStorage id is supported for update",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> updateUser(@PathVariable Long id, @RequestBody PipelineUserVO userVO) {
        return Result.success(userApiService.updateUser(id, userVO));
    }

    @RequestMapping(value = "/user/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a user by a ID.",
            notes = "Deletes a user by a ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result deleteUser(@PathVariable Long id) {
        userApiService.deleteUser(id);
        return Result.success(null);
    }

    @RequestMapping(value = "/users", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads all registered users.",
            notes = "Loads all registered users.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineUser>> loadUsers() {
        return Result.success(userApiService.loadUsers());
    }

    @GetMapping(value = "/users/info")
    @ResponseBody
    @ApiOperation(
            value = "Loads all users' brief information.",
            notes = "Loads all registered users, but instead of providing detailed description only the general "
                    + "information is returned.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<UserInfo>> loadUsersInfo() {
        return Result.success(userApiService.loadUsersInfo());
    }

    @RequestMapping(value = "/user/controls", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns user assigned templates.",
            notes = "Returns user assigned templates.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<CustomControl>> getUserControls() {
        return Result.success(userApiService.getUserControls());
    }

    @PutMapping(value = "/user/{id}/block")
    @ResponseBody
    @ApiOperation(
            value = "Changes the block status of a user.",
            notes = "Changes the block status of a user. If the user is blocked, he can't access his account.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> updateUserBlockingStatus(@PathVariable final Long id,
                                                         @RequestParam final Boolean blockStatus) {
        return Result.success(userApiService.updateUserBlockingStatus(id, blockStatus));
    }

    @RequestMapping(value = "/user/{id}/update", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Updates user roles.",
            notes = "Updates user roles. Pass all assigned roles, "
                    + "as they will be completely replaced with passes IDs",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> updateUserRoles(@PathVariable Long id, @RequestParam List<Long> roleIds) {
        return Result.success(userApiService.updateUserRoles(id, roleIds));
    }

    @RequestMapping(value = "/user/isMember", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Checks a specific registered user is a member of a specified group.",
            notes = "Checks a specific registered user is a member of a specified group.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Boolean> checkUserByGroup(@RequestParam String userName, @RequestParam String group) {
        return Result.success(userApiService.checkUserByGroup(userName, group));
    }

    @RequestMapping(value = "/user/export", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Exports users.",
            notes = "Exports users with specified information",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public void exportUsers(@RequestBody PipelineUserExportVO attr, HttpServletResponse response) throws IOException {
        writeFileToResponse(response, userApiService.exportUsers(attr), "users.csv");
    }

    @RequestMapping(value = "/group", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads all registered users, that are the members of a specified group. ",
            notes = "Loads all registered users, that are the members of a specified group.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineUser>> loadUsersByGroup(@RequestParam String group) {
        return Result.success(userApiService.loadUsersByGroup(group));
    }

    @RequestMapping(value = "/group/find", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Finds user group by a prefix (case insensitive).",
            notes = "Finds user group by a prefix (case insensitive).",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<String>> findGroups(@RequestParam String prefix) {
        return Result.success(userApiService.findGroups(prefix));
    }

    @RequestMapping(value = "/group/{groupName}/block", method = {RequestMethod.POST, RequestMethod.PUT})
    @ResponseBody
    @ApiOperation(
            value = "Creates or updates the block status of a group.",
            notes = "Creates the block status of a group or updates it if it exists. " +
                    "If the group is blocked, none of its users can't access their accounts.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GroupStatus> upsertGroupBlockingStatus(@PathVariable final String groupName,
                                                         @RequestParam final Boolean blockStatus) {
        return Result.success(userApiService.upsertGroupBlockingStatus(groupName, blockStatus));
    }

    @DeleteMapping(value = "/group/{groupName}/block")
    @ResponseBody
    @ApiOperation(
            value = "Removes the block status of a group.",
            notes = "Removes the block status of a group.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GroupStatus> deleteGroupBlockingStatus(@PathVariable final String groupName) {
        return Result.success(userApiService.deleteGroupBlockingStatus(groupName));
    }
}

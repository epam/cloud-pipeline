/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.user.RunnerSidVO;
import com.epam.pipeline.dto.user.OnlineUsers;
import com.epam.pipeline.entity.info.UserInfo;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.ImpersonationStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.entity.user.RunnerSid;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.acl.user.UserApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@Tag(name = "Users")
public class UserController extends AbstractRestController {

    @Autowired
    private AuthManager authManager;

    @Autowired
    private UserApiService userApiService;

    @RequestMapping(value = "/user/token", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a new valid token.",
            description = "Returns a new valid token. " +
                    "If user is name not specified a new token will be generated for currently authenticated user.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<JwtRawToken> getSettings(@RequestParam(required = false) Long expiration,
                                           @RequestParam(required = false) String name) {
        return Result.success(StringUtils.isNotBlank(name)
                ? userApiService.issueToken(name, expiration)
                : authManager.issueTokenForCurrentUser(expiration));
    }

    @RequestMapping(value = "/whoami", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a description of currently authenticated user.",
            description = "Returns a description of currently authenticated user.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> getCurrentUser() {
        return Result.success(userApiService.getCurrentUser());
    }


    @RequestMapping(value = "/route", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns html and sets auth cookies.",
            description = "Returns html and sets auth cookies.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
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
    @Operation(
            summary = "Finds user by a prefix (case insensitive).",
            description = "Finds user by a prefix (case insensitive). Search is performed in user name "
                    + "and all user's additional attribute values.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineUser>> findUsers(@RequestParam String prefix) {
        return Result.success(userApiService.findUsers(prefix));
    }

    @RequestMapping(value = "/user", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Creates a new user.",
            description = "Creates a new user with specified username and roles.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> createUser(@RequestBody PipelineUserVO userVO) {
        return Result.success(userApiService.createUser(userVO));
    }


    @RequestMapping(value = "/user/{id}", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Loads a user by a ID.",
            description = "Loads a user by a ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> loadUser(@PathVariable Long id,
                                         @RequestParam(defaultValue = FALSE) final boolean quotas) {
        return Result.success(userApiService.loadUser(id, quotas));
    }

    @GetMapping(value = "/user")
    @ResponseBody
    @Operation(
            summary = "Loads registered user by user name.",
            description = "Loads registered user by user name.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> loadUserByName(@RequestParam String name) {
        return Result.success(userApiService.loadUserByName(name));
    }

    @RequestMapping(value = "/user/{id}", method = RequestMethod.PUT)
    @ResponseBody
    @Operation(
            summary = "Updates a user by a ID.",
            description = "Updates a user by a ID. Currently only defaultStorage id is supported for update")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> updateUser(@PathVariable Long id, @RequestBody PipelineUserVO userVO) {
        return Result.success(userApiService.updateUser(id, userVO));
    }

    @RequestMapping(value = "/user/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a user by a ID.",
            description = "Deletes a user by a ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result deleteUser(@PathVariable Long id) {
        userApiService.deleteUser(id);
        return Result.success(null);
    }

    @RequestMapping(value = "/users", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Loads all registered users.",
            description = "Loads all registered users.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineUser>> loadUsers(@RequestParam(defaultValue = FALSE) final boolean activity,
                                                @RequestParam(defaultValue = FALSE) final boolean quotas) {
        return Result.success(activity
                ? userApiService.loadUsersWithActivityStatus(quotas)
                : userApiService.loadUsers(quotas));
    }

    @GetMapping(value = "/users/info")
    @ResponseBody
    @Operation(
            summary = "Loads all users' brief information.",
            description = "Loads all registered users, but instead of providing detailed description only the general "
                    + "information is returned.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<UserInfo>> loadUsersInfo() {
        return Result.success(userApiService.loadUsersInfo(Collections.emptyList()));
    }

    @PostMapping(value = "/users/info")
    @ResponseBody
    @Operation(
            summary = "Loads all users' brief information. Allows to filter users by list of usernames.",
            description = "Loads all registered users, but instead of providing detailed description only the general "
                    + "information is returned. Allows to filter users by list of usernames.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<UserInfo>> loadUsersInfo(
            @RequestBody(required = false) final List<String> userNames) {
        return Result.success(userApiService.loadUsersInfo(userNames));
    }

    @RequestMapping(value = "/user/controls", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns user assigned templates.",
            description = "Returns user assigned templates.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<CustomControl>> getUserControls() {
        return Result.success(userApiService.getUserControls());
    }

    @PutMapping(value = "/user/{id}/block")
    @ResponseBody
    @Operation(
            summary = "Changes the block status of a user.",
            description = "Changes the block status of a user. If the user is blocked, he can't access his account.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> updateUserBlockingStatus(@PathVariable final Long id,
                                                         @RequestParam final Boolean blockStatus) {
        return Result.success(userApiService.updateUserBlockingStatus(id, blockStatus));
    }

    @RequestMapping(value = "/user/{id}/update", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Updates user roles.",
            description = "Updates user roles. Pass all assigned roles, "
                    + "as they will be completely replaced with passes IDs")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineUser> updateUserRoles(@PathVariable Long id, @RequestParam List<Long> roleIds) {
        return Result.success(userApiService.updateUserRoles(id, roleIds));
    }

    @RequestMapping(value = "/user/isMember", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Checks a specific registered user is a member of a specified group.",
            description = "Checks a specific registered user is a member of a specified group.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Boolean> checkUserByGroup(@RequestParam String userName, @RequestParam String group) {
        return Result.success(userApiService.checkUserByGroup(userName, group));
    }

    @RequestMapping(value = "/user/export", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Exports users.",
            description = "Exports users with specified information")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public void exportUsers(@RequestBody PipelineUserExportVO attr, HttpServletResponse response) throws IOException {
        writeFileToResponse(response, userApiService.exportUsers(attr), "users.csv");
    }

    @RequestMapping(value = "/group", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Loads all registered users, that are the members of a specified group. ",
            description = "Loads all registered users, that are the members of a specified group.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineUser>> loadUsersByGroup(@RequestParam String group) {
        return Result.success(userApiService.loadUsersByGroup(group));
    }

    @RequestMapping(value = "/group/find", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Finds user group by a prefix (case insensitive).",
            description = "Finds user group by a prefix (case insensitive).")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<String>> findGroups(@RequestParam String prefix) {
        return Result.success(userApiService.findGroups(prefix));
    }

    @RequestMapping(value = "/group/{groupName}/block", method = {RequestMethod.POST, RequestMethod.PUT})
    @ResponseBody
    @Operation(
            summary = "Creates or updates the block status of a group.",
            description = "Creates the block status of a group or updates it if it exists. " +
                    "If the group is blocked, none of its users can't access their accounts.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GroupStatus> upsertGroupBlockingStatus(@PathVariable final String groupName,
                                                         @RequestParam final Boolean blockStatus) {
        return Result.success(userApiService.upsertGroupBlockingStatus(groupName, blockStatus));
    }

    @DeleteMapping(value = "/group/{groupName}/block")
    @ResponseBody
    @Operation(
            summary = "Removes the block status of a group.",
            description = "Removes the block status of a group.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GroupStatus> deleteGroupBlockingStatus(@PathVariable final String groupName) {
        return Result.success(userApiService.deleteGroupBlockingStatus(groupName));
    }

    @GetMapping(value = "/groups/block")
    @ResponseBody
    @Operation(
        summary = "Load all available blocking statuses all over the groups.",
        description = "Load all available blocking statuses all over the groups. "
                + "Returns all statuses presented in the corresponding DB table")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<List<GroupStatus>> loadGroupsBlockingStatuses() {
        return Result.success(userApiService.loadAllGroupsBlockingStatuses());
    }

    @PostMapping("/users/import")
    @ResponseBody
    @Operation(
            summary = "Imports users from csv file.",
            description = "Imports users from csv file.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<PipelineUserEvent>> importUsersFromCsv(
            @RequestParam(defaultValue = FALSE) final boolean createUser,
            @RequestParam(defaultValue = FALSE) final boolean createGroup,
            @RequestParam(required = false) final List<String> createMetadata,
            final HttpServletRequest request) throws FileUploadException, IOException {
        final MultipartFile file = consumeMultipartFile(request);
        if (file.isEmpty()) {
            throw new FileUploadException("File is empty!");
        }
        return Result.success(userApiService.importUsersFromCsv(createUser, createGroup, createMetadata, file));
    }

    @PostMapping("/users/{id}/runners")
    @ResponseBody
    @Operation(
            summary = "Updates runners to user",
            description = "Updates runners to user")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<RunnerSidVO>> updateRunners(@PathVariable final Long id,
                                                 @RequestBody final List<RunnerSidVO> runners) {
        return Result.success(userApiService.updateRunners(id, runners));
    }

    @GetMapping("/users/{id}/runners")
    @ResponseBody
    @Operation(
            summary = "Loads runners for user",
            description = "Loads runners for user")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<RunnerSid>> getRunners(@PathVariable final Long id) {
        return Result.success(userApiService.getRunners(id));
    }

    @GetMapping("/user/impersonation")
    @ResponseBody
    @Operation(
        summary = "Loads impersonation status",
        description = "Loads impersonation status: show original user and impersonated one if present")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<ImpersonationStatus> getImpersonationStatus() {
        return Result.success(userApiService.getImpersonationStatus());
    }

    @PostMapping("/users/online")
    @ResponseBody
    @Operation(
            summary = "Saves currently online users dump",
            description = "Saves currently online users dump")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<OnlineUsers> saveOnlineUsers() {
        return Result.success(userApiService.saveCurrentlyOnlineUsers());
    }

    @DeleteMapping("/users/online")
    @ResponseBody
    @Operation(
            summary = "Deletes online users dumps created before specified date",
            description = "Deletes online users dumps created before specified date")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Boolean> deleteOnlineUsers(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam
                                                 final LocalDate date) {
        return Result.success(userApiService.deleteExpiredOnlineUsers(date));
    }

    @GetMapping("/user/launchLimits")
    @ResponseBody
    @Operation(
            summary = "Loads launch limits for a user.",
            description = "Loads a map of launch limits, configured via contextual preferences.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Map<String, Integer>> getCurrentUserLaunchLimits(
        @RequestParam(required = false, defaultValue = "false") final boolean loadAll) {
        return Result.success(userApiService.getCurrentUserLaunchLimits(loadAll));
    }
}

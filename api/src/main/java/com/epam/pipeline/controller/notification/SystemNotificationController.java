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

package com.epam.pipeline.controller.notification;

import java.util.Date;
import java.util.List;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.SystemNotificationFilterVO;
import com.epam.pipeline.entity.notification.SystemNotification;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmation;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmationRequest;
import com.epam.pipeline.manager.notification.SystemNotificationApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Api(value = "System notifications")
public class SystemNotificationController  extends AbstractRestController {

    private static final String ID = "id";
    private static final String AFTER = "after";

    @Autowired
    private SystemNotificationApiService systemNotificationApiService;

    @RequestMapping(value = "/notification", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates or updates notification.",
            notes = "Creates or updates notification.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<SystemNotification> createOrUpdateNotification(@RequestBody final SystemNotification notification) {
        return Result.success(systemNotificationApiService.createOrUpdateNotification(notification));
    }

    @RequestMapping(value = "/notification/list", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets all notifications.",
            notes = "Gets all notifications.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<List<SystemNotification>> loadNotifications() {
        return Result.success(systemNotificationApiService.loadAllNotifications());
    }

    @RequestMapping(value = "/notification/active", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets all active notifications.",
            notes = "Gets all active notifications.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<List<SystemNotification>> loadActiveNotifications(
            @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
            @RequestParam(value = AFTER, required = false) final Date after) {
        return Result.success(systemNotificationApiService.loadActiveNotifications(after));
    }

    @RequestMapping(value = "/notification/filter", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Filters notifications.",
            notes = "Filters notifications.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<List<SystemNotification>> filterNotifications(
            @RequestBody final SystemNotificationFilterVO filterVO) {
        return Result.success(systemNotificationApiService.filterNotifications(filterVO));
    }

    @RequestMapping(value = "/notification/{id}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets notification by identifier.",
            notes = "Gets notification by identifier.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<SystemNotification> loadNotification(@PathVariable(value = ID) Long id) {
        return Result.success(systemNotificationApiService.loadNotification(id));
    }

    @RequestMapping(value = "/notification/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes notification by identifier.",
            notes = "Deletes notification by identifier.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<SystemNotification> deleteNotification(@PathVariable(value = ID) Long id) {
        return Result.success(systemNotificationApiService.deleteNotification(id));
    }

    @RequestMapping(value = "/notification/confirm", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Confirms notification.",
            notes = "Confirms notification by identifier with the given title and body for the currently authorized " +
                    "user.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<SystemNotificationConfirmation> confirmNotification(
            @RequestBody final SystemNotificationConfirmationRequest request) {
        return Result.success(systemNotificationApiService.confirmNotification(request));
    }
}

/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.acl.notification.UserNotificationApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.notification.UserNotification;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user-notification")
@RequiredArgsConstructor
public class UserNotificationController extends AbstractRestController {

    private final UserNotificationApiService notificationApiService;

    @PostMapping("/message")
    @Operation(
            summary = "Creates custom user notification.",
            description = "Creates a custom notification with the specified parameters. " +
                    "Subject, text and toUser fields are the required ones.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<UserNotification> create(@RequestBody final UserNotification notification) {
        return Result.success(notificationApiService.save(notification));
    }

    @PutMapping("/message/readAll")
    @Operation(
            summary = "Mark all user notifications as read.",
            description = "Mark all user notifications as read.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<Boolean> readAll() {
        notificationApiService.readAll();
        return Result.success();
    }

    @GetMapping("/message/{userId}")
    @Operation(
            summary = "Gets user notifications.",
            description = "Gets user notifications by userId.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<PagedResult<List<UserNotification>>> findByUserId(@PathVariable final Long userId,
                                                       @RequestParam final Boolean isRead,
                                                       @RequestParam final int pageNum,
                                                       @RequestParam final int pageSize) {
        return Result.success(notificationApiService.findByUserId(userId, isRead, pageNum, pageSize));
    }

    @GetMapping("/message/my")
    @Operation(
            summary = "Gets current user's notifications.",
            description = "Gets current user's notifications.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<PagedResult<List<UserNotification>>> findMy(@RequestParam final Boolean isRead,
                                                              @RequestParam final int pageNum,
                                                              @RequestParam final int pageSize) {
        return Result.success(notificationApiService.findMy(isRead, pageNum, pageSize));
    }

    @DeleteMapping("/message/{messageId}")
    @Operation(
            summary = "Deletes a custom user notification.",
            description = "Deletes a custom user notification.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<Boolean> delete(@PathVariable final Long messageId) {
        notificationApiService.delete(messageId);
        return Result.success();
    }
}

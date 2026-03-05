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

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.notification.NotificationMessageVO;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.acl.notification.NotificationApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@Tag(name = "notification-controller", description = "Notification Controller")
public class NotificationController extends AbstractRestController {

    private final NotificationApiService notificationApiService;

    @PostMapping("/message")
    @Operation(
            summary = "Creates custom notification.",
            description = "Creates a custom notification with the specified parameters. " +
                    "Subject, body and toUser fields are the required once. " +
                    "All specified user names have to be actual pipeline user names.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<NotificationMessage> create(@RequestBody final NotificationMessageVO message) {
        return Result.success(notificationApiService.createNotification(message));
    }
}

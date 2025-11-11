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

package com.epam.pipeline.acl.notification;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.notification.UserNotification;
import com.epam.pipeline.manager.notification.UserNotificationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNotificationApiService {

    private final UserNotificationManager notificationManager;

    @PreAuthorize("hasRole('ADMIN') OR @notificationPermissionManager.hasPermission(#notification)")
    public UserNotification save(final UserNotification notification) {
        return notificationManager.save(notification);
    }

    @PreAuthorize("hasRole('ADMIN') OR @notificationPermissionManager.hasPermissionByUserId(#userId)")
    public PagedResult<List<UserNotification>> findByUserId(final Long userId,
                                               final Boolean isRead,
                                               final int pageNum,
                                               final int pageSize) {
        return notificationManager.findByUserId(userId, isRead, pageNum, pageSize);
    }

    public PagedResult<List<UserNotification>> findMy(final Boolean isRead, final int pageNum, final int pageSize) {
        return notificationManager.findMy(isRead, pageNum, pageSize);
    }

    public void readAll() {
        notificationManager.readAll();
    }

    @PreAuthorize("hasRole('ADMIN') OR @notificationPermissionManager.hasPermission(#notificationId)")
    public void delete(final Long notificationId) {
        notificationManager.delete(notificationId);
    }
}

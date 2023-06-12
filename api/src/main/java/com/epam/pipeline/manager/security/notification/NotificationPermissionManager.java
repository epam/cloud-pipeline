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

package com.epam.pipeline.manager.security.notification;

import com.epam.pipeline.dto.notification.UserNotification;
import com.epam.pipeline.entity.notification.UserNotificationEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.repository.notification.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationPermissionManager {

    private final UserNotificationRepository repository;
    private final CheckPermissionHelper permissionsHelper;
    private final UserManager userManager;

    public boolean hasPermission(final UserNotification userNotification) {
        if (Objects.nonNull(userNotification.getId())) {
            return hasPermission(userNotification.getId());
        }
        return hasPermissionByUserId(userNotification.getUserId());
    }

    public boolean hasPermission(final Long notificationId) {
        final UserNotificationEntity userNotification = repository.findOne(notificationId);
        if (userNotification == null) {
            return false;
        }
        return hasPermissionByUserId(userNotification.getUserId());
    }

    public boolean hasPermissionByUserId(final Long userId) {
        final PipelineUser user = userManager.loadUserById(userId);
        if (user == null) {
            return false;
        }
        return permissionsHelper.isOwner(user.getUserName());
    }
}

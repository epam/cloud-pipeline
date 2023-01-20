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

import com.epam.pipeline.entity.notification.UserNotification;
import com.epam.pipeline.manager.notification.UserNotificationManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNotificationApiService {

    private final UserNotificationManager notificationManager;

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public UserNotification save(final UserNotification notification) {
        return notificationManager.save(notification);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public List<UserNotification> findByUserId(final Long userId) {
        return notificationManager.findByUserId(userId);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public void delete(final Long notificationId) {
        notificationManager.delete(notificationId);
    }
}

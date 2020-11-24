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

package com.epam.pipeline.acl.notification;

import com.epam.pipeline.controller.vo.SystemNotificationFilterVO;
import com.epam.pipeline.entity.notification.SystemNotification;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmation;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmationRequest;
import com.epam.pipeline.manager.notification.SystemNotificationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SystemNotificationApiService {

    private static final String ADMIN_ROLE = "hasRole('ADMIN')";

    @Autowired
    private SystemNotificationManager systemNotificationManager;

    @PreAuthorize(ADMIN_ROLE)
    public SystemNotification createOrUpdateNotification(SystemNotification notification) {
        return systemNotificationManager.createOrUpdateNotification(notification);
    }

    @PreAuthorize(ADMIN_ROLE)
    public SystemNotification createNotification(SystemNotification notification) {
        return systemNotificationManager.createNotification(notification);
    }

    @PreAuthorize(ADMIN_ROLE)
    public SystemNotification updateNotification(SystemNotification notification) {
        return systemNotificationManager.updateNotification(notification);
    }

    @PreAuthorize(ADMIN_ROLE)
    public SystemNotification deleteNotification(Long id) {
        return systemNotificationManager.deleteNotification(id);
    }

    @PreAuthorize(ADMIN_ROLE)
    public SystemNotification loadNotification(Long id) {
        return systemNotificationManager.loadNotification(id);
    }

    @PreAuthorize(ADMIN_ROLE)
    public List<SystemNotification> loadAllNotifications() {
        return systemNotificationManager.loadAllNotifications();
    }

    @PreAuthorize(ADMIN_ROLE)
    public List<SystemNotification> filterNotifications(SystemNotificationFilterVO filterVO) {
        return systemNotificationManager.filterNotifications(filterVO);
    }

    public List<SystemNotification> loadActiveNotifications(Date after) {
        return systemNotificationManager.loadActiveNotifications(after);
    }

    public SystemNotificationConfirmation confirmNotification(final SystemNotificationConfirmationRequest request) {
        return systemNotificationManager.confirmNotification(request);
    }
}

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

package com.epam.pipeline.manager.notification;

import java.util.List;

import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.security.acl.AclExpressions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * A service class for Notification Template access control
 */
@Service
public class NotificationTemplateApiService {

    @Autowired
    private NotificationTemplateManager notificationTemplateManager;

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public List<NotificationTemplate> loadAll() {
        return notificationTemplateManager.loadAll();
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public NotificationTemplate create(NotificationTemplate template){
        return notificationTemplateManager.create(template);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public NotificationTemplate load(long id) {
        return notificationTemplateManager.load(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public void delete(long templateId) {
        notificationTemplateManager.delete(templateId);
    }
}

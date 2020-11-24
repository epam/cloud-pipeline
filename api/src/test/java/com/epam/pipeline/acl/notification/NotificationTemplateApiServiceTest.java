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

import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.manager.notification.NotificationTemplateManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getNotificationTemplate;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class NotificationTemplateApiServiceTest extends AbstractAclTest {

    private final NotificationTemplate notificationTemplate = getNotificationTemplate();
    private final List<NotificationTemplate> notificationTemplateList = Collections.singletonList(notificationTemplate);

    @Autowired
    private NotificationTemplateApiService notificationTemplateApiService;

    @Autowired
    private NotificationTemplateManager mockNotificationTemplateManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllForAdmin() {
        doReturn(notificationTemplateList).when(mockNotificationTemplateManager).loadAll();

        assertThat(notificationTemplateApiService.loadAll()).isEqualTo(notificationTemplateList);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadAllForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> notificationTemplateApiService.loadAll());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateOrUpdateForAdmin() {
        doReturn(notificationTemplate).when(mockNotificationTemplateManager).create(notificationTemplate);

        assertThat(notificationTemplateApiService.create(notificationTemplate)).isEqualTo(notificationTemplate);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateOrUpdateForNotAdmin() {
        assertThrows(AccessDeniedException.class,
                () -> notificationTemplateApiService.create(notificationTemplate));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadForAdmin() {
        doReturn(notificationTemplate).when(mockNotificationTemplateManager).load(ID);

        assertThat(notificationTemplateApiService.load(ID)).isEqualTo(notificationTemplate);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> notificationTemplateApiService.load(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteForAdmin() {
        notificationTemplateApiService.delete(ID);

        verify(mockNotificationTemplateManager).delete(ID);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> notificationTemplateApiService.delete(ID));
    }
}

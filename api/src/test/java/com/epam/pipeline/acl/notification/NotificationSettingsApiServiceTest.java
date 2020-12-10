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

import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.manager.notification.NotificationSettingsManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getNotificationSettings;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class NotificationSettingsApiServiceTest extends AbstractAclTest {

    private final NotificationSettings notificationSettings = getNotificationSettings();
    private final List<NotificationSettings> notificationSettingsList = Collections.singletonList(notificationSettings);

    @Autowired
    private NotificationSettingsApiService notificationSettingsApiService;

    @Autowired
    private NotificationSettingsManager mockNotificationSettingsManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllForAdmin() {
        doReturn(notificationSettingsList).when(mockNotificationSettingsManager).loadAll();

        assertThat(notificationSettingsApiService.loadAll()).isEqualTo(notificationSettingsList);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadAllForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> notificationSettingsApiService.loadAll());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateOrUpdateForAdmin() {
        doReturn(notificationSettings).when(mockNotificationSettingsManager).createOrUpdate(notificationSettings);

        assertThat(notificationSettingsApiService.createOrUpdate(notificationSettings)).isEqualTo(notificationSettings);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateOrUpdateForNotAdmin() {
        assertThrows(AccessDeniedException.class,
            () -> notificationSettingsApiService.createOrUpdate(notificationSettings));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadForAdmin() {
        doReturn(notificationSettings).when(mockNotificationSettingsManager).load(ID);

        assertThat(notificationSettingsApiService.load(ID)).isEqualTo(notificationSettings);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> notificationSettingsApiService.load(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteForAdmin() {
        notificationSettingsApiService.delete(ID);

        verify(mockNotificationSettingsManager).delete(ID);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> notificationSettingsApiService.delete(ID));
    }
}

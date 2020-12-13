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
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getSystemNotification;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getSystemNotificationConfirmation;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getSystemNotificationConfirmationRequest;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getSystemNotificationFilterVO;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class SystemNotificationApiServiceTest extends AbstractAclTest {

    private final Date date = new Date();
    private final SystemNotification systemNotification = getSystemNotification();
    private final SystemNotificationFilterVO systemNotificationFilterVO = getSystemNotificationFilterVO();
    private final List<SystemNotification> systemNotificationList = Collections.singletonList(systemNotification);
    private final SystemNotificationConfirmation systemNotificationConfirmation = getSystemNotificationConfirmation();
    private final SystemNotificationConfirmationRequest systemNotificationConfirmationRequest =
            getSystemNotificationConfirmationRequest();

    @Autowired
    private SystemNotificationApiService systemNotificationApiService;

    @Autowired
    private SystemNotificationManager mockSystemNotificationManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateOrUpdateNotificationForAdmin() {
        doReturn(systemNotification).when(mockSystemNotificationManager).createOrUpdateNotification(systemNotification);

        assertThat(systemNotificationApiService.createOrUpdateNotification(systemNotification))
                .isEqualTo(systemNotification);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateOrUpdateNotificationForNotAdmin() {
        assertThrows(AccessDeniedException.class,
            () -> systemNotificationApiService.createOrUpdateNotification(systemNotification));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateNotificationForAdmin() {
        doReturn(systemNotification).when(mockSystemNotificationManager).createNotification(systemNotification);

        assertThat(systemNotificationApiService.createNotification(systemNotification)).isEqualTo(systemNotification);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateNotificationForNotAdmin() {
        assertThrows(AccessDeniedException.class,
            () -> systemNotificationApiService.createNotification(systemNotification));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateNotificationForAdmin() {
        doReturn(systemNotification).when(mockSystemNotificationManager).updateNotification(systemNotification);

        assertThat(systemNotificationApiService.updateNotification(systemNotification))
                .isEqualTo(systemNotification);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateNotificationForNotAdmin() {
        assertThrows(AccessDeniedException.class,
            () -> systemNotificationApiService.updateNotification(systemNotification));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteNotificationForAdmin() {
        doReturn(systemNotification).when(mockSystemNotificationManager).deleteNotification(ID);

        assertThat(systemNotificationApiService.deleteNotification(ID)).isEqualTo(systemNotification);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteNotificationForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> systemNotificationApiService.deleteNotification(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadNotificationForAdmin() {
        doReturn(systemNotification).when(mockSystemNotificationManager).loadNotification(ID);

        assertThat(systemNotificationApiService.loadNotification(ID)).isEqualTo(systemNotification);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadNotificationForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> systemNotificationApiService.loadNotification(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllNotificationsForAdmin() {
        doReturn(systemNotificationList).when(mockSystemNotificationManager).loadAllNotifications();

        assertThat(systemNotificationApiService.loadAllNotifications()).isEqualTo(systemNotificationList);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadAllNotificationForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> systemNotificationApiService.loadAllNotifications());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFilterNotificationsForAdmin() {
        doReturn(systemNotificationList).when(mockSystemNotificationManager)
                .filterNotifications(systemNotificationFilterVO);

        assertThat(systemNotificationApiService.filterNotifications(systemNotificationFilterVO))
                .isEqualTo(systemNotificationList);
    }

    @Test
    @WithMockUser
    public void shouldDenyFilterNotificationsForNotAdmin() {
        assertThrows(AccessDeniedException.class,
            () -> systemNotificationApiService.filterNotifications(systemNotificationFilterVO));
    }

    @Test
    public void shouldLoadActiveNotifications() {
        doReturn(systemNotificationList).when(mockSystemNotificationManager).loadActiveNotifications(date);

        assertThat(systemNotificationApiService.loadActiveNotifications(date)).isEqualTo(systemNotificationList);
    }

    @Test
    public void shouldConfirmNotification() {
        doReturn(systemNotificationConfirmation).when(mockSystemNotificationManager)
                .confirmNotification(systemNotificationConfirmationRequest);

        assertThat(systemNotificationApiService.confirmNotification(systemNotificationConfirmationRequest))
                .isEqualTo(systemNotificationConfirmation);
    }
}

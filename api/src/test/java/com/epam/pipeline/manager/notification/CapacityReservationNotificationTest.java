/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CapacityReservationNotificationTest {

    private static final Long TEMPLATE_ID = 25L;
    private static final Long OWNER_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long RUN_OWNER_ID = 3L;
    private static final Long OTHER_RUN_OWNER_ID = 4L;
    private static final String RUN_OWNER = "run-owner";
    private static final String OTHER_RUN_OWNER = "other-run-owner";

    @Mock
    private NotificationSettingsManager settingsManager;

    @Mock
    private NotificationParameterManager parameterManager;

    @Mock
    private MonitoringNotificationDao monitoringNotificationDao;

    @Mock
    private UserManager userManager;

    @Mock
    private RoleManager roleManager;

    @InjectMocks
    private NotificationManager notificationManager;

    @Captor
    private ArgumentCaptor<NotificationMessage> messageCaptor;

    private CapacityReservation reservation;

    @BeforeEach
    public void setUp() {
        reservation = CapacityReservationCreatorUtils.getReservation(1L);
        reservation.setId(10L);

        final NotificationSettings settings = new NotificationSettings();
        settings.setTemplateId(TEMPLATE_ID);
        settings.setEnabled(true);
        settings.setKeepInformedAdmins(true);
        settings.setInformedUserIds(Collections.emptyList());
        when(settingsManager.load(any(NotificationType.class))).thenReturn(settings);
        when(parameterManager.build(any(NotificationType.class), any(CapacityReservation.class)))
                .thenReturn(Collections.emptyMap());

        when(userManager.loadUserByName(CapacityReservationCreatorUtils.OWNER))
                .thenReturn(user(OWNER_ID, CapacityReservationCreatorUtils.OWNER));
        when(roleManager.loadRoleWithUsers(any())).thenReturn(adminRole());
    }

    @Test
    public void shouldAddressOwnerAndCopyAdmins() {
        notificationManager.notifyCapacityReservation(reservation,
                NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED, Collections.emptyList());

        final NotificationMessage message = captureMessage();
        assertThat(message.getToUserId()).isEqualTo(OWNER_ID);
        assertThat(message.getCopyUserIds()).containsExactly(ADMIN_ID);
        assertThat(message.getTemplate().getId()).isEqualTo(TEMPLATE_ID);
    }

    @Test
    public void shouldCopyExtraRecipientsAlongsideAdmins() {
        when(userManager.loadUsersByNames(anyList()))
                .thenReturn(Arrays.asList(user(RUN_OWNER_ID, RUN_OWNER),
                        user(OTHER_RUN_OWNER_ID, OTHER_RUN_OWNER)));

        notificationManager.notifyCapacityReservation(reservation,
                NotificationType.CAPACITY_RESERVATION_FINALIZING,
                Arrays.asList(RUN_OWNER, OTHER_RUN_OWNER));

        assertThat(captureMessage().getCopyUserIds())
                .containsExactlyInAnyOrder(ADMIN_ID, RUN_OWNER_ID, OTHER_RUN_OWNER_ID);
    }

    @Test
    public void shouldNotCopyOwnerWhoIsAlsoAnExtraRecipient() {
        when(userManager.loadUsersByNames(anyList()))
                .thenReturn(Collections.singletonList(user(RUN_OWNER_ID, RUN_OWNER)));

        notificationManager.notifyCapacityReservation(reservation,
                NotificationType.CAPACITY_RESERVATION_FINALIZING,
                Arrays.asList(CapacityReservationCreatorUtils.OWNER, RUN_OWNER));

        final ArgumentCaptor<List> namesCaptor = ArgumentCaptor.forClass(List.class);
        verify(userManager).loadUsersByNames(namesCaptor.capture());
        assertThat(namesCaptor.getValue()).containsExactly(RUN_OWNER);

        final NotificationMessage message = captureMessage();
        assertThat(message.getToUserId()).isEqualTo(OWNER_ID);
        assertThat(message.getCopyUserIds()).doesNotContain(OWNER_ID);
    }

    @Test
    public void shouldNotNotifyWhenTemplateIsDisabled() {
        final NotificationSettings disabled = new NotificationSettings();
        disabled.setEnabled(false);
        when(settingsManager.load(any(NotificationType.class))).thenReturn(disabled);

        notificationManager.notifyCapacityReservation(reservation,
                NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED, Collections.emptyList());

        verify(monitoringNotificationDao, never()).createMonitoringNotification(any());
    }

    @Test
    public void shouldNotNotifyWhenNoTemplateIsConfigured() {
        when(settingsManager.load(any(NotificationType.class))).thenReturn(null);

        notificationManager.notifyCapacityReservation(reservation,
                NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED, Collections.emptyList());

        verify(monitoringNotificationDao, never()).createMonitoringNotification(any());
    }

    @Test
    public void shouldIgnoreNullReservation() {
        notificationManager.notifyCapacityReservation(null,
                NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED, Collections.emptyList());

        verify(settingsManager, never()).load(eq(NotificationType.CAPACITY_RESERVATION_STATUS_CHANGED));
        verify(monitoringNotificationDao, never()).createMonitoringNotification(any());
    }

    private NotificationMessage captureMessage() {
        verify(monitoringNotificationDao).createMonitoringNotification(messageCaptor.capture());
        return messageCaptor.getValue();
    }

    private static PipelineUser user(final Long id, final String name) {
        final PipelineUser user = new PipelineUser();
        user.setId(id);
        user.setUserName(name);
        return user;
    }

    private static ExtendedRole adminRole() {
        final ExtendedRole role = new ExtendedRole();
        role.setUsers(Collections.singletonList(user(ADMIN_ID, "admin")));
        return role;
    }
}

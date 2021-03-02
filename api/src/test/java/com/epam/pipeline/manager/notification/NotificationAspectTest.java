/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.dao.notification.NotificationSettingsDao;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.test.aspect.AbstractAspectTest;
import com.epam.pipeline.test.creator.notification.NotificationCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class NotificationAspectTest extends AbstractAspectTest {

    private final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser(TEST_STRING);
    private final ExtendedRole extendedRole = UserCreatorUtils.getExtendedRole(pipelineUser);

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private MonitoringNotificationDao mockMonitoringNotificationDao;

    @Autowired
    private NotificationSettingsDao mockNotificationSettingsDao;

    @Autowired
    private UserDao mockUserDao;

    @Autowired
    private RoleDao mockRoleDao;

    @Test
    public void testNotifyRunStatusChanged() {
        final NotificationSettings settings = NotificationCreatorUtils
            .getNotificationSettings(ID, Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE));
        final PipelineRun run = PipelineCreatorUtils.getPipelineRun(TaskStatus.SUCCESS);
        doReturn(settings).when(mockNotificationSettingsDao).loadNotificationSettings(any());
        doReturn(pipelineUser).when(mockUserDao).loadUserByName(any());
        mockRole();

        pipelineRunManager.updatePipelineStatus(run);
        final ArgumentCaptor<NotificationMessage> captor =
            ArgumentCaptor.forClass(NotificationMessage.class);
        verify(mockMonitoringNotificationDao).createMonitoringNotification(captor.capture());

        final NotificationMessage capturedMessage = captor.getValue();
        Assert.assertEquals(pipelineUser.getId(), capturedMessage.getToUserId());
        Assert.assertEquals(TaskStatus.SUCCESS.name(),
            capturedMessage.getTemplateParameters().get("status"));
    }

    @Test
    public void testNotifyRunStatusChangedNotActiveIfStatusNotConfiguredForNotification() {
        final NotificationSettings settings = NotificationCreatorUtils
            .getNotificationSettings(ID, Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE));
        final PipelineRun run = PipelineCreatorUtils.getPipelineRun(TaskStatus.PAUSED);
        doReturn(settings).when(mockNotificationSettingsDao).loadNotificationSettings(any());
        mockRole();

        pipelineRunManager.updatePipelineStatus(run);

        verify(mockMonitoringNotificationDao, never()).createMonitoringNotification(any());
    }

    @Test
    public void testNotifyRunStatusChangedActiveIfSettingsDoesntHaveStatusesConfigured() {
        final NotificationSettings settings = NotificationCreatorUtils
            .getNotificationSettings(ID, Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE));
        final PipelineRun run = PipelineCreatorUtils.getPipelineRun(TaskStatus.PAUSED);
        doReturn(settings).when(mockNotificationSettingsDao).loadNotificationSettings(any());
        doReturn(pipelineUser).when(mockUserDao).loadUserByName(any());
        settings.setStatusesToInform(Collections.emptyList());
        mockRole();

        pipelineRunManager.updatePipelineStatus(run);

        final ArgumentCaptor<NotificationMessage> captor =
            ArgumentCaptor.forClass(NotificationMessage.class);
        verify(mockMonitoringNotificationDao).createMonitoringNotification(captor.capture());

        final NotificationMessage capturedMessage = captor.getValue();
        Assert.assertEquals(pipelineUser.getId(), capturedMessage.getToUserId());
    }

    private void mockRole() {
        doReturn(Optional.of(new Role())).when(mockRoleDao).loadRole(any());
        doReturn(extendedRole).when(mockRoleDao).loadExtendedRole(any());
    }
}
